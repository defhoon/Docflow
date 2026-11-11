package com.docflow.document.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.docflow.document.validation.DocumentFileType;

/**
 * SPEC-05 §7.3 LibreOffice 변환기 (ADR-0006): 인수 배열·독립 프로필·최소 환경 변수, 시간 초과 시 프로세스 트리 종료, 격리 래퍼
 * 점검 실패 시 격리 없이 실행하지 않음, 실제 LibreOffice 변환 결과의 PDF 유효성(PDFBox).
 * <p>
 * 실제 변환 테스트는 LibreOffice가 설치되어 있어야 하며 없으면 실패한다(건너뛰지 않는다, 결정 A4).
 */
class LibreOfficePreviewConverterTest {

	@TempDir
	private Path workDir;

	@Test
	void commandIsArgumentArrayWithLauncherProfileAndServerChosenInputName() {
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties(List.of("unshare", "-rn")), false);
		Path profile = this.workDir.resolve("profile");
		Path out = this.workDir.resolve("out");
		Path input = this.workDir.resolve("in").resolve("input.docx");

		List<String> command = converter.command(Path.of("/usr/bin/soffice"), profile, out, input);

		assertThat(command).startsWith("unshare", "-rn", "/usr/bin/soffice")
			.contains("-env:UserInstallation=" + profile.toUri(), "--headless", "--convert-to", "pdf", "--outdir",
					out.toString())
			.endsWith(input.toString())
			.doesNotContain("sh", "-c", "cmd", "/c");
		assertThat(String.join(" ", command)).doesNotContain(";").doesNotContain("|").doesNotContain("&&");
	}

	@Test
	void environmentIsMinimalAndDoesNotLeakBackendSecrets() {
		Map<String, String> current = Map.of("DB_PASSWORD", "secret", "DB_URL", "jdbc:x", "SystemRoot", "C:\\Windows",
				"JAVA_TOOL_OPTIONS", "-Dx");

		Map<String, String> linux = new LibreOfficePreviewConverter(properties(List.of()), false)
			.environment(this.workDir, current);
		Map<String, String> windows = new LibreOfficePreviewConverter(properties(List.of()), true)
			.environment(this.workDir, current);

		assertThat(linux).containsOnlyKeys("PATH", "HOME", "TMPDIR", "LANG")
			.containsEntry("HOME", this.workDir.toString());
		assertThat(windows).doesNotContainKeys("DB_PASSWORD", "DB_URL", "JAVA_TOOL_OPTIONS")
			.containsEntry("SystemRoot", "C:\\Windows")
			.containsEntry("TEMP", this.workDir.toString());
	}

	// Windows는 콘솔용 soffice.com을 찾는다(soffice.exe는 출력이 없어 자체 점검 실패, SPEC-05 §18.7)
	@Test
	void windowsDefaultCandidatesUseConsoleLauncher() {
		assertThat(LibreOfficePreviewConverter.WINDOWS_CANDIDATES).isNotEmpty()
			.allMatch((candidate) -> candidate.endsWith("\\program\\soffice.com"));
	}

	@Test
	void profileSettingsDisableMacrosAndLinkUpdates() {
		assertThat(LibreOfficePreviewConverter.PROFILE_SETTINGS)
			.contains("MacroSecurityLevel\" oor:op=\"fuse\"><value>3")
			.contains("DisableMacrosExecution\" oor:op=\"fuse\"><value>true")
			.contains("BlockUntrustedRefererLinks\" oor:op=\"fuse\"><value>true")
			.contains("Office.Writer/Content/Update\"><prop oor:name=\"Link\" oor:op=\"fuse\"><value>2")
			.contains("Office.Calc/Content/Update\"><prop oor:name=\"Link\" oor:op=\"fuse\"><value>1");
	}

	@Test
	void configuredButMissingExecutableIsUnavailable() {
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(
				new PreviewProperties(null, null, null, null, this.workDir.resolve("no-soffice").toString(), List.of(),
						null, null));

		assertThat(converter.status().available()).isFalse();
		assertThatThrownBy(() -> converter.convertToPdf(this.workDir.resolve("input.docx"), DocumentFileType.DOCX,
				this.workDir, Duration.ofSeconds(1)))
			.isInstanceOfSatisfying(PreviewConversionException.class,
					(ex) -> assertThat(ex.kind()).isEqualTo(PreviewConversionException.Kind.ENGINE));
	}

	// 격리 래퍼가 동작하지 않으면 격리 없이 실행하지 않는다 (ADR-0006 결정 3)
	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void failingIsolationLauncherDisablesEngineInsteadOfFallingBack() throws Exception {
		Path soffice = script("soffice", """
				#!/bin/sh
				echo "LibreOffice 0.0"
				""");
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(
				new PreviewProperties(null, null, null, null, soffice.toString(), List.of("/bin/false"), null, null));

		assertThat(converter.status().available()).isFalse();
		assertThat(converter.status().reason()).contains("isolation");
	}

	// 시간 초과 시 프로세스와 하위 프로세스를 모두 종료한다 (가짜 엔진이 하위 프로세스를 만든다)
	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void timeoutKillsProcessTree() throws Exception {
		Path soffice = script("soffice", """
				#!/bin/sh
				case "$*" in *--version*) echo "LibreOffice 0.0"; exit 0;; esac
				sleep 120 &
				echo $! > "$PWD/child.pid"
				echo $$ > "$PWD/parent.pid"
				wait
				""");
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(
				new PreviewProperties(null, null, null, null, soffice.toString(), List.of(), null, null));
		assertThat(converter.status().available()).isTrue();
		Path input = Files.createDirectories(this.workDir.resolve("in")).resolve("input.docx");
		Files.write(input, new byte[] { 1 });

		assertThatThrownBy(() -> converter.convertToPdf(input, DocumentFileType.DOCX, this.workDir, Duration.ofMillis(800)))
			.isInstanceOfSatisfying(PreviewConversionException.class,
					(ex) -> assertThat(ex.kind()).isEqualTo(PreviewConversionException.Kind.TIMEOUT));

		for (String pidFile : List.of("child.pid", "parent.pid")) {
			long pid = Long.parseLong(Files.readString(this.workDir.resolve(pidFile)).strip());
			// 강제 종료 신호는 비동기로 처리되므로 잠시 기다린다.
			long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) {
				Thread.sleep(50);
			}
			Optional<ProcessHandle> handle = ProcessHandle.of(pid);
			assertThat(handle.map(ProcessHandle::isAlive).orElse(false)).as(pidFile + " alive").isFalse();
		}
	}

	// AC-VDL-11: 실제 LibreOffice 변환 결과가 유효한 PDF이고 형식별 표식 텍스트를 담는다
	@ParameterizedTest
	@CsvSource({ "DOCX, sample.docx, DOCFLOW-PREVIEW-DOCX-1", "XLSX, sample.xlsx, DOCFLOW-PREVIEW-XLSX-1",
			"PPTX, sample.pptx, DOCFLOW-PREVIEW-PPTX-1" })
	void realLibreOfficeConvertsToValidPdf(DocumentFileType type, String fixture, String marker) throws Exception {
		LibreOfficePreviewConverter converter = realConverter(List.of());
		Path input = copyFixture(fixture, type);

		Path pdf = converter.convertToPdf(input, type, this.workDir, Duration.ofSeconds(60));

		assertValidPdf(pdf, marker);
		assertThat(this.workDir.resolve("profile/user/registrymodifications.xcu")).exists();
	}

	@Test
	void realLibreOfficeReportsUnloadableDocumentAsFailed() throws Exception {
		LibreOfficePreviewConverter converter = realConverter(List.of());
		Path input = copyFixture("broken.docx", DocumentFileType.DOCX);

		assertThatThrownBy(() -> converter.convertToPdf(input, DocumentFileType.DOCX, this.workDir, Duration.ofSeconds(60)))
			.isInstanceOfSatisfying(PreviewConversionException.class,
					(ex) -> assertThat(ex.kind()).isEqualTo(PreviewConversionException.Kind.FAILED));
	}

	// AC-VDL-13: 실제 LibreOffice가 시간 제한을 넘으면 프로세스가 남지 않는다
	@Test
	void realLibreOfficeTimeoutLeavesNoProcess() throws Exception {
		LibreOfficePreviewConverter converter = realConverter(List.of());
		Path input = copyFixture("sample.xlsx", DocumentFileType.XLSX);

		assertThatThrownBy(() -> converter.convertToPdf(input, DocumentFileType.XLSX, this.workDir, Duration.ofMillis(200)))
			.isInstanceOfSatisfying(PreviewConversionException.class,
					(ex) -> assertThat(ex.kind()).isEqualTo(PreviewConversionException.Kind.TIMEOUT));

		String marker = this.workDir.toString();
		assertThat(ProcessHandle.allProcesses()
			.filter((handle) -> handle.info().commandLine().map((line) -> line.contains(marker)).orElse(false))
			.toList()).isEmpty();
	}

	// AC-VDL-16: 네트워크 격리 래퍼(Linux 네트워크 namespace). 사용할 수 없는 환경이면 격리 없이 실행하지 않고 사용 불가가 된다
	@Test
	@EnabledOnOs(OS.LINUX)
	void isolationLauncherEitherConvertsInNetworkNamespaceOrDisablesEngine() throws Exception {
		List<String> launcher = List.of("unshare", "-rn");
		boolean namespaceAvailable = new ProcessBuilder("unshare", "-rn", "true").start().waitFor() == 0;
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties(launcher));

		if (namespaceAvailable) {
			assertThat(converter.status().available()).as("LibreOffice required").isTrue();
			Path input = copyFixture("sample.docx", DocumentFileType.DOCX);
			assertValidPdf(converter.convertToPdf(input, DocumentFileType.DOCX, this.workDir, Duration.ofSeconds(60)),
					"DOCFLOW-PREVIEW-DOCX-1");
		}
		else {
			assertThat(converter.status().available()).isFalse();
			assertThat(converter.status().reason()).contains("isolation");
		}
		System.out.println("[SPEC-05 isolation] unshare -rn available=" + namespaceAvailable);
	}

	static void assertValidPdf(Path pdf, String marker) throws IOException {
		assertThat(Files.readAllBytes(pdf)).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
			assertThat(document.isEncrypted()).isFalse();
			assertThat(new PDFTextStripper().getText(document)).contains(marker);
		}
	}

	private LibreOfficePreviewConverter realConverter(List<String> launcher) {
		LibreOfficePreviewConverter converter = new LibreOfficePreviewConverter(properties(launcher));
		assertThat(converter.status().available())
			.as("LibreOffice is required for SPEC-05 preview tests (decision A4): " + converter.status().reason())
			.isTrue();
		return converter;
	}

	private Path copyFixture(String fixture, DocumentFileType type) throws IOException {
		Path input = Files.createDirectories(this.workDir.resolve("in")).resolve("input." + type.extension());
		try (var in = getClass().getResourceAsStream("/preview/" + fixture)) {
			Files.copy(in, input);
		}
		return input;
	}

	private Path script(String name, String content) throws IOException {
		Path dir = Files.createTempDirectory("docflow-fake-engine");
		Path script = dir.resolve(name);
		Files.writeString(script, content);
		Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
		return script;
	}

	private static PreviewProperties properties(List<String> launcher) {
		return new PreviewProperties(null, null, null, null, System.getenv("DOCFLOW_PREVIEW_SOFFICE_PATH"), launcher,
				null, null);
	}

}

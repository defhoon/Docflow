package com.docflow.document.preview;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.docflow.document.validation.DocumentFileType;

/**
 * LibreOffice({@code soffice})를 자식 프로세스로 실행해 Office 문서를 PDF로 변환한다 (ADR-0006 결정 2·3).
 * <ul>
 * <li>셸을 거치지 않고 인수 배열로 실행하며 원본 파일명을 인수에 넣지 않는다.</li>
 * <li>변환마다 작업 디렉터리 안에 독립 사용자 프로필을 만들고 매크로·링크 갱신을 끄는 설정을 미리 넣는다.</li>
 * <li>환경 변수를 비우고 최소 값만 전달한다.</li>
 * <li>시간 초과·종료 시 프로세스와 모든 하위 프로세스를 강제 종료한다.</li>
 * <li>OS 격리 래퍼({@code docflow.preview.launcher})가 설정되면 모든 실행에 앞붙인다. 래퍼 점검이 실패하면 격리 없이 실행하지
 * 않고 엔진을 사용 불가로 둔다.</li>
 * </ul>
 */
@Component
public class LibreOfficePreviewConverter implements DocumentPreviewConverter {

	static final String PROFILE_SETTINGS = """
			<?xml version="1.0" encoding="UTF-8"?>
			<oor:items xmlns:oor="http://openoffice.org/2001/registry" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
			<item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item>
			<item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="DisableMacrosExecution" oor:op="fuse"><value>true</value></prop></item>
			<item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="BlockUntrustedRefererLinks" oor:op="fuse"><value>true</value></prop></item>
			<item oor:path="/org.openoffice.Office.Writer/Content/Update"><prop oor:name="Link" oor:op="fuse"><value>2</value></prop></item>
			<item oor:path="/org.openoffice.Office.Calc/Content/Update"><prop oor:name="Link" oor:op="fuse"><value>1</value></prop></item>
			</oor:items>
			""";

	private static final Logger log = LoggerFactory.getLogger(LibreOfficePreviewConverter.class);

	private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * Windows 기본 설치 경로. 콘솔용 {@code soffice.com}을 쓴다: GUI용 {@code soffice.exe}는 표준 출력을 내지 않아
	 * {@code --version} 자체 점검이 실패한다(Windows 실측, SPEC-05 §18.7).
	 */
	static final List<String> WINDOWS_CANDIDATES = List.of("C:\\Program Files\\LibreOffice\\program\\soffice.com",
			"C:\\Program Files (x86)\\LibreOffice\\program\\soffice.com");

	/** Windows에서 프로세스 실행에 필요한 시스템 변수만 전달한다. */
	private static final List<String> WINDOWS_PASSTHROUGH = List.of("SystemRoot", "windir", "SystemDrive",
			"PROCESSOR_ARCHITECTURE", "NUMBER_OF_PROCESSORS", "OS");

	private final PreviewProperties properties;

	private final boolean windows;

	private final Set<Process> running = ConcurrentHashMap.newKeySet();

	private volatile EngineStatus status;

	private volatile Path executable;

	@Autowired
	public LibreOfficePreviewConverter(PreviewProperties properties) {
		this(properties, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
	}

	LibreOfficePreviewConverter(PreviewProperties properties, boolean windows) {
		this.properties = properties;
		this.windows = windows;
	}

	@Override
	public EngineStatus status() {
		EngineStatus current = this.status;
		if (current == null) {
			synchronized (this) {
				if (this.status == null) {
					this.status = check();
				}
				current = this.status;
			}
		}
		return current;
	}

	@Override
	public Path convertToPdf(Path input, DocumentFileType type, Path workDir, Duration timeout)
			throws PreviewConversionException {
		Path soffice = this.executable;
		if (soffice == null || !status().available()) {
			throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "engine unavailable");
		}
		Path outDir = workDir.resolve("out");
		Path profileDir = workDir.resolve("profile");
		try {
			Files.createDirectories(outDir);
			Files.createDirectories(profileDir.resolve("user"));
			Files.writeString(profileDir.resolve("user").resolve("registrymodifications.xcu"), PROFILE_SETTINGS,
					StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "work directory setup failed",
					ex);
		}
		List<String> command = command(soffice, profileDir, outDir, input);
		int exit = run(command, workDir, timeout);
		String baseName = input.getFileName().toString();
		Path result = outDir.resolve(baseName.substring(0, baseName.lastIndexOf('.')) + ".pdf");
		if (exit != 0 || !Files.isRegularFile(result)) {
			throw new PreviewConversionException(PreviewConversionException.Kind.FAILED,
					"no output (exit=" + exit + ")");
		}
		return result;
	}

	/** 변환 명령 인수 배열. 셸을 거치지 않으며 원본 파일명을 쓰지 않는다. */
	List<String> command(Path soffice, Path profileDir, Path outDir, Path input) {
		List<String> command = new ArrayList<>(this.properties.launcher());
		command.add(soffice.toString());
		command.add("-env:UserInstallation=" + profileDir.toUri());
		command.addAll(List.of("--headless", "--invisible", "--nologo", "--nodefault", "--nofirststartwizard",
				"--norestore", "--nolockcheck", "--convert-to", "pdf", "--outdir", outDir.toString(), input.toString()));
		return command;
	}

	/** 자식 프로세스 환경 변수: 비운 뒤 최소 값만 둔다. Backend 환경 변수(DB 비밀번호 등)를 물려주지 않는다. */
	Map<String, String> environment(Path workDir, Map<String, String> current) {
		Map<String, String> env = new LinkedHashMap<>();
		String dir = workDir.toString();
		if (this.windows) {
			for (String name : WINDOWS_PASSTHROUGH) {
				if (current.containsKey(name)) {
					env.put(name, current.get(name));
				}
			}
			String systemRoot = current.getOrDefault("SystemRoot", "C:\\Windows");
			env.put("PATH", systemRoot + "\\System32;" + systemRoot);
			env.put("TEMP", dir);
			env.put("TMP", dir);
			env.put("USERPROFILE", dir);
			env.put("APPDATA", dir);
			env.put("LOCALAPPDATA", dir);
		}
		else {
			env.put("PATH", "/usr/local/bin:/usr/bin:/bin");
			env.put("HOME", dir);
			env.put("TMPDIR", dir);
			env.put("LANG", "C.UTF-8");
		}
		return env;
	}

	@PreDestroy
	void destroyRunning() {
		for (Process process : this.running) {
			destroyTree(process);
		}
	}

	private int run(List<String> command, Path workDir, Duration timeout) throws PreviewConversionException {
		ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile())
			.redirectErrorStream(true)
			.redirectOutput(workDir.resolve("engine.log").toFile());
		Map<String, String> env = builder.environment();
		Map<String, String> current = Map.copyOf(env);
		env.clear();
		env.putAll(environment(workDir, current));
		Process process;
		try {
			process = builder.start();
		}
		catch (IOException ex) {
			throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "engine start failed", ex);
		}
		this.running.add(process);
		try {
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				destroyTree(process);
				throw new PreviewConversionException(PreviewConversionException.Kind.TIMEOUT,
						"timed out after " + timeout.toMillis() + "ms");
			}
			// 종료 후에도 남은 하위 프로세스가 있으면 정리하고, 끝날 때까지 기다린다. Windows는 열린 파일이 있으면
			// 작업 디렉터리를 지울 수 없으므로 하위 프로세스가 파일 핸들을 놓은 뒤에 반환한다.
			awaitExit(process.descendants().toList(), true);
			return process.exitValue();
		}
		catch (InterruptedException ex) {
			destroyTree(process);
			Thread.currentThread().interrupt();
			throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "interrupted", ex);
		}
		finally {
			this.running.remove(process);
		}
	}

	/** 하위 프로세스를 먼저 수집해 종료한다(부모가 먼저 끝나면 하위 프로세스를 찾을 수 없다). */
	static void destroyTree(Process process) {
		List<ProcessHandle> descendants = process.descendants().toList();
		descendants.forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
		try {
			process.waitFor(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		awaitExit(descendants, true);
	}

	/** 주어진 프로세스들을 (필요하면 강제 종료하고) 최대 5초 동안 종료를 기다린다. 이 변환 작업의 프로세스만 다룬다. */
	private static void awaitExit(List<ProcessHandle> handles, boolean destroy) {
		if (destroy) {
			handles.forEach(ProcessHandle::destroyForcibly);
		}
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		for (ProcessHandle handle : handles) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				break;
			}
			try {
				handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
				log.warn("Preview engine process did not exit in time: pid={}", handle.pid());
			}
		}
	}

	private EngineStatus check() {
		Path soffice = resolveExecutable();
		if (soffice == null) {
			String reason = (this.properties.sofficePath() != null)
					? "configured LibreOffice not found: docflow.preview.soffice-path" : "LibreOffice (soffice) not found";
			log.warn("Office preview disabled: {}", reason);
			return EngineStatus.unavailable(reason);
		}
		Path checkDir = null;
		try {
			checkDir = Files.createTempDirectory("docflow-preview-check");
			List<String> command = new ArrayList<>(this.properties.launcher());
			command.add(soffice.toString());
			command.add("-env:UserInstallation=" + checkDir.resolve("profile").toUri());
			command.addAll(List.of("--headless", "--version"));
			int exit = run(command, checkDir, CHECK_TIMEOUT);
			String output = Files.readString(checkDir.resolve("engine.log"), StandardCharsets.UTF_8);
			if (exit != 0 || !output.contains("LibreOffice")) {
				return failedCheck("exit=" + exit);
			}
			this.executable = soffice;
			if (this.properties.launcher().isEmpty()) {
				log.warn("Office preview enabled WITHOUT OS-level isolation (docflow.preview.launcher is empty); "
						+ "see ADR-0006 residual risk");
			}
			else {
				log.info("Office preview enabled with isolation launcher {}", this.properties.launcher());
			}
			return EngineStatus.ok();
		}
		catch (IOException | PreviewConversionException ex) {
			return failedCheck(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
		finally {
			if (checkDir != null) {
				try {
					PreviewWorkspace.deleteRecursively(checkDir, checkDir);
				}
				catch (IOException ex) {
					log.warn("Failed to delete preview check directory", ex);
				}
			}
		}
	}

	private EngineStatus failedCheck(String detail) {
		if (this.properties.launcher().isEmpty()) {
			log.error("Office preview disabled: LibreOffice self-check failed ({})", detail);
			return EngineStatus.unavailable("LibreOffice self-check failed");
		}
		// 격리 래퍼가 동작하지 않으면 격리 없이 실행하지 않는다 (ADR-0006 결정 3).
		log.error("Office preview disabled: isolation launcher {} or LibreOffice self-check failed ({}); "
				+ "conversions will NOT run without isolation", this.properties.launcher(), detail);
		return EngineStatus.unavailable("isolation launcher self-check failed");
	}

	private Path resolveExecutable() {
		if (this.properties.sofficePath() != null) {
			Path configured = Path.of(this.properties.sofficePath());
			return Files.isRegularFile(configured) ? configured : null;
		}
		List<String> names = this.windows ? List.of("soffice.com") : List.of("soffice");
		String path = System.getenv("PATH");
		if (path != null) {
			for (String dir : path.split(File.pathSeparator)) {
				for (String name : names) {
					Path candidate = Path.of(dir, name);
					if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
						return candidate;
					}
				}
			}
		}
		if (this.windows) {
			for (String candidate : WINDOWS_CANDIDATES) {
				if (Files.isRegularFile(Path.of(candidate))) {
					return Path.of(candidate);
				}
			}
		}
		return null;
	}

}

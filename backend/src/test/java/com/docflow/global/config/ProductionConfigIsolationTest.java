package com.docflow.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * #39 Windows 실패 회귀: 로컬 .env(개발 값)가 prod 프로파일 설정을 덮어쓰지 않는지, prod에서 비보안 쿠키 설정이면 기동을
 * 거부하는지 확인한다. DB·웹 없이 설정 로딩과 가드 빈만 띄운다(OS·작업 디렉터리와 무관하게 임시 .env 경로를 지정).
 */
class ProductionConfigIsolationTest {

	@TempDir
	private Path dir;

	@Configuration(proxyBeanMethods = false)
	@Import(ProductionCookieGuard.class)
	static class Minimal {

	}

	@Test
	void dotenvIsReadOutsideProdButIgnoredInProd() throws IOException {
		Path dotenv = writeDotenv("SESSION_COOKIE_SECURE=false\nDOCFLOW_DOTENV_CANARY=loaded\n");

		try (ConfigurableApplicationContext local = start(dotenv)) {
			assertThat(local.getEnvironment().getProperty("DOCFLOW_DOTENV_CANARY")).isEqualTo("loaded");
			assertThat(local.getEnvironment().getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
		}
		try (ConfigurableApplicationContext prod = start(dotenv, "prod")) {
			assertThat(prod.getEnvironment().getProperty("DOCFLOW_DOTENV_CANARY")).isNull();
			assertThat(prod.getEnvironment().getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
		}
	}

	@Test
	void prodRefusesToStartWithInsecureCookies() throws IOException {
		Path empty = writeDotenv("");

		assertThatThrownBy(() -> start(empty, "prod", "--server.servlet.session.cookie.secure=false").close())
			.hasStackTraceContaining("prod profile requires secure cookies");
	}

	@Test
	void insecureCookiesInProdNeedExplicitLocalOptIn() throws IOException {
		Path empty = writeDotenv("");

		try (ConfigurableApplicationContext prod = start(empty, "prod", "--server.servlet.session.cookie.secure=false",
				"--docflow.security.allow-insecure-cookies=true")) {
			assertThat(prod.getBean(ProductionCookieGuard.class)).isNotNull();
		}
	}

	private Path writeDotenv(String content) throws IOException {
		return Files.writeString(this.dir.resolve(".env"), content);
	}

	private ConfigurableApplicationContext start(Path dotenv, String... args) {
		String profile = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : null;
		String[] rest = (profile == null) ? args : java.util.Arrays.copyOfRange(args, 1, args.length);
		SpringApplicationBuilder builder = new SpringApplicationBuilder(Minimal.class).web(WebApplicationType.NONE)
			.logStartupInfo(false);
		if (profile != null) {
			builder.profiles(profile);
		}
		String[] all = java.util.stream.Stream
			.concat(java.util.stream.Stream.of("--docflow.dotenv=" + dotenv.toAbsolutePath(), "--spring.main.banner-mode=off"),
					java.util.Arrays.stream(rest))
			.toArray(String[]::new);
		return builder.run(all);
	}

}

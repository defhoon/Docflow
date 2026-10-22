package com.docflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator 검증을 두 관점으로 나눈다 (SPEC-AUTH §13.2).
 * <ol>
 * <li>노출 구성: HTTP로 노출되는 Actuator 엔드포인트가 health뿐인지</li>
 * <li>Security 처리: health 외 경로가 SecurityFilterChain에서 거부되는지 (익명 401, 인증 사용자 403)</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorExposureTest {

	private static final String[] NON_HEALTH_PATHS = { "/actuator/env", "/actuator/beans", "/actuator/configprops" };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WebEndpointsSupplier webEndpointsSupplier;

	@Test
	void healthIsPublicAndReturnsOnlyOverallStatus() throws Exception {
		this.mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist())
			.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void onlyHealthEndpointIsExposedOverHttp() {
		Set<String> exposedIds = this.webEndpointsSupplier.getEndpoints()
			.stream()
			.map((endpoint) -> endpoint.getEndpointId().toLowerCaseString())
			.collect(Collectors.toSet());

		assertThat(exposedIds).containsExactly("health");
	}

	@Test
	void anonymousRequestsToOtherActuatorPathsAreRejectedBySecurity() throws Exception {
		for (String path : NON_HEALTH_PATHS) {
			this.mockMvc.perform(get(path))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		}
	}

	@Test
	void authenticatedRequestsToOtherActuatorPathsAreDenied() throws Exception {
		for (String path : NON_HEALTH_PATHS) {
			this.mockMvc.perform(get(path).with(user("someone@test.local")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
	}

}

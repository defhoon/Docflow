package com.docflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** #37: 신뢰 프록시 경계 밖의 X-Forwarded-For 값은 사용하지 않는다. */
class ClientIpResolverTest {

	@Test
	void disabledByDefault() {
		MockHttpServletRequest request = request("10.0.0.2", "198.51.100.7");
		assertThat(new ClientIpResolver(0).resolve(request)).isEmpty();
	}

	@Test
	void takesAddressSeenByOutermostTrustedProxy() {
		// 클라이언트가 보낸 위조 값, 엣지가 붙인 실제 클라이언트, Next가 붙인 엣지 주소, 원격 주소(Next)
		MockHttpServletRequest request = request("10.0.0.2", "203.0.113.66, 198.51.100.7, 10.0.0.9");
		assertThat(new ClientIpResolver(2).resolve(request)).contains("198.51.100.7");
		assertThat(new ClientIpResolver(1).resolve(request)).contains("10.0.0.9");
	}

	@Test
	void spoofedHeadersCannotChooseTheIpBeyondTrustedHops() {
		MockHttpServletRequest withSpoof = request("10.0.0.2", "1.1.1.1, 2.2.2.2, 198.51.100.7, 10.0.0.9");
		assertThat(new ClientIpResolver(2).resolve(withSpoof)).contains("198.51.100.7");
	}

	@Test
	void tooShortChainOrGarbageGivesNothing() {
		assertThat(new ClientIpResolver(2).resolve(request("10.0.0.2", null))).isEmpty();
		assertThat(new ClientIpResolver(1).resolve(request("10.0.0.2", "<script>"))).isEmpty();
		assertThatThrownBy(() -> new ClientIpResolver(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	private static MockHttpServletRequest request(String remote, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remote);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}

}

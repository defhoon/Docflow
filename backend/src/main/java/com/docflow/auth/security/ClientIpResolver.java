package com.docflow.auth.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 시도 제한용 클라이언트 IP (#37). 신뢰 프록시 경계 밖에서 온 값은 쓰지 않는다.
 * <p>
 * {@code trustedProxyHops}는 Backend 앞에 있는 신뢰 프록시 수다(예: Railway 엣지 → Next.js → Backend이면 2). 각 프록시는
 * 자신에게 연결한 주소를 {@code X-Forwarded-For} 끝에 덧붙이므로, {@code [X-Forwarded-For…, remoteAddr]} 목록의 오른쪽에서
 * {@code hops}개를 건너뛴 값이 가장 바깥 신뢰 프록시가 본 클라이언트 주소다. 그보다 왼쪽 값은 클라이언트가 위조할 수 있어
 * 무시한다. 0(기본)이면 IP를 사용하지 않는다.
 */
@Component
public class ClientIpResolver {

	private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

	private final int trustedProxyHops;

	public ClientIpResolver(@Value("${docflow.auth.trusted-proxy-hops:0}") int trustedProxyHops) {
		if (trustedProxyHops < 0 || trustedProxyHops > 5) {
			throw new IllegalArgumentException("docflow.auth.trusted-proxy-hops must be 0..5");
		}
		this.trustedProxyHops = trustedProxyHops;
	}

	public Optional<String> resolve(HttpServletRequest request) {
		if (this.trustedProxyHops == 0) {
			return Optional.empty();
		}
		List<String> chain = new ArrayList<>();
		for (String header : Collections.list(request.getHeaders("X-Forwarded-For"))) {
			for (String part : header.split(",")) {
				chain.add(part.strip());
			}
		}
		chain.add(request.getRemoteAddr());
		int index = chain.size() - 1 - this.trustedProxyHops;
		if (index < 0) {
			return Optional.empty();
		}
		String candidate = chain.get(index);
		return IP_LITERAL.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
	}

}

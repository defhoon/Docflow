package com.docflow.home.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.home.dto.HomeResponse;
import com.docflow.home.service.HomeService;

/** 홈 통합 조회 API (SPEC-18). 읽기 전용. */
@RestController
public class HomeController {

	private final HomeService homeService;

	public HomeController(HomeService homeService) {
		this.homeService = homeService;
	}

	@GetMapping("/api/home")
	public HomeResponse getHome(@AuthenticationPrincipal AuthenticatedUser principal) {
		return this.homeService.getHome(principal.getId());
	}

}

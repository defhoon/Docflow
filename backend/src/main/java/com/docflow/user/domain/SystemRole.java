package com.docflow.user.domain;

/** 서비스 전역 역할 (SPEC-AUTH 확장 #26). ADMIN은 초대코드 발급만 할 수 있으며 프로젝트 권한과는 무관하다. */
public enum SystemRole {

	USER, ADMIN

}

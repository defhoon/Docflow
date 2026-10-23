package com.docflow.project.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 프로젝트 설명 허용 문자 검증 ({@link com.docflow.project.domain.ProjectText#isAllowedDescription(String)}).
 */
@Documented
@Constraint(validatedBy = ProjectDescriptionCharactersValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectDescriptionCharacters {

	String message() default "설명에 사용할 수 없는 문자가 포함되어 있습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}

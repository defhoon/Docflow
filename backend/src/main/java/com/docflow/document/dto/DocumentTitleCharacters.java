package com.docflow.document.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 문서 제목 허용 문자 검증. 문자 규칙은 {@link com.docflow.project.domain.ProjectText#isAllowedName(String)}을 그대로
 * 쓰고 메시지만 문서용이다 (SPEC-03 §5.3).
 */
@Documented
@Constraint(validatedBy = DocumentTitleCharactersValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentTitleCharacters {

	String message() default "문서 제목에 사용할 수 없는 문자가 포함되어 있습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}

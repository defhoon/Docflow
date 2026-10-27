package com.docflow.document.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.docflow.project.domain.ProjectText;

public class DocumentTitleCharactersValidator implements ConstraintValidator<DocumentTitleCharacters, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return ProjectText.isAllowedName(value);
	}

}

package com.docflow.project.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.docflow.project.domain.ProjectText;

public class ProjectDescriptionCharactersValidator
		implements ConstraintValidator<ProjectDescriptionCharacters, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return ProjectText.isAllowedDescription(value);
	}

}

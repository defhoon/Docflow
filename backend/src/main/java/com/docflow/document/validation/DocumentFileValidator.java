package com.docflow.document.validation;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * 확장자에 맞는 형식 검증을 임시 파일에 수행한다 (SPEC-03 §5.5.1-3). 클라이언트가 보낸 Content-Type은 사용하지 않는다.
 */
@Component
public class DocumentFileValidator {

	private final PdfStructureValidator pdfValidator = new PdfStructureValidator();

	private final OoxmlPackageValidator ooxmlValidator = new OoxmlPackageValidator(OoxmlPackageValidator.Limits.DEFAULT);

	public void validate(Path file, DocumentFileType type) {
		if (type.isOoxml()) {
			this.ooxmlValidator.validate(file, type);
		}
		else {
			this.pdfValidator.validate(file);
		}
	}

}

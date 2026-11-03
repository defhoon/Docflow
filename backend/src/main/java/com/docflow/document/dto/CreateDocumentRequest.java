package com.docflow.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import com.docflow.project.domain.ProjectText;
import com.docflow.project.dto.ProjectDescriptionCharacters;

/**
 * 문서 등록 요청 ({@code multipart/form-data}, SPEC-03 §5.3, §6.3). 작성자·상태·버전 번호는 요청으로 받지 않는다.
 * <p>
 * 정규화는 Bean Validation 전에 이 생성자에서 한 번만 수행한다. 파일 크기·형식·파일명은 Service에서 검증한다.
 */
public record CreateDocumentRequest(
		@NotBlank(message = "문서 제목을 입력해 주세요.")
		@Size(max = 200, message = "문서 제목은 200자 이하로 입력해 주세요.") @DocumentTitleCharacters String title,
		@Size(max = 1000, message = "설명은 1000자 이하로 입력해 주세요.") @ProjectDescriptionCharacters String description,
		@NotNull(message = "파일을 선택해 주세요.") MultipartFile file) {

	public CreateDocumentRequest {
		title = ProjectText.trimEdges(title);
		description = ProjectText.trimEdgesToNull(description);
	}

}

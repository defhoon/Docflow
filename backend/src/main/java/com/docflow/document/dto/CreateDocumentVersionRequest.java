package com.docflow.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import com.docflow.document.domain.DocumentVersion;
import com.docflow.project.domain.ProjectText;
import com.docflow.project.dto.ProjectDescriptionCharacters;

/**
 * 새 버전 등록 요청 ({@code multipart/form-data}, SPEC-04 §6.3). 파일과 선택 입력인 변경사항(직전 버전 대비, 최대
 * 1,000자, SPEC-09)을 받는다. 버전 번호·업로더·상태 등 정의되지 않은 파트는 바인딩하지 않으므로 무시된다. 파일 크기·형식·파일명은
 * Service에서 검증한다.
 */
public record CreateDocumentVersionRequest(@NotNull(message = "파일을 선택해 주세요.") MultipartFile file,
		@Size(max = DocumentVersion.MAX_NOTE_LENGTH, message = "변경사항은 1000자 이하로 입력해 주세요.") @ProjectDescriptionCharacters String versionNote) {

	public CreateDocumentVersionRequest {
		versionNote = ProjectText.trimEdgesToNull(versionNote);
	}

}

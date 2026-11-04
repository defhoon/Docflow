package com.docflow.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectRole;
import com.docflow.user.domain.User;

class ProjectPermissionServiceTest {

	private final ProjectPermissionService permissionService = new ProjectPermissionService();

	private final Project project = Project.create("DocFlow", null, user(1L));

	@Test
	void ownerResolvesToOwner() {
		assertThat(this.permissionService.resolveRole(this.project, 1L)).isEqualTo(ProjectRole.OWNER);
		assertThatCode(() -> this.permissionService.requireOwner(this.project, 1L)).doesNotThrowAnyException();
	}

	@Test
	void otherAccessibleUserResolvesToMemberAndIsDenied() {
		assertThat(this.permissionService.resolveRole(this.project, 2L)).isEqualTo(ProjectRole.MEMBER);
		assertThatThrownBy(() -> this.permissionService.requireOwner(this.project, 2L))
			.isInstanceOfSatisfying(BusinessException.class,
					(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
	}

	// SPEC-03 §7.1: 문서 등록은 OWNER·MEMBER 모두 허용
	@Test
	void ownerAndMemberCanCreateDocuments() {
		assertThatCode(() -> this.permissionService.requireDocumentCreator(this.project, 1L))
			.doesNotThrowAnyException();
		assertThatCode(() -> this.permissionService.requireDocumentCreator(this.project, 2L))
			.doesNotThrowAnyException();
	}

	// SPEC-04 §7.1 (결정 V3): 새 버전 등록은 OWNER·MEMBER 모두 허용 (문서 작성자 여부와 무관)
	@Test
	void ownerAndMemberCanUploadVersions() {
		assertThatCode(() -> this.permissionService.requireVersionUploader(this.project, 1L))
			.doesNotThrowAnyException();
		assertThatCode(() -> this.permissionService.requireVersionUploader(this.project, 2L))
			.doesNotThrowAnyException();
	}

	private static User user(Long id) {
		User user = User.create(id + "@test.local", "hash", "User " + id);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}

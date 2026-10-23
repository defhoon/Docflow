package com.docflow.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.docflow.user.domain.User;

class ProjectTest {

	@Test
	void createKeepsGivenValuesAndOwner() {
		User owner = userWithId(1L);

		Project project = Project.create("DocFlow", null, owner);

		assertThat(project.getName()).isEqualTo("DocFlow");
		assertThat(project.getDescription()).isNull();
		assertThat(project.getOwner()).isSameAs(owner);
	}

	@Test
	void isOwnedByComparesOwnerId() {
		Project project = Project.create("DocFlow", null, userWithId(1L));

		assertThat(project.isOwnedBy(1L)).isTrue();
		assertThat(project.isOwnedBy(2L)).isFalse();
		assertThat(project.isOwnedBy(null)).isFalse();
	}

	private static User userWithId(Long id) {
		User user = User.create("owner@test.local", "{noop}x", "Owner");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}

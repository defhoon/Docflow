package com.docflow.global.common;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성·수정 시각을 JPA Auditing으로 관리하는 Entity 공통 상위 클래스.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * 다른 컬럼이 바뀌지 않아도 {@code updated_at}을 갱신해야 할 때 호출한다. 엔티티를 변경 상태로 만들 뿐이며, 실제 기록
	 * 값은 flush 시 JPA Auditing({@code @LastModifiedDate})이 정한다 (SPEC-04 §5.3, 결정 I2).
	 */
	protected void markModified() {
		this.updatedAt = Instant.now();
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}

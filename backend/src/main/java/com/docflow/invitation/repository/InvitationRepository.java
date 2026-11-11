package com.docflow.invitation.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.invitation.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	/** 가입 시 코드 행을 잠가 동시 소비를 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Invitation i where i.codeHash = :codeHash")
	Optional<Invitation> findByCodeHashForUpdate(@Param("codeHash") String codeHash);

	@Query("select i from Invitation i left join fetch i.usedBy left join fetch i.createdBy order by i.id desc")
	List<Invitation> findRecent(Limit limit);

	Optional<Invitation> findByCodeHash(String codeHash);

	@Query("select i from Invitation i where i.grantsAdmin = true and i.usedAt is null and i.revokedAt is null")
	List<Invitation> findUnusedAdminInvitations();

}

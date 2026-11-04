package com.sequan.user.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
	Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

	boolean existsByEmailIgnoreCase(String email);

	@Query("""
			SELECT u FROM UserEntity u
			WHERE u.deletedAt IS NULL
			AND (
				LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
				OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
			)
			ORDER BY u.fullName ASC
			""")
	List<UserEntity> searchActive(@Param("q") String q, Pageable pageable);
}

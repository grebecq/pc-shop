package dev.pcshop.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SavedBuildRepository extends JpaRepository<SavedBuild, Long> {

    List<SavedBuild> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<SavedBuild> findByIdAndAccountId(Long id, Long accountId);

    long countByAccountId(Long accountId);
}

package dev.pcshop.repository;

import dev.pcshop.entity.SavedBuild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedBuildRepository extends JpaRepository<SavedBuild, Long> {

    List<SavedBuild> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<SavedBuild> findByIdAndAccountId(Long id, Long accountId);

    long countByAccountId(Long accountId);
}

package dev.pcshop.repository;

import dev.pcshop.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Последние операции по товару, новые сверху. */
    List<StockMovement> findTop50ByProductIdOrderByIdDesc(long productId);
}

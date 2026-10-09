package dev.pcshop.repository;

import dev.pcshop.entity.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    List<PickupPoint> findAllByActiveTrueOrderById();

    Optional<PickupPoint> findByIdAndActiveTrue(long id);
}

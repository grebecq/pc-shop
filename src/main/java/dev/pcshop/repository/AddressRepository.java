package dev.pcshop.repository;

import dev.pcshop.entity.DeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<DeliveryAddress, Long> {

    List<DeliveryAddress> findAllByAccountIdOrderByDefaultAddressDescIdAsc(long accountId);

    Optional<DeliveryAddress> findByIdAndAccountId(long id, long accountId);

    long countByAccountId(long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DeliveryAddress a set a.defaultAddress = false where a.accountId = :accountId and a.defaultAddress = true")
    void clearDefault(long accountId);
}

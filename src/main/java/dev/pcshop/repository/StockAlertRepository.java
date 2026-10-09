package dev.pcshop.repository;

import dev.pcshop.entity.Account;
import dev.pcshop.entity.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, StockAlert.Key> {

    /** Кто ждёт товар: им уйдёт письмо, когда он появится. */
    @Query("select a from Account a where a.id in (select s.accountId from StockAlert s where s.productId = :productId)")
    List<Account> subscribers(@Param("productId") long productId);

    long countByAccountId(long accountId);
}

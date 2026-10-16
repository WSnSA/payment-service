package com.wsnsa.payment.ebarimt;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, String> {

    Optional<Receipt> findByIdAndMerchantId(String id, String merchantId);

    Optional<Receipt> findByMerchantIdAndExternalRef(String merchantId, String externalRef);

    Optional<Receipt> findByInvoiceId(String invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findForUpdate(@Param("id") String id);

    @Query("select r.id from Receipt r where r.status = com.wsnsa.payment.ebarimt.ReceiptStatus.PENDING " +
            "and (r.nextAttemptAt is null or r.nextAttemptAt <= :now) order by r.createdAt")
    List<String> findDue(@Param("now") Instant now, Pageable page);

    @Query("select r.id from Receipt r where r.status = com.wsnsa.payment.ebarimt.ReceiptStatus.ISSUING " +
            "and r.updatedAt < :before")
    List<String> findStuckIssuing(@Param("before") Instant before, Pageable page);
}

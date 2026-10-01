package com.wsnsa.payment.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByIdAndMerchantId(String id, String merchantId);

    Optional<Invoice> findByMerchantIdAndExternalRef(String merchantId, String externalRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findForUpdate(@Param("id") String id);

    /** Webhook алдагдсан тохиолдолд poll хийх нэхэмжлэхүүд. */
    @Query("select i.id from Invoice i where i.status = com.wsnsa.payment.payment.InvoiceStatus.PENDING " +
            "and i.createdAt between :from and :to order by i.createdAt")
    List<String> findPendingCreatedBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable page);

    @Query("select i.id from Invoice i where i.status = com.wsnsa.payment.payment.InvoiceStatus.PENDING " +
            "and i.expiresAt < :now")
    List<String> findExpired(@Param("now") Instant now, Pageable page);
}

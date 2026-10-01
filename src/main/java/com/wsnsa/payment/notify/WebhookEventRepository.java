package com.wsnsa.payment.notify;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    @Query("select e from WebhookEvent e where e.status = com.wsnsa.payment.notify.WebhookEvent.Status.PENDING " +
            "and e.nextAttemptAt <= :now order by e.nextAttemptAt")
    List<WebhookEvent> findDue(@Param("now") Instant now, Pageable page);

    List<WebhookEvent> findAllByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable page);
}

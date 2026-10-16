package com.wsnsa.payment.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Webhook алдагдсан тохиолдолд PENDING нэхэмжлэхүүдийг provider-оос шалгах, хугацаа дууссаныг хаах.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceJobs {

    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;

    /** Сүүлийн 2 цагт үүссэн (1 минутаас хуучин) PENDING нэхэмжлэхүүд. */
    @Scheduled(fixedDelayString = "${payment.jobs.invoice-poll-delay:PT1M}")
    public void pollPending() {
        Instant now = Instant.now();
        for (String id : invoices.findPendingCreatedBetween(now.minus(Duration.ofHours(2)), now.minus(Duration.ofMinutes(1)), PageRequest.of(0, 100))) {
            try {
                invoiceService.refresh(id);
            } catch (RuntimeException e) {
                log.debug("Poll of invoice {} failed: {}", id, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.jobs.invoice-expire-delay:PT5M}")
    public void expireOld() {
        for (String id : invoices.findExpired(Instant.now(), PageRequest.of(0, 100))) {
            try {
                invoiceService.expire(id);
            } catch (RuntimeException e) {
                log.warn("Expire of invoice {} failed: {}", id, e.getMessage());
            }
        }
    }
}

package com.wsnsa.payment.ebarimt;

import com.wsnsa.payment.merchant.Merchant;
import com.wsnsa.payment.merchant.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptJobs {

    private static final Duration STUCK_AFTER = Duration.ofMinutes(15);

    private final ReceiptRepository receipts;
    private final ReceiptService receiptService;
    private final MerchantRepository merchants;
    private final PosApiClient posApi;
    private final TransactionTemplate tx;

    /** Хүлээгдэж буй (шинэ эсвэл дахин оролдох) баримтуудыг илгээнэ. */
    @Scheduled(fixedDelayString = "${payment.jobs.receipt-issue-delay:PT10S}")
    public void issueDue() {
        for (String id : receipts.findDue(Instant.now(), PageRequest.of(0, 50))) {
            receiptService.issue(id);
        }
    }

    /**
     * ISSUING дээр удаан гацсан баримт (процесс унасан г.м). PosAPI дээр үүссэн эсэх нь тодорхойгүй тул
     * автоматаар дахин илгээхгүй, FAILED болгож гараар шалгуулна.
     */
    @Scheduled(fixedDelayString = "PT5M")
    public void recoverStuck() {
        for (String id : receipts.findStuckIssuing(Instant.now().minus(STUCK_AFTER), PageRequest.of(0, 50))) {
            tx.executeWithoutResult(s -> receipts.findForUpdate(id).ifPresent(r -> {
                if (r.getStatus() == ReceiptStatus.ISSUING) {
                    r.setStatus(ReceiptStatus.FAILED);
                    r.setLastError("Interrupted while issuing; verify in PosAPI before retrying");
                    log.warn("Receipt {} stuck in ISSUING, marked FAILED", id);
                }
            }));
        }
    }

    /** PosAPI-д хуримтлагдсан баримтуудыг татварын нэгдсэн систем рүү илгээх (өдөр бүр). */
    @Scheduled(cron = "${ebarimt.send-cron:0 0 23 * * *}", zone = "Asia/Ulaanbaatar")
    public void sendData() {
        for (Merchant m : merchants.findAll()) {
            if (!m.isActive() || m.getEbarimt().getMode() == EbarimtMode.DISABLED
                    || m.getEbarimt().getPosApiUrl() == null || m.getEbarimt().getPosApiUrl().isBlank()) {
                continue;
            }
            try {
                posApi.sendData(m.getEbarimt());
                log.info("PosAPI sendData ok for merchant {}", m.getCode());
            } catch (RuntimeException e) {
                log.warn("PosAPI sendData failed for merchant {}: {}", m.getCode(), e.getMessage());
            }
        }
    }
}

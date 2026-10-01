package com.wsnsa.payment.notify;

import com.wsnsa.payment.merchant.Merchant;
import com.wsnsa.payment.merchant.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox-оос merchant-ийн webhook URL руу илгээнэ. 2xx хариу авбал DELIVERED,
 * үгүй бол 1м, 5м, 15м, 1ц, 3ц, 6ц, 12ц, 24ц... завсартай дахин оролдож, эцэст нь FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1),
            Duration.ofHours(3), Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(24)
    };

    private final WebhookEventRepository events;
    private final MerchantRepository merchants;
    private final RestClient restClient;

    @Scheduled(fixedDelayString = "${payment.jobs.webhook-dispatch-delay:PT5S}")
    public void dispatchDue() {
        List<WebhookEvent> due = events.findDue(Instant.now(), PageRequest.of(0, 50));
        for (WebhookEvent e : due) {
            deliver(e);
        }
    }

    void deliver(WebhookEvent e) {
        Merchant m = merchants.findById(e.getMerchantId()).orElse(null);
        if (m == null || m.getWebhookUrl() == null || m.getWebhookUrl().isBlank()) {
            // Webhook URL тохируулаагүй: merchant API-аар poll хийнэ гэж үзээд хадгалаад орхино.
            e.setStatus(WebhookEvent.Status.FAILED);
            e.setLastError("Merchant webhook URL is not configured");
            events.save(e);
            return;
        }
        e.setAttempts(e.getAttempts() + 1);
        try {
            long ts = Instant.now().getEpochSecond();
            var response = restClient.post()
                    .uri(m.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSigner.HEADER, WebhookSigner.sign(m.getWebhookSecret(), ts, e.getPayload()))
                    .header("X-Payment-Event", e.getEventType())
                    .header("X-Payment-Event-Id", e.getId())
                    .body(e.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            e.setLastStatusCode(response.getStatusCode().value());
            e.setStatus(WebhookEvent.Status.DELIVERED);
            e.setDeliveredAt(Instant.now());
            e.setLastError(null);
        } catch (RestClientResponseException ex) {
            e.setLastStatusCode(ex.getStatusCode().value());
            scheduleRetry(e, "HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            scheduleRetry(e, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        events.save(e);
    }

    private void scheduleRetry(WebhookEvent e, String error) {
        e.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
        if (e.getAttempts() > BACKOFF.length) {
            e.setStatus(WebhookEvent.Status.FAILED);
            log.warn("Webhook {} to merchant {} failed permanently: {}", e.getId(), e.getMerchantId(), error);
        } else {
            e.setNextAttemptAt(Instant.now().plus(BACKOFF[e.getAttempts() - 1]));
        }
    }
}

package com.wsnsa.payment.notify;

import com.wsnsa.payment.common.JsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WebhookEventRepository repository;
    private final JsonSupport json;

    /**
     * Мэдэгдлийг outbox-д бичнэ. Дуудагчийн transaction-д оролцох ёстой.
     *
     * <p>Payload: {@code {"id": "...", "type": "invoice.paid", "createdAt": "...", "data": {...}}}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String merchantId, String type, Object data) {
        WebhookEvent e = new WebhookEvent();
        e.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", e.getId());
        envelope.put("type", type);
        envelope.put("createdAt", now.toString());
        envelope.put("data", data);
        e.setMerchantId(merchantId);
        e.setEventType(type);
        e.setPayload(json.write(envelope));
        e.setNextAttemptAt(now);
        repository.save(e);
    }
}

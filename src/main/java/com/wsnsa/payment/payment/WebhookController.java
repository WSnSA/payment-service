package com.wsnsa.payment.payment;

import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.provider.ProviderType;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider-уудаас ирэх мэдэгдэл: {@code /webhooks/{provider}/{invoiceId}}.
 *
 * <p>Body-д огт итгэхгүй, зөвхөн "шалга" гэсэн дохио гэж үзээд provider-ийн API-аар төлөвийг
 * баталгаажуулна. {@code ?redirect=1} үед хэрэглэгчийг merchant-ийн returnUrl руу буцаана.
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class WebhookController {

    /** Нэг нэхэмжлэх дээр provider руу хэт олон дуудлага явуулахаас сэргийлэх. */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(2);

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoices;
    private final Map<String, Instant> lastChecked = new ConcurrentHashMap<>();

    @RequestMapping(value = "/webhooks/{provider}/{invoiceId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> handle(@PathVariable String provider, @PathVariable String invoiceId,
                                    @RequestParam(value = "redirect", required = false) String redirect) {
        ProviderType type;
        try {
            type = ProviderType.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Invoice");
        }
        Invoice invoice = invoices.findById(invoiceId)
                .filter(i -> i.getProvider() == type)
                .orElseThrow(() -> ApiException.notFound("Invoice"));

        if (shouldCheck(invoiceId)) {
            invoice = invoiceService.refresh(invoiceId);
        }

        if (redirect != null && invoice.getReturnUrl() != null) {
            String location = UriComponentsBuilder.fromUriString(invoice.getReturnUrl())
                    .queryParam("invoiceId", invoice.getId())
                    .queryParam("externalRef", invoice.getExternalRef())
                    .queryParam("status", invoice.getStatus().name())
                    .encode().build().toUriString();
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
        }
        return ResponseEntity.ok(Map.of("status", invoice.getStatus().name()));
    }

    private boolean shouldCheck(String invoiceId) {
        Instant now = Instant.now();
        Instant prev = lastChecked.put(invoiceId, now);
        if (lastChecked.size() > 10_000) {
            lastChecked.entrySet().removeIf(e -> e.getValue().isBefore(now.minus(Duration.ofMinutes(5))));
        }
        return prev == null || prev.isBefore(now.minus(MIN_INTERVAL));
    }
}

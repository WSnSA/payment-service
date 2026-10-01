package com.wsnsa.payment.provider;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param invoiceId   Манай нэхэмжлэхийн id (provider талд sender_invoice_no / orderId болно)
 * @param options     Provider-ийн нэмэлт параметр (жишээ нь DigiPay-ийн хэрэглэгчийн token)
 */
public record ProviderInvoiceRequest(
        ProviderContext context,
        String invoiceId,
        BigDecimal amount,
        String description,
        Map<String, String> options
) {
    public String option(String key) {
        return options == null ? null : options.get(key);
    }
}

package com.wsnsa.payment.provider.impl;

import com.wsnsa.payment.config.AppProperties;
import com.wsnsa.payment.provider.PaymentProvider;
import com.wsnsa.payment.provider.PaymentStatus;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderInvoice;
import com.wsnsa.payment.provider.ProviderInvoiceRequest;
import com.wsnsa.payment.provider.ProviderType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Жинхэнэ мөнгө хөдлөхгүй тест provider. {@code POST /sandbox/invoices/{id}/pay} дуудахад
 * төлөгдсөнд тооцогдоно. Production-д асаахгүй.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "sandbox-enabled", havingValue = "true")
public class SandboxProvider implements PaymentProvider {

    private final Map<String, BigDecimal> paid = new ConcurrentHashMap<>();
    private final AppProperties props;

    public SandboxProvider(AppProperties props) {
        this.props = props;
    }

    @Override
    public ProviderType type() {
        return ProviderType.SANDBOX;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        String id = "sbx-" + req.invoiceId();
        return ProviderInvoice.builder()
                .providerInvoiceId(id)
                .qrText(id)
                .paymentUrl(props.publicBaseUrl() + "/sandbox/invoices/" + req.invoiceId() + "/pay")
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        BigDecimal amount = paid.get(providerInvoiceId);
        return amount == null ? PaymentStatus.pending() : PaymentStatus.paid("sbx-txn-" + invoiceId, amount);
    }

    public void markPaid(String providerInvoiceId, BigDecimal amount) {
        paid.put(providerInvoiceId, amount);
    }
}

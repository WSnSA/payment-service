package com.wsnsa.payment.sandbox;

import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.payment.Invoice;
import com.wsnsa.payment.payment.InvoiceRepository;
import com.wsnsa.payment.payment.InvoiceService;
import com.wsnsa.payment.provider.ProviderType;
import com.wsnsa.payment.provider.impl.SandboxProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SANDBOX нэхэмжлэхийг "төлөх". Provider webhook-ийг дуурайж, ердийн шалгалтын урсгалаар PAID болгоно.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sandbox")
@ConditionalOnProperty(prefix = "payment", name = "sandbox-enabled", havingValue = "true")
public class SandboxController {

    private final SandboxProvider sandbox;
    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;

    @Operation(summary = "SANDBOX нэхэмжлэхийг төлөгдсөн болгох")
    @PostMapping("/sandbox/invoices/{id}/pay")
    public Map<String, String> pay(@PathVariable String id) {
        Invoice invoice = invoices.findById(id)
                .filter(i -> i.getProvider() == ProviderType.SANDBOX && i.getProviderInvoiceId() != null)
                .orElseThrow(() -> ApiException.notFound("Sandbox invoice"));
        sandbox.markPaid(invoice.getProviderInvoiceId(), invoice.getAmount());
        return Map.of("status", invoiceService.refresh(id).getStatus().name());
    }
}

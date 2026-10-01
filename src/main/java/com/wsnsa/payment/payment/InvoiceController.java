package com.wsnsa.payment.payment;

import com.wsnsa.payment.merchant.MerchantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
@SecurityRequirement(name = "apiKey")
public class InvoiceController {

    private final InvoiceService service;

    @Operation(summary = "Нэхэмжлэх үүсгэх", description = "Ижил externalRef-ээр дахин дуудвал өмнөх нэхэмжлэхийг буцаана (idempotent).")
    @PostMapping
    public InvoiceResponse create(@AuthenticationPrincipal MerchantPrincipal merchant, @Valid @RequestBody CreateInvoiceRequest request) {
        return service.create(merchant.merchantId(), request);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.get(merchant.merchantId(), id);
    }

    @GetMapping
    public InvoiceResponse getByExternalRef(@AuthenticationPrincipal MerchantPrincipal merchant, @RequestParam String externalRef) {
        return service.getByExternalRef(merchant.merchantId(), externalRef);
    }

    @Operation(summary = "Provider-оос төлөв шалгах")
    @PostMapping("/{id}/check")
    public InvoiceResponse check(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.check(merchant.merchantId(), id);
    }

    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.cancel(merchant.merchantId(), id);
    }
}

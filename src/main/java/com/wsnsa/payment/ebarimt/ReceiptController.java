package com.wsnsa.payment.ebarimt;

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
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipts (И-баримт)")
@SecurityRequirement(name = "apiKey")
public class ReceiptController {

    private final ReceiptService service;

    @Operation(summary = "Баримт гаргах (нэхэмжлэхгүй)", description = "Бэлэн төлбөр, сарын нэгтгэл г.м. Ижил externalRef-ээр дахин дуудвал өмнөхийг буцаана.")
    @PostMapping
    public ReceiptResponse create(@AuthenticationPrincipal MerchantPrincipal merchant, @Valid @RequestBody CreateReceiptRequest request) {
        return service.createStandalone(merchant.merchantId(), request);
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.get(merchant.merchantId(), id);
    }

    @GetMapping
    public ReceiptResponse getByExternalRef(@AuthenticationPrincipal MerchantPrincipal merchant, @RequestParam String externalRef) {
        return service.getByExternalRef(merchant.merchantId(), externalRef);
    }

    @Operation(summary = "Баримт буцаах")
    @PostMapping("/{id}/return")
    public ReceiptResponse returnReceipt(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.returnReceipt(merchant.merchantId(), id);
    }

    @Operation(summary = "FAILED баримтыг дахин илгээх")
    @PostMapping("/{id}/retry")
    public ReceiptResponse retry(@AuthenticationPrincipal MerchantPrincipal merchant, @PathVariable String id) {
        return service.retry(merchant.merchantId(), id);
    }
}

package com.wsnsa.payment.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.payment.Payment;
import com.wsnsa.payment.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Төлбөр, QPay нэхэмжлэх, и-баримт")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /** Төлбөр үүсгэж QPay QR / deeplink буцаана. */
    @Operation(summary = "Төлбөр үүсгэх", description = "QPay-д нэхэмжлэх үүсгэж QR, shortUrl, банкны deeplink буцаана. receiver эсвэл lines өгвөл дэлгэрэнгүй нэхэмжлэх үүснэ.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return toResponse(paymentService.create(request.toCommand()));
    }

    @Operation(summary = "Төлбөрийн мэдээлэл авах")
    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return toResponse(paymentService.get(id));
    }

    @Operation(summary = "Захиалгын дугаараар төлбөрүүд хайх")
    @GetMapping
    public List<PaymentResponse> findByOrder(@RequestParam String orderId) {
        return paymentService.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    /** QPay-ээс төлбөрийн төлөвийг шалгаж шинэчилнэ (жишээ нь хэрэглэгч "Төлсөн" товч дарах үед). */
    @Operation(summary = "QPay-ээс төлөв шалгах", description = "QPay payment/check дуудаж төлөвийг шинэчилнэ. Хэрэглэгч 'Төлсөн' товч дарах үед дуудна.")
    @PostMapping("/{id}/check")
    public PaymentResponse check(@PathVariable String id) {
        return toResponse(paymentService.syncWithQPay(id));
    }

    @Operation(summary = "Нэхэмжлэх цуцлах", description = "Зөвхөн PENDING төлөвтэй төлбөрт.")
    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable String id) {
        return toResponse(paymentService.cancel(id));
    }

    @Operation(summary = "Төлбөр буцаах", description = "Зөвхөн PAID төлбөрт. QPay зөвхөн картын гүйлгээг буцаадаг.")
    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable String id,
            @Valid @RequestBody(required = false) RefundRequest request) {
        return toResponse(paymentService.refund(id, request == null ? null : request.note()));
    }

    @Operation(summary = "И-баримт үүсгэх", description = "Зөвхөн PAID төлбөрт. Аль хэдийн үүссэн бол байгааг нь буцаана.")
    @PostMapping("/{id}/ebarimt")
    public PaymentResponse createEbarimt(@PathVariable String id, @Valid @RequestBody EbarimtRequest request) {
        return toResponse(paymentService.createEbarimt(id, request.receiverType(), request.receiver()));
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment, objectMapper);
    }
}

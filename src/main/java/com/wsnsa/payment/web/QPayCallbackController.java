package com.wsnsa.payment.web;

import com.wsnsa.payment.payment.PaymentNotFoundException;
import com.wsnsa.payment.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QPay төлбөрийн callback. QPay нэхэмжлэх үүсгэхэд өгсөн callback_url-ийг төлбөр төлөгдөх үед дуудна.
 *
 * <p>QPay-ийн шаардлага: HTTP 200, body нь яг "SUCCESS" байх ёстой. Callback-ийн агуулгад итгэхгүй -
 * төлөвийг заавал QPay payment/check API-гаар баталгаажуулна.
 */
@Tag(name = "QPay callback", description = "Зөвхөн QPay дуудна")
@RestController
public class QPayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(QPayCallbackController.class);
    static final String SUCCESS = "SUCCESS";

    private final PaymentService paymentService;

    public QPayCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "QPay төлбөрийн callback", description = "QPay төлбөр төлөгдөхөд дуудна. Үргэлж 200 SUCCESS буцаана.")
    @RequestMapping(path = "/api/v1/qpay/callback", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam(name = "payment_id", required = false) String paymentId,
            @RequestParam(name = "qpay_payment_id", required = false) String qpayPaymentId) {
        log.info("QPay callback received: payment_id={}, qpay_payment_id={}", paymentId, qpayPaymentId);
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("QPay callback without payment_id");
            return SUCCESS;
        }
        try {
            paymentService.syncWithQPay(paymentId);
        } catch (PaymentNotFoundException e) {
            log.warn("QPay callback for unknown payment {}", paymentId);
        } catch (RuntimeException e) {
            // QPay-д өөр хариу буцаахыг хориглосон тул алдааг логлоод SUCCESS буцаана.
            // Төлөвийг дараа нь POST /api/v1/payments/{id}/check-ээр шинэчилж болно.
            log.error("Failed to process QPay callback for payment {}", paymentId, e);
        }
        return SUCCESS;
    }
}

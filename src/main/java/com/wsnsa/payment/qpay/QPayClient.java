package com.wsnsa.payment.qpay;

import com.wsnsa.payment.qpay.dto.EbarimtCreateRequest;
import com.wsnsa.payment.qpay.dto.EbarimtResponse;
import com.wsnsa.payment.qpay.dto.InvoiceRequest;
import com.wsnsa.payment.qpay.dto.InvoiceResponse;
import com.wsnsa.payment.qpay.dto.PaymentCancelRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckResponse;
import com.wsnsa.payment.qpay.dto.PaymentInfo;
import com.wsnsa.payment.qpay.dto.PaymentListRequest;
import com.wsnsa.payment.qpay.dto.PaymentListResponse;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * QPay merchant API v2 клиент. Бүх дуудлага Bearer токентой явах ба QPay 401 буцаавал
 * токеныг шинэчлээд нэг удаа дахин оролдоно.
 */
@Component
public class QPayClient {

    private final RestClient restClient;
    private final QPayTokenManager tokenManager;

    public QPayClient(RestClient qpayRestClient, QPayTokenManager tokenManager) {
        this.restClient = qpayRestClient;
        this.tokenManager = tokenManager;
    }

    /** POST /v2/invoice - нэхэмжлэх үүсгэх (энгийн болон дэлгэрэнгүй). */
    public InvoiceResponse createInvoice(InvoiceRequest request) {
        return call(token -> restClient.post()
                .uri("/v2/invoice")
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InvoiceResponse.class));
    }

    /** DELETE /v2/invoice/{invoice_id} - нэхэмжлэх цуцлах. */
    public void cancelInvoice(String invoiceId) {
        call(token -> restClient.delete()
                .uri("/v2/invoice/{id}", invoiceId)
                .headers(bearer(token))
                .retrieve()
                .toBodilessEntity());
    }

    /** GET /v2/payment/{payment_id} - төлбөрийн мэдээлэл авах. */
    public PaymentInfo getPayment(String paymentId) {
        return call(token -> restClient.get()
                .uri("/v2/payment/{id}", paymentId)
                .headers(bearer(token))
                .retrieve()
                .body(PaymentInfo.class));
    }

    /** POST /v2/payment/check - төлбөр төлөгдсөн эсэхийг шалгах. */
    public PaymentCheckResponse checkPayment(PaymentCheckRequest request) {
        return call(token -> restClient.post()
                .uri("/v2/payment/check")
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentCheckResponse.class));
    }

    /** DELETE /v2/payment/cancel/{payment_id} - төлбөр цуцлах (картын гүйлгээ). */
    public void cancelPayment(String paymentId, PaymentCancelRequest request) {
        call(token -> restClient.method(HttpMethod.DELETE)
                .uri("/v2/payment/cancel/{id}", paymentId)
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    /** DELETE /v2/payment/refund/{payment_id} - төлбөр буцаах (картын гүйлгээ). */
    public void refundPayment(String paymentId, PaymentCancelRequest request) {
        call(token -> restClient.method(HttpMethod.DELETE)
                .uri("/v2/payment/refund/{id}", paymentId)
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    /** POST /v2/payment/list - төлбөрийн жагсаалт. */
    public PaymentListResponse listPayments(PaymentListRequest request) {
        return call(token -> restClient.post()
                .uri("/v2/payment/list")
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentListResponse.class));
    }

    /** POST /v2/ebarimt/create - и-баримт үүсгэх. */
    public EbarimtResponse createEbarimt(EbarimtCreateRequest request) {
        return call(token -> restClient.post()
                .uri("/v2/ebarimt/create")
                .headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EbarimtResponse.class));
    }

    /** GET /v2/ebarimt/{barimt_id} - и-баримтын мэдээлэл авах. */
    public EbarimtResponse getEbarimt(String ebarimtId) {
        return call(token -> restClient.get()
                .uri("/v2/ebarimt/{id}", ebarimtId)
                .headers(bearer(token))
                .retrieve()
                .body(EbarimtResponse.class));
    }

    /** DELETE /v2/ebarimt/{barimt_id} - и-баримт цуцлах. */
    public void cancelEbarimt(String ebarimtId) {
        call(token -> restClient.delete()
                .uri("/v2/ebarimt/{id}", ebarimtId)
                .headers(bearer(token))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(Function<String, T> request) {
        String token = tokenManager.getAccessToken();
        try {
            return execute(request, token);
        } catch (QPayApiException e) {
            if (e.getStatus() != 401) {
                throw e;
            }
            tokenManager.invalidate(token);
            return execute(request, tokenManager.getAccessToken());
        }
    }

    private static <T> T execute(Function<String, T> request, String token) {
        try {
            return request.apply(token);
        } catch (ResourceAccessException e) {
            throw new QPayApiException("QPay request failed: " + e.getMessage(), e);
        }
    }

    private static java.util.function.Consumer<HttpHeaders> bearer(String token) {
        return headers -> headers.setBearerAuth(token);
    }
}

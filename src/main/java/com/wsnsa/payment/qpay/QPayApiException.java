package com.wsnsa.payment.qpay;

/**
 * QPay API алдаа. {@code status} нь QPay-ийн HTTP статус (холболтын алдааны үед 0),
 * {@code error} нь QPay-ийн алдааны код (жишээ: INVOICE_NOTFOUND, PAYMENT_SETTLED).
 */
public class QPayApiException extends RuntimeException {

    public static final String UNAVAILABLE = "QPAY_UNAVAILABLE";

    private final int status;
    private final String error;

    public QPayApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public QPayApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.error = UNAVAILABLE;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "QPayApiException{status=" + status + ", error=" + error + ", message=" + getMessage() + "}";
    }
}

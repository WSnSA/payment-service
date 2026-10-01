package com.wsnsa.payment.provider;

import java.math.BigDecimal;

/**
 * Provider-оос шалгасан төлбөрийн төлөв.
 *
 * @param paidAmount Provider мэдээлсэн бол төлсөн дүн (дүн таарч байгааг шалгана)
 */
public record PaymentStatus(State state, String providerTxnId, BigDecimal paidAmount, String message) {

    public enum State {PAID, PENDING, FAILED}

    public static PaymentStatus paid(String txnId, BigDecimal amount) {
        return new PaymentStatus(State.PAID, txnId, amount, null);
    }

    public static PaymentStatus pending() {
        return new PaymentStatus(State.PENDING, null, null, null);
    }

    public static PaymentStatus failed(String message) {
        return new PaymentStatus(State.FAILED, null, null, message);
    }
}

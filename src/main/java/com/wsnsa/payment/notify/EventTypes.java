package com.wsnsa.payment.notify;

public final class EventTypes {

    public static final String INVOICE_PAID = "invoice.paid";
    public static final String INVOICE_FAILED = "invoice.failed";
    public static final String INVOICE_EXPIRED = "invoice.expired";
    public static final String RECEIPT_ISSUED = "receipt.issued";
    public static final String RECEIPT_FAILED = "receipt.failed";
    public static final String RECEIPT_RETURNED = "receipt.returned";

    private EventTypes() {
    }
}

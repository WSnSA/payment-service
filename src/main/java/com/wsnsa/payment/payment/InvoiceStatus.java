package com.wsnsa.payment.payment;

public enum InvoiceStatus {
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELLED;

    /**
     * Зөвхөн PAID нь эцсийн төлөв. FAILED/EXPIRED/CANCELLED нэхэмжлэхийг provider
     * хожуу төлөгдсөн гэж баталгаажуулбал PAID болгоно (мөнгө орсон тул алдахгүй).
     */
    public boolean isFinal() {
        return this == PAID;
    }
}

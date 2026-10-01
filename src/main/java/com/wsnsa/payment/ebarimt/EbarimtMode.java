package com.wsnsa.payment.ebarimt;

public enum EbarimtMode {
    /** И-баримт ашиглахгүй. */
    DISABLED,
    /** Нэхэмжлэх төлөгдөх бүрт (receipt өгөгдөлтэй бол) автоматаар баримт гаргана. */
    PER_PAYMENT,
    /** Зөвхөн {@code POST /api/v1/receipts}-ээр гараар/нэгтгэж гаргана (жишээ нь сарын нэгтгэл). */
    MANUAL
}

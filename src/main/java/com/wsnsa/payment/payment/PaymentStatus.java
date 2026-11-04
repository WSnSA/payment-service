package com.wsnsa.payment.payment;

public enum PaymentStatus {
    /** QPay-д нэхэмжлэх үүсгэж байна. */
    NEW,
    /** Нэхэмжлэх үүссэн, төлбөр хүлээж байна. */
    PENDING,
    /** Төлөгдсөн (QPay payment/check-ээр баталгаажсан). */
    PAID,
    /** Нэхэмжлэх цуцлагдсан. */
    CANCELLED,
    /** Төлбөр буцаагдсан. */
    REFUNDED,
    /** QPay-д нэхэмжлэх үүсгэж чадаагүй. */
    FAILED
}

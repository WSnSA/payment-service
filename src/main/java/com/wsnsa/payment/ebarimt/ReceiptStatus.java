package com.wsnsa.payment.ebarimt;

public enum ReceiptStatus {
    /** Гаргахаар хүлээгдэж байна (эсвэл дахин оролдоно). */
    PENDING,
    /** PosAPI руу илгээж байна. */
    ISSUING,
    ISSUED,
    /** Оролдлого дууссан, гараар шалгах шаардлагатай. */
    FAILED,
    /** Буцаагдсан (PosAPI DELETE). */
    RETURNED
}

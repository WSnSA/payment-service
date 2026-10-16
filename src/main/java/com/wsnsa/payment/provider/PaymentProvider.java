package com.wsnsa.payment.provider;

/**
 * Төлбөрийн provider-ийн адаптер.
 *
 * <p>Webhook-оор ирсэн өгөгдөлд итгэхгүй: webhook ирэхэд үргэлж {@link #checkStatus}-аар
 * provider-оос шууд шалгаж байж төлөгдсөн гэж тооцно.
 */
public interface PaymentProvider {

    ProviderType type();

    ProviderInvoice createInvoice(ProviderInvoiceRequest request);

    PaymentStatus checkStatus(ProviderContext context, String invoiceId, String providerInvoiceId);

    /** Provider талд нэхэмжлэхийг цуцлах (дэмждэг бол). */
    default void cancel(ProviderContext context, String providerInvoiceId) {
    }
}

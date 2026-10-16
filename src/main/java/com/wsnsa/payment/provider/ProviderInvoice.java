package com.wsnsa.payment.provider;

import lombok.Builder;

import java.util.List;

/**
 * Provider дээр үүссэн нэхэмжлэх.
 *
 * @param providerInvoiceId Provider-ийн нэхэмжлэхийн дугаар (шалгалтад ашиглана)
 * @param qrText    QR кодын өгөгдөл
 * @param qrImage   QR зураг (base64 PNG), байвал
 * @param shortUrl  Богино холбоос
 * @param deeplink  Үндсэн апп руу шилжих холбоос
 * @param paymentUrl Вэб төлбөрийн хуудас
 * @param deeplinks Банкны аппуудын жагсаалт (QPay)
 */
@Builder
public record ProviderInvoice(
        String providerInvoiceId,
        String qrText,
        String qrImage,
        String shortUrl,
        String deeplink,
        String paymentUrl,
        List<DeepLink> deeplinks
) {
}

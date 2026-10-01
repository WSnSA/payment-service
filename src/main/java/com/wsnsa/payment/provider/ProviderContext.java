package com.wsnsa.payment.provider;

/**
 * Provider дуудлагад хэрэгтэй орчин.
 *
 * @param merchantId  Merchant (token cache-ийн түлхүүрт ашиглана)
 * @param config      Merchant-ийн энэ provider-ийн тохиргоо
 * @param callbackUrl Provider-ийн server-to-server мэдэгдэл ирэх манай URL
 * @param redirectUrl Хэрэглэгч төлбөрийн дараа browser/app-аар буцаж ирэх манай URL
 */
public record ProviderContext(String merchantId, ProviderConfig config, String callbackUrl, String redirectUrl) {
}

package com.wsnsa.payment.provider;

public enum ProviderType {
    QPAY,
    HIPAY,
    /** Голомт банкны e-commerce (картаар). */
    GOLOMT_CARD,
    /** Голомт банкны SocialPay deeplink. */
    SOCIALPAY,
    TOKI,
    MONPAY,
    /** Хаан банкны DigiPay. */
    DIGIPAY,
    /** Тест/интеграцид зориулсан хуурамч provider ({@code payment.sandbox-enabled=true} үед). */
    SANDBOX
}

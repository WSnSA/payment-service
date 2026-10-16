package com.wsnsa.payment.provider;

/** Provider-ийн API алдаа эсвэл хүлээгдээгүй хариу. */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

CREATE TABLE payment (
    id                  VARCHAR(45)    NOT NULL PRIMARY KEY,
    order_id            VARCHAR(100),
    amount              NUMERIC(18, 2) NOT NULL,
    currency            VARCHAR(10)    NOT NULL,
    description         VARCHAR(255)   NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    qpay_invoice_id     VARCHAR(100),
    qpay_payment_id     VARCHAR(100),
    paid_amount         NUMERIC(18, 2),
    payment_type        VARCHAR(20),
    qr_text             TEXT,
    qr_image            TEXT,
    short_url           VARCHAR(255),
    deeplinks           TEXT,
    ebarimt_id          VARCHAR(100),
    ebarimt_status      VARCHAR(50),
    ebarimt_lottery     VARCHAR(50),
    ebarimt_qr_data     TEXT,
    error_message       VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at             TIMESTAMP WITH TIME ZONE,
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_payment_qpay_invoice_id ON payment (qpay_invoice_id);
CREATE INDEX ix_payment_order_id ON payment (order_id);

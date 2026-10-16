create table merchants (
    id                          varchar2(36)  primary key,
    code                        varchar2(50)  not null unique,
    name                        varchar2(255) not null,
    api_key_hash                varchar2(64)  not null unique,
    webhook_url                 varchar2(500),
    webhook_secret              varchar2(500) not null,
    active                      number(1)     default 1 not null,
    ebarimt_mode                varchar2(20)  default 'DISABLED' not null,
    ebarimt_posapi_url          varchar2(255),
    ebarimt_merchant_tin        varchar2(20),
    ebarimt_pos_no              varchar2(20),
    ebarimt_district_code       varchar2(10),
    ebarimt_branch_no           varchar2(10),
    ebarimt_classification_code varchar2(10),
    ebarimt_default_item_name   varchar2(255 char),
    ebarimt_measure_unit        varchar2(20 char),
    ebarimt_tax_type            varchar2(20),
    ebarimt_city_tax_rate       number(5, 2),
    created_at                  timestamp with time zone not null,
    updated_at                  timestamp with time zone not null
);

create table merchant_providers (
    id          varchar2(36) primary key,
    merchant_id varchar2(36) not null references merchants (id),
    provider    varchar2(30) not null,
    enabled     number(1)    default 1 not null,
    config      clob         not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    constraint uk_merchant_providers unique (merchant_id, provider)
);

create table invoices (
    id                  varchar2(36)    primary key,
    merchant_id         varchar2(36)    not null references merchants (id),
    external_ref        varchar2(100)   not null,
    provider            varchar2(30)    not null,
    amount              number(18, 2)   not null,
    currency            varchar2(3)     not null,
    description         varchar2(255 char),
    status              varchar2(20)    not null,
    provider_invoice_id varchar2(100),
    provider_txn_id     varchar2(100),
    qr_text             clob,
    qr_image            clob,
    short_url           varchar2(500),
    deeplink            varchar2(1000),
    payment_url         varchar2(1000),
    deeplinks           clob,
    return_url          varchar2(1000),
    metadata            clob,
    receipt_spec        clob,
    paid_amount         number(18, 2),
    paid_at             timestamp with time zone,
    expires_at          timestamp with time zone not null,
    status_message      varchar2(500 char),
    version             number(19)      default 0 not null,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null,
    constraint uk_invoices_merchant_ref unique (merchant_id, external_ref),
    constraint uk_invoices_provider_ref unique (provider, provider_invoice_id)
);
create index ix_invoices_status_created on invoices (status, created_at);
create index ix_invoices_status_expires on invoices (status, expires_at);

create table receipts (
    id              varchar2(36)    primary key,
    merchant_id     varchar2(36)    not null references merchants (id),
    invoice_id      varchar2(36)    references invoices (id),
    external_ref    varchar2(100)   not null,
    type            varchar2(20)    not null,
    status          varchar2(20)    not null,
    customer_tin    varchar2(20),
    consumer_no     varchar2(20),
    total_amount    number(18, 2)   not null,
    total_vat       number(18, 2),
    total_city_tax  number(18, 2),
    spec            clob            not null,
    ddtd            varchar2(50),
    pos_id          varchar2(50),
    lottery         varchar2(500),
    qr_data         clob,
    printed_at      timestamp with time zone,
    posapi_date     varchar2(30),
    attempts        number(10)      default 0 not null,
    next_attempt_at timestamp with time zone,
    last_error      varchar2(1000 char),
    returned_at     timestamp with time zone,
    version         number(19)      default 0 not null,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    constraint uk_receipts_merchant_ref unique (merchant_id, external_ref),
    constraint uk_receipts_invoice unique (invoice_id)
);
create index ix_receipts_status_next on receipts (status, next_attempt_at);

create table webhook_events (
    id               varchar2(36) primary key,
    merchant_id      varchar2(36) not null references merchants (id),
    event_type       varchar2(50) not null,
    payload          clob         not null,
    status           varchar2(20) not null,
    attempts         number(10)   default 0 not null,
    next_attempt_at  timestamp with time zone not null,
    last_status_code number(10),
    last_error       varchar2(1000 char),
    delivered_at     timestamp with time zone,
    created_at       timestamp with time zone not null,
    updated_at       timestamp with time zone not null
);
create index ix_webhook_events_due on webhook_events (status, next_attempt_at);

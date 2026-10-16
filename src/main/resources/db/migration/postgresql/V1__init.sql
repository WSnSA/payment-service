create table merchants (
    id                          varchar(36)  primary key,
    code                        varchar(50)  not null unique,
    name                        varchar(255) not null,
    api_key_hash                varchar(64)  not null unique,
    webhook_url                 varchar(500),
    webhook_secret              varchar(500) not null,
    active                      boolean      not null default true,
    ebarimt_mode                varchar(20)  not null default 'DISABLED',
    ebarimt_posapi_url          varchar(255),
    ebarimt_merchant_tin        varchar(20),
    ebarimt_pos_no              varchar(20),
    ebarimt_district_code       varchar(10),
    ebarimt_branch_no           varchar(10),
    ebarimt_classification_code varchar(10),
    ebarimt_default_item_name   varchar(255),
    ebarimt_measure_unit        varchar(20),
    ebarimt_tax_type            varchar(20),
    ebarimt_city_tax_rate       numeric(5, 2),
    created_at                  timestamp with time zone not null,
    updated_at                  timestamp with time zone not null
);

create table merchant_providers (
    id          varchar(36) primary key,
    merchant_id varchar(36) not null references merchants (id),
    provider    varchar(30) not null,
    enabled     boolean     not null default true,
    config      text        not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    constraint uk_merchant_providers unique (merchant_id, provider)
);

create table invoices (
    id                  varchar(36)    primary key,
    merchant_id         varchar(36)    not null references merchants (id),
    external_ref        varchar(100)   not null,
    provider            varchar(30)    not null,
    amount              numeric(18, 2) not null,
    currency            varchar(3)     not null,
    description         varchar(255),
    status              varchar(20)    not null,
    provider_invoice_id varchar(100),
    provider_txn_id     varchar(100),
    qr_text             text,
    qr_image            text,
    short_url           varchar(500),
    deeplink            varchar(1000),
    payment_url         varchar(1000),
    deeplinks           text,
    return_url          varchar(1000),
    metadata            text,
    receipt_spec        text,
    paid_amount         numeric(18, 2),
    paid_at             timestamp with time zone,
    expires_at          timestamp with time zone not null,
    status_message      varchar(500),
    version             bigint         not null default 0,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null,
    constraint uk_invoices_merchant_ref unique (merchant_id, external_ref),
    constraint uk_invoices_provider_ref unique (provider, provider_invoice_id)
);
create index ix_invoices_status_created on invoices (status, created_at);
create index ix_invoices_status_expires on invoices (status, expires_at);

create table receipts (
    id             varchar(36)    primary key,
    merchant_id    varchar(36)    not null references merchants (id),
    invoice_id     varchar(36)    references invoices (id),
    external_ref   varchar(100)   not null,
    type           varchar(20)    not null,
    status         varchar(20)    not null,
    customer_tin   varchar(20),
    consumer_no    varchar(20),
    total_amount   numeric(18, 2) not null,
    total_vat      numeric(18, 2),
    total_city_tax numeric(18, 2),
    spec           text           not null,
    ddtd           varchar(50),
    pos_id         varchar(50),
    lottery        varchar(500),
    qr_data        text,
    printed_at     timestamp with time zone,
    posapi_date    varchar(30),
    attempts       integer        not null default 0,
    next_attempt_at timestamp with time zone,
    last_error     varchar(1000),
    returned_at    timestamp with time zone,
    version        bigint         not null default 0,
    created_at     timestamp with time zone not null,
    updated_at     timestamp with time zone not null,
    constraint uk_receipts_merchant_ref unique (merchant_id, external_ref),
    constraint uk_receipts_invoice unique (invoice_id)
);
create index ix_receipts_status_next on receipts (status, next_attempt_at);

create table webhook_events (
    id               varchar(36) primary key,
    merchant_id      varchar(36) not null references merchants (id),
    event_type       varchar(50) not null,
    payload          text        not null,
    status           varchar(20) not null,
    attempts         integer     not null default 0,
    next_attempt_at  timestamp with time zone not null,
    last_status_code integer,
    last_error       varchar(1000),
    delivered_at     timestamp with time zone,
    created_at       timestamp with time zone not null,
    updated_at       timestamp with time zone not null
);
create index ix_webhook_events_due on webhook_events (status, next_attempt_at);

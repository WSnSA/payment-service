# CLAUDE.md

Reusable payment + ebarimt (PosAPI 3.0) service. Spring Boot 3.3, Java 17, Gradle. Each client system is a **merchant** with its own API key, provider credentials and ebarimt settings.

## Commands

```bash
./gradlew test       # H2 in PostgreSQL mode + JDK HttpServer stubs (src/test/.../support/StubServer)
./gradlew bootJar
```

Maven Central may return 429 in CI sandboxes; retry with `--max-workers=1`.

## Layout (`src/main/java/com/wsnsa/payment`)

- `provider/`: `PaymentProvider` interface and `impl/` adapters (QPay, HiPay, Golomt card/SocialPay, Toki, MonPay, DigiPay, Sandbox). Adapters are stateless; per-merchant config arrives in `ProviderContext`.
- `payment/`: `InvoiceService` (create/refresh/apply), incoming `WebhookController` (`/webhooks/{provider}/{invoiceId}`), poll/expire jobs.
- `ebarimt/`: `ReceiptService`, `ReceiptBuilder` (spec → PosAPI request), `TaxCalculator`, `PosApiClient`, `EbarimtInfoClient`.
- `notify/`: outbox (`webhook_events`) + `WebhookDispatcher` with HMAC-signed delivery.
- `merchant/`: merchants, encrypted provider configs, admin API.

## Rules

- Never trust provider webhook bodies: a webhook only triggers `InvoiceService.refresh`, which asks the provider API.
- No HTTP calls inside DB transactions. State changes go through `findForUpdate` (row lock) in a `TransactionTemplate`.
- Money is `BigDecimal`. MNT amounts are whole numbers.
- Secrets live in DB columns using `EncryptedStringConverter`, or in env vars; never in code.
- Schema changes need a Flyway migration in **both** `db/migration/postgresql` and `db/migration/oracle`. `ddl-auto` is `none`.
- Don't use `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`: the PostgreSQL driver rejects its null binding. Plain `String` maps to `text`/`clob`.

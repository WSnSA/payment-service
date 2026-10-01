# payment-service

Олон системд дахин ашиглах **төлбөрийн нэхэмжлэх + И-баримт (PosAPI 3.0)** сервис.

Клиент систем бүр (USUG kiosk, вэб дэлгүүр, mini app г.м) нь **merchant** болж бүртгүүлээд өөрийн API түлхүүрээр нэхэмжлэх үүсгэнэ. Төлбөр баталгаажмагц тухайн системийн webhook руу гарын үсэгтэй мэдэгдэл очно. Хэрэв merchant тохируулсан бол И-баримт автоматаар гарна.

| Төлбөрийн хэрэгсэл | `provider` |
|---|---|
| QPay v2 | `QPAY` |
| HiPay | `HIPAY` |
| Голомт e-commerce (карт) | `GOLOMT_CARD` |
| SocialPay | `SOCIALPAY` |
| Toki | `TOKI` |
| MonPay | `MONPAY` |
| DigiPay (Хаан банк) | `DIGIPAY` |
| Тест (мөнгө хөдлөхгүй) | `SANDBOX` |

## Ажиллах зарчим

```
Клиент систем ──X-API-Key──▶ POST /api/v1/invoices ──▶ Provider (QPay, ...)
                                                        │
Хэрэглэгч төлнө ─────────────────────────────────────── ┘
                                                        │ callback
Provider ──────────────────▶ /webhooks/{provider}/{invoiceId}
                                │  body-д итгэхгүй → provider API-аар төлөв шалгана
                                ▼
                        invoice PAID (мөр түгжиж, 1 л удаа)
                                ├─▶ И-баримт (PER_PAYMENT горимд) → PosAPI
                                └─▶ outbox → POST {merchant.webhookUrl}  (X-Payment-Signature)
```

- **Webhook-д итгэхгүй.** Provider-оос ирсэн мэдэгдэл нь зөвхөн "шалга" гэсэн дохио. Төлөвийг үргэлж provider-ийн API-аар баталгаажуулна. Хуурамч хүсэлт явуулаад төлөгдсөн болгох боломжгүй.
- **Нэг удаа л PAID болно.** Webhook, poll, гар шалгалт зэрэг ирсэн ч `SELECT ... FOR UPDATE` болон `@Version`-оор хамгаалагдсан тул `invoice.paid` ганцхан удаа үүснэ.
- **Idempotent.** `externalRef` нь merchant дотроо давтагдахгүй. Ижил хүсэлтийг дахин явуулбал өмнөх нэхэмжлэх буцна. Дүн эсвэл provider өөр бол `409` буцаана.
- **Webhook алдагдсан тохиолдолд** сүүлийн 2 цагийн PENDING нэхэмжлэхүүдийг минут тутам provider-оос шалгана. `invoice-ttl` хэтэрсэн нэхэмжлэх EXPIRED болно. Хожуу төлөгдвөл PAID болно, мөнгө алдагдахгүй.
- **Нууц мэдээлэл.**
  - Provider-ийн тохиргоо, webhook secret, сугалааны дугаар DB-д AES-256-GCM-ээр шифрлэгдэнэ.
  - API түлхүүрийн зөвхөн SHA-256 хадгалагдана.
  - Код дотор ямар ч нууц үг бичигдээгүй.

## Хурдан эхлэх

```bash
export DB_URL=jdbc:postgresql://localhost:5432/payment DB_USERNAME=payment DB_PASSWORD=...
export PAYMENT_PUBLIC_BASE_URL=https://pay.example.mn
export PAYMENT_ADMIN_API_KEY=$(openssl rand -hex 32)
export PAYMENT_ENCRYPTION_KEY=$(openssl rand -base64 32)   # алдвал хадгалсан нууцуудыг тайлах боломжгүй!
./gradlew bootRun
```

Swagger UI: `/swagger-ui.html`. DB schema-г Flyway үүсгэнэ: PostgreSQL → `db/migration/postgresql`, Oracle → `db/migration/oracle`.

### Тохиргоо (env)

| Хувьсагч | Тайлбар |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL эсвэл Oracle |
| `PAYMENT_PUBLIC_BASE_URL` | Provider-ууд callback илгээх, гаднаас хандах хаяг |
| `PAYMENT_ADMIN_API_KEY` | `/admin/**` API-ийн түлхүүр |
| `PAYMENT_ENCRYPTION_KEY` | AES-256 түлхүүр (base64, 32 байт) |
| `PAYMENT_INVOICE_TTL` | Нэхэмжлэхийн хугацаа, default `PT24H` |
| `PAYMENT_SANDBOX_ENABLED` | `SANDBOX` provider (production-д `false`) |
| `PAYMENT_SCHEDULING_ENABLED` | Background job-ууд. Олон instance-тай бол зөвхөн нэг дээр нь `true` |
| `EBARIMT_INFO_API_URL` | default `https://api.ebarimt.mn` |
| `EBARIMT_SEND_CRON` | PosAPI sendData, default `0 0 23 * * *` (Улаанбаатарын цагаар) |
| `EBARIMT_EASY_AUTH_URL`, `EBARIMT_EASY_API_URL`, `EBARIMT_EASY_CLIENT_ID`, `EBARIMT_EASY_USERNAME`, `EBARIMT_EASY_PASSWORD` | Easy Register (иргэний `consumerNo` шалгах). Хоосон бол шалгалт хийгдэхгүй. |

## Merchant бүртгэх (admin)

```bash
curl -X POST $BASE/admin/v1/merchants -H "X-Admin-Key: $ADMIN" -H 'Content-Type: application/json' -d '{
  "code": "usug",
  "name": "УСУГ ухаалаг ус түгээх",
  "webhookUrl": "https://services.usug.mn/app/api/v1/payment-events",
  "ebarimt": {
    "mode": "PER_PAYMENT",
    "posApiUrl": "http://10.0.0.5:7080",
    "merchantTin": "12345678901",
    "posNo": "10012345",
    "districtCode": "25",
    "branchNo": "001",
    "classificationCode": "3600100",
    "defaultItemName": "Байгалийн ус",
    "measureUnit": "литр",
    "taxType": "VAT_ABLE",
    "cityTaxRate": 0
  }
}'
# → { "merchant": {...}, "apiKey": "pk_...", "webhookSecret": "whsec_..." }   ← зөвхөн нэг удаа харагдана
```

Бусад admin endpoint-ууд:
- `PUT /admin/v1/merchants/{id}`
- `POST /admin/v1/merchants/{id}/rotate-api-key`
- `POST /admin/v1/merchants/{id}/rotate-webhook-secret`
- `GET /admin/v1/merchants/{id}/providers`

### Provider тохиргоо

```bash
curl -X PUT $BASE/admin/v1/merchants/$ID/providers/QPAY -H "X-Admin-Key: $ADMIN" -H 'Content-Type: application/json' \
  -d '{"enabled": true, "config": {"baseUrl": "https://merchant.qpay.mn/v2", "username": "...", "password": "...", "invoiceCode": "..."}}'
```

Хариунд нууц утгууд `********` болж харагдана. Дахин PUT хийхдээ `********` гэж үлдээсэн утга өөрчлөгдөхгүй.

| Provider | Түлхүүрүүд (`*` заавал) |
|---|---|
| `QPAY` | `baseUrl`*, `username`*, `password`*, `invoiceCode`*, `invoiceReceiverCode` |
| `HIPAY` | `baseUrl`*, `token`*, `entityId`* |
| `GOLOMT_CARD`, `SOCIALPAY` | `baseUrl`* (`https://ecommerce.golomtbank.com/api`), `token`*, `hmacKey`*, `paymentPageUrl` |
| `TOKI` | `baseUrl`*, `merchantId`*, `token`*, `apiKey`, `basicUsername`, `basicPassword` |
| `MONPAY` | `baseUrl`*, `token`*, `receiver`* |
| `DIGIPAY` | `baseUrl`*, `clientId`*, `clientSecret`*, `merchantId`*, `terminalId`*, `merchantName` |

## Клиент системийн API (`X-API-Key`)

### Нэхэмжлэх

```bash
curl -X POST $BASE/api/v1/invoices -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{
  "provider": "QPAY",
  "externalRef": "topup-102938",
  "amount": 5000,
  "description": "Цэнэглэлт",
  "returnUrl": "https://miniapp.usug.mn/payment/result",
  "metadata": {"accountId": "A-1", "cardNumber": "..."},
  "receipt": {"consumerNo": "12345678"}
}'
```

Хариунд provider-оос хамаарч `qrText`, `qrImage`, `shortUrl`, `deeplink`, `deeplinks[]` (банкны аппууд), `paymentUrl` ирнэ.

- `options`: provider-ийн нэмэлт параметр. Жишээ нь DigiPay mini app дотор `{"userToken": "..."}`.
- `returnUrl`: хэрэглэгч төлбөрийн дараа `returnUrl?invoiceId=..&externalRef=..&status=PAID` руу буцна.
- `receipt`: И-баримтын өгөгдөл (`ReceiptSpec`). Merchant `PER_PAYMENT` горимтой үед ашиглагдана. Бүх талбар сонголттой; мөр заагаагүй бол merchant-ийн default нэр болон ангиллаар нэг мөр гарна.
  - `type`: `B2C_RECEIPT` эсвэл `B2B_RECEIPT`
  - `customerTin`: B2B үед заавал
  - `consumerNo`: иргэний ebarimt дугаар (8 орон)
  - `paymentCode`: `CASH` эсвэл `PAYMENT_CARD`
  - `items[]`: `name`, `quantity`, `unitPrice` (НӨАТ багтсан), `classificationCode`, `measureUnit`, `taxType`, `barCode`. Мөрүүдийн нийлбэр `amount`-тай тэнцүү байх ёстой.
  - `enabled: false`: энэ нэхэмжлэхэд баримт гаргахгүй

Бусад нэхэмжлэхийн endpoint-ууд:
- `GET /api/v1/invoices/{id}`
- `GET /api/v1/invoices?externalRef=...`
- `POST /api/v1/invoices/{id}/check`: provider-оос шууд шалгана
- `POST /api/v1/invoices/{id}/cancel`
- `GET /api/v1/providers`: идэвхтэй хэрэгслүүд

### И-баримт

- `POST /api/v1/receipts`: нэхэмжлэхгүй баримт. Жишээ нь бэлэн төлбөр эсвэл сарын нэгтгэл; `MANUAL` горимд ашиглана.
  ```json
  {"externalRef": "2026-09-cust-77", "amount": 11000,
   "receipt": {"type": "B2B_RECEIPT", "customerTin": "12345678901",
               "items": [{"name": "Ус", "quantity": 1000, "unitPrice": 11, "measureUnit": "литр"}]}}
  ```
- `GET /api/v1/receipts/{id}`
- `GET /api/v1/receipts?externalRef=...`
- `POST /api/v1/receipts/{id}/return`: буцаалт
- `POST /api/v1/receipts/{id}/retry`
- `GET /api/v1/ebarimt/posapi/info`
- `POST /api/v1/ebarimt/posapi/send`
- `GET /api/v1/ebarimt/taxpayers/{regNo}`: регистрээр ТТД, нэр
- `GET /api/v1/ebarimt/consumers/{consumerNo}`
- `GET /api/v1/ebarimt/branches`

**Merchant-ийн ebarimt горим:**
- `DISABLED`: баримт гаргахгүй.
- `PER_PAYMENT`: нэхэмжлэх төлөгдөх бүрт баримт автоматаар гарна.
- `MANUAL`: зөвхөн `POST /api/v1/receipts`-ээр гаргана.

**Татвар:** үнэд багтсан дүнгээс задлан тооцоолно.
- `VAT_ABLE`: НӨАТ = дүн × 10 / (110 + НХАТ%)
- НХАТ = дүн × НХАТ% / (110 + НХАТ%)

**Алдаа ба дахин оролдлого:** PosAPI алдаа өгвөл 1м, 5м, 15м, 1ц, 3ц, 6ц завсартай дахин оролдоно. Эцэст нь `FAILED` болж `receipt.failed` мэдэгдэл очно. Өдөр бүр 23:00-д merchant бүрийн PosAPI дээр `sendData` дуудагдана.

## Merchant руу очих webhook

`POST {merchant.webhookUrl}`. Header-үүд:
- `X-Payment-Event`
- `X-Payment-Event-Id`
- `X-Payment-Signature: t=<unix>,v1=<hex>`

```json
{"id": "evt-uuid", "type": "invoice.paid", "createdAt": "2026-10-01T05:33:45Z",
 "data": {"invoiceId": "...", "externalRef": "topup-102938", "provider": "QPAY", "status": "PAID",
          "amount": 5000, "paidAmount": 5000, "providerTxnId": "...", "paidAt": "...",
          "metadata": {"accountId": "A-1"}, "receiptId": "..."}}
```

Event-үүд:
- `invoice.paid`
- `invoice.failed`
- `invoice.expired`
- `receipt.issued`: `ddtd`, `lottery`, `qrData`, `totalVat` агуулна
- `receipt.failed`
- `receipt.returned`

Хүлээн авах тал:
1. Гарын үсгийг шалгана: `v1 == hex(HMAC_SHA256(webhookSecret, t + "." + rawBody))` бөгөөд `t` нь 5 минутаас хуучин биш байх ёстой.
2. `id`-аар давхардлыг шүүнэ.
3. 2xx буцаана.

2xx биш хариу авбал 1м, 5м, 15м, 1ц, 3ц, 6ц, 12ц, 24ц завсартай дахин илгээнэ.

```java
// Жишээ (Java)
boolean ok = WebhookSigner.verify(secret, request.getHeader("X-Payment-Signature"), rawBody,
        Instant.now().getEpochSecond(), 300);
```

## USUG системээс шилжүүлэх

Одоо төлбөрийн код `customer-service` дотор, ebarimt нь `third-service` болон `skm-service` дотор байна.

1. **Бэлтгэл**
   - USUG-ийг merchant болгон бүртгэнэ.
   - `PaymentEnv`, `application.properties` дээрх provider-ийн гэрээний мэдээллийг admin API-аар оруулна.
   - Код дотор бичигдсэн token, нууц үгийг **шинэчилнэ (rotate)**.
2. **customer-service**
   - `InvoiceServiceImpl`-ийн provider дуудлагуудыг `POST /api/v1/invoices` болгоно. `externalRef` нь `Statement.id`, `metadata` нь accountId/cardNumber байна.
   - Данс цэнэглэх логикийг шинэ `invoice.paid` webhook handler руу шилжүүлнэ. Одоогийн `WebhookServiceImpl`-ийн цэнэглэх хэсгийг ашиглах боловч гарын үсэг болон `id` давхардлыг шалгана.
3. **Provider callback URL**
   - Шинэ нэхэмжлэхүүд `/webhooks/...` руу callback авна.
   - Хуучин нэхэмжлэхүүд дуустал `customer-service`-ийн `/api/v1/webhook/*` ажилласаар байна.
4. **И-баримт**
   - `third-service`-ийн `/posapi` болон `skm-service`-ийн сарын нэгтгэлийг `POST /api/v1/receipts` болгоно (`MANUAL` горим).
   - Эсвэл `PER_PAYMENT` горимд шилжинэ.

## Хөгжүүлэлт

```bash
./gradlew test          # H2 (PostgreSQL mode) + stub HTTP server-үүд
./gradlew bootJar
docker build -t payment-service .
```

## Шалгах шаардлагатай зүйлс

- **Toki `/status`:** хариуны бүтэц баримтжуулагдаагүй. `data.status` дотор `SUCCESS`, `COMPLETED`, `PAID` байвал төлөгдсөн гэж үзэж байна.
- **Golomt:** `returnType=POST` ашиглаж байна (хуучин код `GET` ашигладаг байсан). Шалгалт нь `/inquiry` дээр тулгуурладаг тул аль ч тохиолдолд аюулгүй.
- **Oracle migration:** бичигдсэн боловч бодит Oracle дээр ажиллуулж шалгаагүй. PostgreSQL 16 дээр шалгасан.
- **Easy Register:** URL-ууд env-ээр тохируулагдана. Хуучин кодод prod дээр `easy-register-test` эрх ашиглаж байсан тул бодит эрх авах шаардлагатай.

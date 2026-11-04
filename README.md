# payment-service

Төлбөр болон И-баримтын сервис. QPay merchant API v2-той холбогдсон
(`2024.05.14_API_doc_V2` баримт бичиг болон QPay Postman collection-ий дагуу).

Spring Boot 3.5 · Java 21 · Gradle · Flyway · H2 / PostgreSQL

## Ажиллуулах

```bash
cp .env.example .env   # утгуудаа бөглөнө
set -a; . ./.env; set +a
./gradlew bootRun
```

Тест:

```bash
./gradlew test
```

### Тохиргоо (environment variables)

| Хувьсагч | Тайлбар |
|---|---|
| `QPAY_BASE_URL` | Sandbox: `https://merchant-sandbox.qpay.mn`, Production: `https://merchant.qpay.mn` |
| `QPAY_CLIENT_ID`, `QPAY_CLIENT_SECRET` | QPay-ээс олгосон нэвтрэх нэр, нууц үг (Basic auth-аар токен авна) |
| `QPAY_INVOICE_CODE` | QPay-ээс олгосон нэхэмжлэхийн код |
| `QPAY_CALLBACK_BASE_URL` | QPay-ээс хандах боломжтой энэ сервисийн **public** хаяг. Callback `{base}/api/v1/qpay/callback?payment_id=...` руу ирнэ |
| `QPAY_BRANCH_CODE` | Салбарын код (заавал биш) |
| `QPAY_EBARIMT_AUTO_CREATE` | `true` бол төлбөр төлөгдмөгц и-баримт автоматаар үүсгэнэ |
| `QPAY_EBARIMT_RECEIVER_TYPE` | Автомат и-баримтын хүлээн авагч: `CITIZEN` / `COMPANY` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Өгөгдлийн сан. Өгөхгүй бол `./data` дотор H2 файл үүсгэнэ |

## Төлбөрийн урсгал

1. `POST /api/v1/payments` → QPay-д нэхэмжлэх үүсгэж QR (`qrText`, `qrImage`), `shortUrl`,
   банкны апп-уудын `deeplinks` буцаана. Төлөв: `PENDING`.
2. Хэрэглэгч банкны аппаар төлнө.
3. QPay `callback_url`-ийг дуудна → сервис **QPay `payment/check` API-гаар баталгаажуулж** төлөвийг `PAID`
   болгоно, QPay-д `200 SUCCESS` хариулна. (Callback-ийн агуулгад итгэхгүй.)
4. (Сонголтоор) и-баримт үүсгэнэ.

QPay-ийн шаардлагын дагуу: токеныг нэг удаа авч кэшлээд хугацаа дуусахад `refresh` хийнэ;
cron-оор `payment/check`-ийг байнга дуудахгүй — зөвхөн callback ирэх эсвэл хэрэглэгч
"Төлсөн" дарах үед (`/check`) шалгана.

## API

| Method | Path | Тайлбар |
|---|---|---|
| `POST` | `/api/v1/payments` | Төлбөр (QPay нэхэмжлэх) үүсгэх |
| `GET` | `/api/v1/payments/{id}` | Төлбөрийн мэдээлэл |
| `GET` | `/api/v1/payments?orderId=...` | Захиалгын төлбөрүүд |
| `POST` | `/api/v1/payments/{id}/check` | QPay-ээс төлөв шалгаж шинэчлэх |
| `POST` | `/api/v1/payments/{id}/cancel` | Төлөгдөөгүй нэхэмжлэх цуцлах |
| `POST` | `/api/v1/payments/{id}/refund` | Төлбөр буцаах (QPay зөвхөн картын гүйлгээнд) |
| `POST` | `/api/v1/payments/{id}/ebarimt` | И-баримт үүсгэх |
| `GET`/`POST` | `/api/v1/qpay/callback?payment_id=...` | QPay callback (үргэлж `SUCCESS` хариулна) |
| `GET` | `/actuator/health` | Health check |

Энгийн нэхэмжлэх:

```bash
curl -X POST localhost:8080/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"orderId":"ORDER-1","amount":1500,"description":"Order 1"}'
```

Дэлгэрэнгүй нэхэмжлэх (`receiver` эсвэл `lines` өгвөл):

```json
{
  "orderId": "ORDER-2",
  "amount": 200,
  "description": "Order No1311",
  "receiverCode": "83",
  "receiver": {"register": "UZ96021105", "name": "Ganzul", "email": "test@gmail.com", "phone": "88614450"},
  "lines": [{"taxProductCode": "6401", "description": "Бараа", "quantity": 1, "unitPrice": 200}]
}
```

И-баримт:

```bash
curl -X POST localhost:8080/api/v1/payments/{id}/ebarimt -H 'Content-Type: application/json' \
  -d '{"receiverType":"CITIZEN","receiver":"88614450"}'
```

Төлөвүүд: `NEW` → `PENDING` → `PAID` → (`REFUNDED`), эсвэл `PENDING` → `CANCELLED`; QPay-д нэхэмжлэх
үүсгэж чадаагүй бол `FAILED`.

Алдааны хариу: `{"error": "...", "message": "...", "qpayStatus": 400}`. QPay-ийн алдаа `502`,
QPay-д холбогдож чадаагүй бол `503`, буруу төлөв `409`, олдоогүй `404`, validation `400`.

## Бүтэц

- `qpay/` — QPay API v2 клиент (`QPayClient`), токен удирдлага (`QPayTokenManager`), DTO-ууд
- `payment/` — `Payment` entity, `PaymentService` (бизнес логик)
- `web/` — REST controller-ууд, QPay callback, алдааны боловсруулалт

## Deploy (101 сервер)

`.github/workflows/deploy.yml` нь `self-hosted, Windows, X64, prod` runner (101 сервер) дээр
Docker image build хийж `payment-service` контейнерийг **9095** порт дээр асаана.
Commit message-д `#deploy` орсон push (`main` эсвэл энэ PR-ын branch) deploy-г эхлүүлнэ.

Repo → Settings → Secrets and variables → Actions:

| Төрөл | Нэр | Жишээ |
|---|---|---|
| Secret | `QPAY_CLIENT_ID`, `QPAY_CLIENT_SECRET`, `QPAY_INVOICE_CODE` | QPay-ээс олгосон утга |
| Variable | `QPAY_BASE_URL` | `https://merchant.qpay.mn` |
| Variable | `QPAY_CALLBACK_BASE_URL` | `https://tn.usug.mn` |

`QPAY_BASE_URL` нь credential-ийн орчинтой таарах ёстой: production credential-ийг sandbox
руу илгээвэл QPay `NO_CREDENDIALS` буцаана. Callback зам
`{QPAY_CALLBACK_BASE_URL}/api/v1/qpay/callback` нь гаднаас 9095 порт руу дамжиж байх ёстой.
Swagger UI: `http://<server>:9095/swagger-ui.html`.

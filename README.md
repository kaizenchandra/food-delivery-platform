# Saffron marketplace

Runnable incremental platform; the complete enterprise master specification is still unfinished. The current implementation includes OIDC sign-in and provider-initiated revocation, customizable meals, customer-owned saved addresses, persistent multi-line carts, coupon-aware authoritative checkout, authenticated real-time tracking, test-provider payments with signed idempotent webhooks/refunds/reconciliation, order support with refund approval/escalation, durable in-app and opt-in email/SMS/browser-push delivery with local simulators, editable verified reviews with history and moderation, and road routing with a local simulator fallback. The full stack remains a local demonstration, not a certified production deployment.

## Run locally

Java 21, Maven, Node >=20.9, Docker Compose and Chrome for browser tests. From this directory:

```sh
docker compose up -d
mvn -f backend/pom.xml verify
java -jar backend/target/marketplace-0.1.0.jar --spring.profiles.active=local
# In a second terminal
cd frontend
npm ci
npm run dev
# In a third terminal, from project root
node tests/oidc-workflows.cjs
```

Open http://localhost:3008 and select **Sign in securely**. Local Keycloak users `customer`, `restaurant`, `driver`, `admin` have password `local-demo`. These fixture users exist only in the imported development realm. Basic authentication and the browser role switcher have been removed. Use separate browser profiles or sign out/sign in to change identity. Local delivery proof remains `246810`, visible only in the customer's order list. Payments use an independent local hosted sandbox; no real money or card details are collected.

Dependencies: API 8088, Keycloak 8188, PostgreSQL 5544, Kafka 9094, OpenSearch 9208, payment sandbox 8508 and notification simulator 8608. PostGIS uses amd64 emulation on ARM. IAM imports take several seconds on first start. Stop infrastructure with `docker compose stop`; deleting volumes destroys local data.

## Workflows

1. Customer: save an address at `/profile/addresses` → discover/menu → Customize → Add to cart → `/cart` → choose address/coupon/cutlery → review final quote → place order → `/orders/[id]` → Pay in sandbox → provider confirmation → live tracking.
2. Restaurant: sign in → accept → prepare → ready. Customer receives status updates through SSE.
3. Driver: sign in → assign → pickup → Track order → share location with browser consent or Simulate movement → enter delivery proof → deliver.
4. Customer: live state, driver position, sample time and modeled ETA; reconnect can replay missed events. Routes use a configured OSRM road network or a labeled local simulator. Street tiles are opt-in; new cart orders use the saved delivery address; older local single-item demos retain their fixture destination.
5. Administrator: Payments workspace for partial/full refunds, provider reconciliation and issues; ledger and protected log search. PostgreSQL outbox → Kafka → OpenSearch pipeline from the previous increment remains present.

## Implementation

Java 21 / Spring Boot 4.1.2 modular backend, PostgreSQL/PostGIS/Flyway, Kafka, OpenSearch and one Next.js 16.4.6 app with role-specific customer/restaurant/driver/admin/logging workspaces. Root experience is implemented; the full requested route inventory is not.

OIDC uses authorization code + S256 PKCE, verified ID-token roles, opaque HttpOnly sessions persisted through Spring Session JDBC, CSRF, session-fixation protection, provider logout, audit records, 30-minute idle / eight-hour absolute limits and resource ownership based on `sub`. Provider-signed back-channel logout uses PostgreSQL session correlations, replay receipts and revocation tombstones; active SSE sessions close and old cookies are denied. Production mode requires HTTPS issuer/application/callback and Secure cookies. Tokens are held on the server.

Meals have required variants, bounded extras and immutable snapshots. New cart checkout has customer ownership, address revisions, PostGIS serviceability, optimistic cart versions, expiring server quotes, coupon redemption locks and idempotency. The clearly labeled local pricing policy applies a demo 5% tax and one delivery fee per basket. Real-time tracking uses persisted versions, authenticated SSE, cursor replay, bounded connections, heartbeats, reconnect and GPS authorization/validation. A bounded OSRM adapter supplies road geometry/directions and modeled travel ETA; outage, timeout, missing configuration and explicit simulator selection use a clearly labeled approximation. SQL locks and the active-driver uniqueness constraint remain in effect.

## Production configuration

Do not enable the `local` profile. Supply APP_ORIGIN, OIDC_ISSUER, OIDC_CALLBACK, OIDC_CLIENT_SECRET, DB_URL, DB_PASSWORD and appropriate Kafka/search settings. Register the exact callback/logout URLs and the roles claim mapper with your provider. Use a trusted HTTPS ingress under one origin; turn off SSE buffering/compression and allow >120-second streams. Protect database/session payloads with least privilege and encryption. The included Keycloak start-dev realm and unauthenticated single-node search/Kafka are local-only infrastructure.

See [provider revocation and routing](docs/provider-revocation-routing.md) for callback configuration, routing limits, and reproducible end-to-end tests. To enable road routes in the local demo, set `ROUTING_URL=https://router.project-osrm.org` when starting Java; omit it for fully local simulation. Do not rely on public community services for production traffic. Existing clients must sign in again after migration V5.

## Still missing

Mobile OTP, full profiles/export/deletion, production promotion policies, onboarding and menu operations, ranked dispatch, native/background GPS, address geocoding/pin selection and live-traffic ETA, live-payment certification, UPI/wallets/COD, bank/payout settlement reconciliation, complete admin tooling, multi-level support assignment/escalation, external-channel certification, per-event notification preferences, quiet hours and delivery repair, review abuse controls, reorder/invoices, Collector/traces, tenant-safe logging, log live tail/alerts/archival, PWA, full design-system tooling, CI/CD/Helm/AWS and load/fault/accessibility certification. The log indexer still uses default retry exhaustion behavior and is not a no-loss indexing guarantee. No 99.99% availability or target throughput claim.

[Verification](docs/verification.md) · [Architecture](docs/architecture.md) · [Requirements](docs/requirements.csv) · [Increment coverage](docs/increment-coverage.csv)

[Saved addresses and checkout contract](docs/addresses-cart-checkout.md) describes the new workflow, local compatibility endpoint, pricing policy, privacy boundaries and verification. Run `node tests/cart-address-checkout.cjs` for its browser/API/concurrency suite.

[Payment sandbox and recovery contract](docs/payment-sandbox.md) covers Stripe test configuration, signed callbacks, refund reservations, retry limits and known risks. Run `node tests/payments-sandbox.cjs` for local payment end-to-end verification. External Stripe-hosted verification requires account credentials and remains unverified.

[Support, refund approval and reviews](docs/support-reviews.md) describes `/support`, delivered-order feedback, administrator decisions/moderation, access controls and limitations. Run `node tests/aftercare.cjs` for its integrated browser suite.

[Escalation, notifications and review editing](docs/escalation-notifications-review-editing.md) documents manual/deadline escalation, the persistent `/notifications` inbox, versioned edits and hidden-review safeguards. Run `node tests/escalation-notifications-edits.cjs` for its browser and concurrency checks.

[Email, SMS and push channels](docs/notification-channels.md) covers opt-in preferences, native adapters, local capture, service-worker fallback, durable retries, verified contacts and delivery-state semantics. Run `node tests/notification-channels.cjs` and `python3 tests/notification-channel-durability.py` for the local channel workflows. External provider verification remains explicitly limited.

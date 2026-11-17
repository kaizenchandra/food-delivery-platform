# Current verification — email, SMS and browser push channels, 2026-11-17

- Java 21 / Spring Boot 4.1.2 `mvn verify`: **62 JUnit tests passed**, zero failures/errors/skips. Eight new channel tests cover destination/header validation, push SSRF constraints, production/local separation, exact verified-contact gates, masked history, Twilio acceptance versus delivery, VAPID signature/encrypted-payload preparation with current cryptography and an SMTP protocol test refusing authentication without STARTTLS.
- Next.js production build and final TypeScript checking: **passed**. Browser checks used `next start`, actual local OIDC/PostgreSQL and the independent channel simulator.
- `tests/notification-channels.cjs`: **passed** for EMAIL, SMS and PUSH local workflows, service-worker presentation, consent, status transitions and faults.
- `tests/notification-channel-durability.py`: **passed**. An actual captured recipient receipt survived a simulator container restart unchanged. A targeted/removed database clock fixture confirmed that known SMTP ACCEPTED state does not incorrectly become UNKNOWN under local/SMS receipt aging.
- `tests/native-notification-adapters.cjs`: **SMTP positive path passed** through the real adapter and a loopback authenticated STARTTLS fixture, using TLS 1.3. This was actual SMTP transport, not the HTTP email simulator, and did not send external mail. Native browser PushManager registration was attempted with temporary VAPID keys but **did not complete within 18 seconds**; remote push service delivery was not verified.
- Existing `tests/escalation-notifications-edits.cjs`, `tests/payments-sandbox.cjs` and `tests/oidc-workflows.cjs`: **all passed** after channel integration. They retain support/review editing, cursor inbox/read receipts, payment recovery/idempotency/refunds, ownership, logout, tracking and Kafka/OpenSearch investigation evidence.
- Flyway **V11 applied successfully**; existing migrations/data retained. JavaScript syntax and Compose configuration checks passed. Native-test temporary TLS trust, keys and browser profile were removed; ordinary all-local backend configuration was restored and API health is UP.

New local channel workflows actually verified:

1. Explicit preference UI opt-in for fixture email, phone and browser; persistence after reload. Invalid local destinations, private push endpoints, stale preference versions and missing CSRF are rejected.
2. Checkout creates one EMAIL, one SMS and one PUSH job for the same protected inbox event. Independent provider captures show one receipt per delivery key. The customer opens email/SMS capture content in the UI; destinations are masked in history and another customer/administrator cannot read those private captures or acknowledge another recipient's push.
3. With Chrome permission granted, the local bridge passes the delivery to the actual service worker. `showNotification`, `getNotifications()` and the authenticated browser presentation acknowledgment are verified. This is browser presentation, not proof of a human read or remote cloud push transport.
4. Local email outage produces RETRY, then one captured delivery after recovery. A local SMS response lost after provider commit is retried using the original key and produces one simulator message, despite multiple attempts. A permanent SMS rejection stays FAILED while other channels/inbox remain available.
5. A pending email receipt remains ACCEPTED until the simulator confirms DELIVERED. An expired push endpoint fails and disables the matching registration. Opt-out cancels an unsent/retrying email and suppresses future channel fan-out while the inbox event remains.
6. Actual Chrome notification permission denial activates the clearly labeled local in-app push fallback. It does not claim a native popup. Simulator controls are separate from application authorization and are reset in finally; fixture channel preferences are disabled afterward.

The independent simulator initially exposed a table insert placeholder error; retries exhausted to honest UNKNOWN states instead of false success. The simulator was corrected. Browser verification then exposed initialization overwriting an entered destination; the form now waits for preference initialization and retains edits. The full channel suite passed after correction. The optional positive SMTP fixture initially rejected a valid SASL authorization-ID form; after correcting that fixture, a new business event passed STARTTLS/authentication and stable Message-ID assertions. Intermediate failures were not hidden or relabeled as success. Captured email/SMS UI screenshots were visually inspected.

Evidence: [channel workflow](results/notification-channels.json), [browser output](results/notification-channels.txt), [62 unit tests](results/channels-unit-tests.txt), [production build](results/channels-frontend-build.txt), [TypeScript](results/channels-typecheck.txt), [restart/aging](results/channel-durability.json), [native SMTP and push attempt](results/native-notification-adapters.json), [inbox regression](results/channels-inbox-regression.json), [payment regression](results/channels-payment-regression.json), [identity regression](results/channels-oidc-regression.json), [email capture](results/email-simulator.png), [SMS capture](results/sms-simulator.png).

Not verified: external SMTP recipient/inbox delivery, real Twilio/carrier delivery, successful native push-provider registration/delivery, background/offline push across platforms, DSN/bounce or provider webhook flows, multi-device accounts, provider outages across pods, comprehensive accessibility, retention/export/erasure, load/spend/rate limits or cloud deployment. No paid account/send, benchmark or production-readiness claim. Local simulation does not establish exactly-once native delivery: uncertain real sends stop for review, and SMTP/push-service acceptance is distinct from recipient delivery. Full master definition of done remains incomplete.

See [configuration, APIs, reliability/security semantics, reproducible native fixtures and six-pillar review](notification-channels.md).

---

# Previous increment — escalation, notifications and review editing, 2026-11-17

- Java 21 / Spring Boot 4.1.2 `mvn verify`: **54 JUnit tests passed**, zero failures/errors/skips. Five new tests cover escalation deadline bounds, pending-versus-confirmed notification semantics, template validation and worker conflict/error handling.
- Next.js `npm run build`: production compilation and TypeScript passed. Browser verification used the production `next start` build.
- `tests/escalation-notifications-edits.cjs`: **passed** against actual Keycloak, PostgreSQL/PostGIS and the independent local payment provider. V10 applied successfully with existing data retained.
- Existing `tests/payments-sandbox.cjs`, `tests/aftercare.cjs` and `tests/oidc-workflows.cjs`: **all passed** sequentially after this increment. These retain payment ambiguity/reconciliation, support/refund/moderation, authorization, live tracking and Kafka/OpenSearch investigation evidence.

New end-to-end workflows actually executed:

1. Pay and fulfill an order through the existing customer/restaurant/driver workflow. Publish a review and edit rating/text in the customer UI; published restaurant output reflects the new rating.
2. Moderator hides the review; a stale customer edit cannot overwrite that version. The customer refreshes and edits hidden content; it stays HIDDEN and absent from public reviews. Stale restoration is rejected; the administrator UI restores the latest content.
3. Sixteen concurrent edits at one expected version yield one 200 and fifteen 409 responses. Version 5 retains six snapshots (creation, edits and moderation); replaying identical current content is a no-op. Author and administrator history APIs work; another customer cannot read/edit/history and a restaurant cannot edit. Edited HTML-like text renders literally without execution.
4. Customer opens support, escalates through the UI, and administrator acknowledges with an explanation. Acknowledgment leaves the thread OPEN. A subsequent approved ₹50 refund reaches provider-confirmed success and marks escalation COMPLETED. Duplicate acknowledgment and escalation of a closed thread are rejected.
5. Sixteen concurrent escalation attempts yield one transition, one audit action and one notification for each of the two audiences. Cross-customer, driver and customer self-acknowledgment attempts are denied.
6. The test advances only its own persisted fixture deadline. The real scheduled worker automatically escalates it, with one automatic-escalation audit record. An ineligible refund approval rolls back and produces no approval notification.
7. Fifty-five real administrator replies exercise notification cursor pagination: latest page is bounded at 50, older page has no overlap. Customer-owned and ADMIN audience restrictions apply to reads and receipts. Sixteen identical read mutations create one receipt and reduce unread count once. Customer UI marks another item read and reload preserves it; older-page navigation works.
8. A duplicate signed payment callback does not duplicate PaymentSucceeded notifications. Refund confirmation produces its own notice. Notification links navigate to authorized support; the escalated outcome remains visible. A 390-pixel inbox viewport has no page-level horizontal overflow and no browser page errors were observed.

Desktop escalation and mobile inbox screenshots were visually inspected. Intermediate browser runs exposed unstable implicit accessible names on prefilled rating/review controls; explicit labels corrected them, and the complete suite then passed. No failure was ignored or converted into a passing assertion.

Evidence: [new workflow results](results/escalation-notifications-edits.json), [browser output](results/escalation-notifications-edits.txt), [54 unit tests](results/escalation-unit-tests.txt), [production build](results/escalation-frontend-build.txt), [payment regression](results/escalation-payment-regression.json), [support regression](results/escalation-aftercare-regression.json), [identity regression](results/escalation-oidc-regression.json), [escalation screenshot](results/escalation-conversation.png), [mobile inbox](results/notifications-mobile.png).

Notification acceptance means committed to the protected PostgreSQL inbox. Email, SMS, background/native push, preferences and external paging were **not** implemented or verified. Escalation has one level and a shared administrator queue; assignment, reopening and business-hours SLA policies remain incomplete. Existing reviews receive only their migration-time historical snapshot; earlier edited text cannot be reconstructed. No multi-pod/outage, retention/erasure, full accessibility, load, latency/availability or cloud deployment certification was run. External Stripe remains unverified without account credentials. Full master definition of done remains incomplete.

See [API, delivery semantics, configuration, architecture decisions and six-pillar risks](escalation-notifications-review-editing.md).

---

# Previous increment — support, refund approval and reviews, 2026-11-17

- Java 21 / Spring Boot 4.1.2 `mvn -f backend/pom.xml verify`: **49 tests passed**, zero failures/errors/skips. Four new Aftercare tests check rating/category/money validation, text limits, control-character rejection and preservation of plain text for safe rendering.
- Next.js production build and TypeScript: **passed**. Browser verification ran against the actual production frontend (`next start`) and live local backend, Keycloak, PostgreSQL/PostGIS and independent hosted payment sandbox.
- Flyway **V9 successfully applied** without replacing existing data.
- Chrome `tests/aftercare.cjs`: **passed** for delivered-order review UI, support creation/replies, administrator refund approval, provider-confirmed ₹50.00 partial refund, customer financial status, review moderation/restoration, aggregate-rating visibility, resource/role isolation, duplicate/concurrent writes, rollback and mobile layout.
- Existing Chrome `tests/cart-address-checkout.cjs`: **passed** for saved addresses, customized multi-line cart, authoritative pricing/coupons, hosted payment, delivery, privacy, quote/idempotency constraints and ledger allocation.
- Existing Chrome `tests/oidc-workflows.cjs`: **passed** for OIDC, roles/ownership, CSRF, logout replay denial, legacy local compatibility, SSE state/GPS/replay, allocation and Kafka/OpenSearch log investigation.

The new suite starts with a new provider-backed order, pays through the independent hosted sandbox and completes restaurant/driver fulfillment. Sixteen duplicate review submissions return one review. Sixteen duplicate ticket requests create one thread; changed content under the same key is rejected. Unauthorized customer/restaurant/driver access and customer self-approval/moderation are rejected. A stale administrator decision creates no refund. A successful decision reserves the requested ₹50.00 once and the provider worker confirms it; the customer sees SUCCEEDED. Refund approval against an uncaptured cancelled order returns 409 and leaves its OPEN thread/version/message count unchanged. Sixteen concurrent non-financial resolutions commit one decision and one audit action. These are single-API-process concurrency checks, not a multi-pod certification.

Manual hide/restore removes/restores both published review visibility and restaurant aggregate counts. Stale moderation is rejected; reasons are persisted. A review containing literal HTML/event-handler text renders as text, creates no image element and executes no handler in Chrome. This checks stored-HTML execution protection, not full content-abuse or privacy filtering.

Desktop support conversation and 390-pixel mobile screenshots were captured and visually inspected. The first full browser run passed the business checks but exposed navigation overflow at the responsive assertion; navigation now scrolls inside its own bounded area. The open support conversation also refreshes provider refund state. The full suite then passed with no browser page errors. An initial generated test preamble had a syntax error and was corrected before the successful run.

Evidence: [new suite](results/aftercare.json), [browser output](results/aftercare.txt), [unit tests](results/aftercare-unit-tests.txt), [production build](results/aftercare-frontend-build.txt), [cart regression](results/aftercare-cart-regression.json), [OIDC regression](results/aftercare-oidc-regression.json), [desktop](results/support-workspace.png), [mobile](results/support-mobile.png).

Not run: multi-pod/Testcontainers/outage recovery, full accessibility, message-cap exhaustion, pagination/load, retention/deletion/export, live Stripe, external care integration or cloud deployment. No throughput/latency/availability benchmark was run. One thread/review per order, no reopening/escalation/notifications/attachments or review editing. Support refund replacement after a confirmed failure still requires explicit administrator payment operations. Full master definition of done remains incomplete.

See [API, transaction boundaries, six-pillar review and remaining work](support-reviews.md).

---

# Previous increment — payment operations, 2026-11-17

- Java 21 / Spring Boot 4.1.2 `mvn verify`: **45 JUnit tests passed**, zero failures/errors/skips. New tests cover raw webhook signing/rotation/window/limits, rejecting live credentials and untrusted checkout hosts, exact monotone refund allocation, unpaid fulfillment gating, quarantined lookup handling and completed-but-unpaid cancellation reconciliation.
- Next.js `npm run build`: production compilation and TypeScript passed. The final payment browser run used `next start` with this production build.
- Real Chrome `tests/payments-sandbox.cjs`: **passed** with Keycloak OIDC, actual PostgreSQL/PostGIS and a separate persistent HTTP payment simulator. No Stripe test credentials were configured; **external Stripe-hosted interoperability was not run**.
- Updated Chrome `tests/cart-address-checkout.cjs`: **passed**, now paying on the independent hosted sandbox before restaurant/driver fulfillment. Existing saved-address/privacy, two customized lines, coupon race, quote validation, 16 duplicate checkouts and allocation checks remain intact. Cancellation of an unpaid duplicate checkout is asynchronously confirmed UNPAID rather than falsely refunded.
- Chrome `tests/oidc-workflows.cjs`: **passed** against the production frontend: OIDC login/roles, CSRF, ownership denials, compatibility checkout, SSE state/GPS/replay, logout-cookie replay denial, allocation and Kafka/OpenSearch log investigation.
- Flyway V8 applied successfully to the existing database. `docker compose config --quiet`, sandbox Python compilation and JavaScript syntax checks passed.

Payment end-to-end checks actually executed:

1. Place one logical order under a repeated checkout key; unpaid restaurant acceptance is rejected; owning customer reaches hosted payment, and confirmed payment permits preparation/delivery.
2. Sixteen concurrent identical signed callbacks have one monetary effect. Invalid signatures/stale timestamps are rejected, changed content under the same event ID is rejected, and another customer's payment/private administrative operations are denied.
3. Administrator UI issues ₹100.00 partial refund from a ₹342.45 capture; customer sees confirmed partial refund. Sixteen concurrent identical requests for the remaining amount produce one durable refund operation. A committed provider refund whose response is lost is recovered without duplicate money. Over-refund and changed-key parameters are rejected; net capture/refunds and net delivered allocations both equal zero after full refund.
4. Disabled webhooks demonstrate that a success redirect leaves fulfillment unpaid. Administrator reconciliation performs an authenticated provider lookup and recovers the missing capture. Missing refund callbacks recover through provider status/idempotent replay.
5. Decline followed by retry uses the same hosted session. A committed session-creation response timeout persists UNKNOWN and recovers using its original key. Cancellation followed by late capture stays CANCELLED and results in recoverable refund compensation.
6. Pending refund reserves money but records no confirmed return. Over-reservation is rejected; eventual success is applied once. Confirmed failure releases reservation without a refund ledger entry; a new explicitly authorized request succeeds.
7. An asynchronous complete/unpaid payment stays AWAITING_PAYMENT until confirmed. A signed wrong-amount snapshot is quarantined and canonical reconciliation resolves the issue. Distinct repeated expiry events do not repeat terminal transitions or bump order version.
8. Provider checks persist MATCH history. The local provider's cumulative object counts in evidence include previous fixture runs; they are not throughput or charge measurements.

The partial-refund UI screenshot was visually inspected. One intermediate expanded run failed on an incorrect simulator button label; it was corrected. Another regression initially waited only 15 seconds for asynchronous expiration and was aligned with the bounded polling workflow. A later repeat was interrupted by local API/frontend process disappearance (proxy connection refusal); both were restored in persistent terminals and the payment suite passed against the production frontend. No application exception explaining that process exit was observed; host/process durability is not certified by this run.

Evidence: [payment suite](results/payments-sandbox.json), [browser output](results/payments-sandbox.txt), [unit results](results/payment-unit-tests.txt), [production build](results/payment-frontend-build.txt), [cart regression](results/cart-payment-regression.json), [OIDC regression](results/payment-oidc-regression.json), [refund screenshot](results/payment-refund.png).

Not verified: real Stripe account/hosted Checkout/webhook configuration, live funds, UPI/wallets/COD, payout/bank settlement, disputes/chargebacks, multi-pod/Testcontainers/database-outage recovery, complete accessibility, target load/availability or cloud deployment. No performance benchmark was run. Refund allocation is a demo waterfall, not legal tax accounting. Mutation retry exhaustion requires an operator recovery workflow; provider pagination/import and history-retention policies remain unfinished. Full master definition of done remains incomplete.

See [implementation, API, configuration and operational limits](payment-sandbox.md).

---

# Previous increment — saved addresses and persistent checkout, 2026-11-17

- Java 21 / Spring Boot 4.1.2 Maven verify: **33 JUnit tests passed**, zero failures/errors (six new address/money tests).
- Next.js production build and TypeScript: passed after final UI changes.
- Real Chrome `tests/cart-address-checkout.cjs`: passed against Keycloak, PostgreSQL/PostGIS and the running API/Next proxy.
- Updated `tests/oidc-workflows.cjs`: passed OIDC, ownership denial, local compatibility APIs, live tracking/replay, logout denial, ledger and indexed log investigation.
- Migrations V6/V7 add saved addresses, persistent carts, immutable quotes/lines/address snapshots, coupon redemption and access indexes. Existing data retained.

The new browser suite verified address creation/edit/deletion and ownership, persistence across reload, two customized lines in one restaurant, itemized checkout, saved-address routing, non-disclosure to restaurant APIs, stale cart/version rejection, mixed-restaurant rejection, expired quotes, changed menu price, unavailable item, changed address revision, one original key/quote under 16 concurrent duplicate requests, and a two-subject race for a one-redemption coupon (one 200, one 409, one redemption). Delivery allocation equals the simulated capture; a later test cancellation generates a refund.

Verified amount: subtotal 116700 paise − discount 10000 + demo tax 5335 + delivery 3900 = **115935 paise (₹1,159.35)**. This is a local demo tax policy, not production tax validation or real gateway settlement.

Screenshots of the final quote were inspected. The direct-route session initialization race, unstable accessible select name, delayed checkbox feedback and dark-panel button contrast were corrected during verification. Cart names are loaded only for saved item IDs; the UI no longer fetches every restaurant menu.

Evidence: results/cart-address-checkout.json/.txt, results/cart-auth-regression.txt and address-pricing-unit-tests.txt. Legacy `oidc-workflows.cjs` exercises its old single-item API only in explicit local mode; it no longer represents the current checkout UI. The new suite is the end-to-end UI evidence for that path.

Not run: live payment/webhook/reconciliation, Testcontainers/multi-pod fault suite, full accessibility/performance, GPS permission/geocoding/pin-selection browser flow, expired-quote retention/deletion, production tax/promotion policy, native background GPS or cloud deployment. Full master definition of done is still incomplete.

See [API, architecture and remaining work](addresses-cart-checkout.md).

---

# Previous increment — provider revocation and road routing, 2026-11-17

- Java 21 / Spring Boot 4.1.2 `mvn verify`: **27 tests passed**, zero failures/errors (17 previous + 6 routing + 4 logout-token freshness tests).
- Next.js 16.4.6 `npm run build`: production compilation and TypeScript passed, including Leaflet 1.9.4.
- Flyway V5 applied to real PostgreSQL/PostGIS; existing business data retained. Session/correlation deployment requires reauthentication of pre-V5 browser sessions.
- Existing `tests/oidc-workflows.cjs` passed with the durable session registry and routing integration.
- New `tests/revocation-routing.cjs` passed in actual Chrome against Keycloak 26.4.7, PostgreSQL, Kafka, OpenSearch, and **live OSRM** through a loopback-only fault/replay harness.

New end-to-end checks:

1. Actual OIDC login for two independent sessions of one customer and an unrelated customer.
2. Live OSRM road route: **2222.6 meters, 11 geometry points**, maneuver directions, Leaflet geometry rendering and real optional OSM street tiles.
3. Another customer denied route access; explicit simulator selection returns clearly labeled simulated geometry.
4. Injected routing-provider HTTP failure activates fallback; restoring the real provider and moving the driver restores ROAD mode. Null/malformed/outage protections also have focused unit coverage.
5. Keycloak administrator deletes one provider session; application cookie becomes invalid, active SSE delivers revocation, private UI clears, sibling provider session and unrelated user remain valid.
6. Reusing the old application cookie is denied. Replaying the same signed logout token is harmless and does not revoke a new login; tampered signature and malformed JWT are rejected.
7. Keycloak subject-wide signed logout (configured without required sid) revokes both remaining subject sessions; unrelated user remains active. Signed scope is honored rather than widened in the application.
8. The test order completes through the authorized driver API. The test restores IAM client configuration and gateway mode in finally; only identified acceptance fixtures are cleaned up.

Observed provider request-to-cleared-UI delay: **845 ms in one local run**. This includes the one-second SSE revocation check and is not a p95/SLO, load test, or multi-node measurement. Route durations exclude live traffic and destination remains the demo-address fixture.

Evidence: results/revocation-routing.json, results/revocation-routing.txt, routing-unit-tests.txt, provider-revocation-unit-tests.txt. Desktop and mobile map screenshots were captured and visually inspected. Live OSRM and OSM tiles require network access; ordinary local simulation does not.

Corrected during this increment: the test gateway used a trusted TLS client after the local Python certificate store rejected public HTTPS; a user-wide test was aligned with signed subject-only logout semantics; SSE async redispatch was permitted so a revoked session closes cleanly. The automatically rejected all-interface harness was replaced by a loopback-only, secret-protected relay. No logout JWT is logged or written to disk.

Not validated: multi-pod failover, provider callback loss/retry exhaustion, JWKS rotation, production TLS/ingress, disabled-account/role-change event policy, live traffic ETA, saved-address navigation, native background geolocation, load/latency/availability targets. The master definition of done remains incomplete.

See [configuration and test instructions](provider-revocation-routing.md).

---

# Previous increment — OIDC/customization/tracking, 2026-11-17

The complete enterprise master definition of done remains unmet. This report separates the verified increment from earlier evidence.

## Current executed checks

- Java 21, Spring Boot 4.1.2: `mvn -f backend/pom.xml verify` passed. **17 JUnit tests, zero failures/errors**: 5 workflow tests, 5 customization/authorization tests, 5 secure transport policy tests, 2 absolute session lifetime tests.
- Next.js 16.4.6: `npm run build` passed compilation and TypeScript checking after the final UI changes. No ESLint/accessibility/performance certification implied.
- Flyway migrations V1–V4 applied to the real local PostgreSQL/PostGIS database. Existing data retained. V2 ownership migration assigns the seeded restaurant owner to the stable local Keycloak subject; legacy customer orders are not rebound by username.
- `node tests/oidc-workflows.cjs` passed against actual Chrome, Keycloak 26.4.7, PostgreSQL, Kafka, OpenSearch and the Next proxy.

The browser suite covers:

1. Authorization-code OIDC login with PKCE S256, state and nonce; role from verified token; opaque HttpOnly/SameSite session cookie.
2. Anonymous and old Basic-auth requests denied; CSRF-less mutation denied; another customer's order and SSE denied; wrong restaurant/driver denied; customer denied logs; driver denied checkout.
3. Customer selects Large + Extra cheese, quantity two, kitchen instructions and cutlery. Backend quote and checkout are **₹857.00**: (₹289 + ₹80 + ₹40) × 2 + ₹39. Snapshot preserves choices and instructions.
4. Required/duplicate/foreign options rejected. Repeated idempotency key returns the same order; changed cutlery returns 409. **16 concurrent customized requests** sharing one key return one logical order.
5. Customer sees restaurant accept/prepare/ready through real SSE; driver assignment/pickup, valid location and updated order card render in-browser. Null and stale GPS samples rejected; wrong delivery proof rejected.
6. Customer disconnects tracking, driver completes delivery, new SSE connection with the saved cursor replays the persisted DELIVERED event. ETA reaches zero; further GPS publication after completion is forbidden.
7. Logout through the UI invalidates the server session. Reusing the pre-logout cookie from a new browser context returns 401.
8. Administrator OIDC login grants ledger and log access; the completed order has a restaurant payable and searchable indexed events through the Kafka/outbox pipeline.
9. Desktop/mobile tracking screenshots captured and inspected; no uncaught browser errors. This is smoke coverage rather than a full accessibility/responsive matrix.

OIDC login/logout audit rows were separately confirmed in PostgreSQL. Current implementation uses 30-minute idle and eight-hour absolute session limits. Absolute limit and HTTPS/cookie guard behavior are unit tested; eight-hour wall-clock browser expiry and deployed HTTPS certificates were not exercised.

Current entry points `tests/browser.cjs`, `tests/acceptance.py`, and `tests/concurrency.py` delegate to the same OIDC suite; they are not three independent suites and must not be counted as additional coverage.

## Failures corrected during implementation

- Additive migration initially conflicted with existing customization columns; corrected in unapplied V3 using renames, preserving V2 and stored data.
- Concurrent first session/CSRF reads caused JDBC attribute conflicts; successful login now initializes CSRF before redirect and PostgreSQL-specific insertion handles concurrent attributes.
- SSE read a field directly from a transactional proxy; moved reads behind a service method.
- Persisted JSON payload needed object decoding; SSE now sends JSON objects.
- Proxy compression buffered browser EventSource chunks; no-transform and no-buffering headers restored prompt delivery, confirmed in Chrome.
- Tracking validation exceptions initially returned 500; global advice now returns 400, and SQL constraint conflicts return 409.
- Order cards initially showed stale state under live tracking; stream updates now refresh both views.

## Historical evidence and remaining verification

Earlier increment verified local refund simulation, driver uniqueness races and Kafka/outbox search under Basic fixtures. Those old test implementations have been replaced; historical results do not establish coverage for every changed OIDC path. Current tests cover the new scopes above.

Not run: Testcontainers suite, complete security/tenant matrix, back-channel logout, provider MFA/revocation propagation, HTTPS ingress/session-store failover, live provider payments, multiple API pods, collector recovery, Kafka restart/fault injection, dead-letter recovery, alerts, retention/archive/restore, AWS deployment, broad accessibility or load/latency/SLO benchmarks. No production-capacity, no-loss-log, availability or financial certification.

Local UI: http://localhost:3008. API: 8088; IAM: 8188. Fixtures/test orders remain in the local database. Deployment settings and remaining security/identity limitations are in oidc-customization-tracking.md.

Evidence files: results/oidc-workflows.txt and .json, customization-unit-tests.txt, transport-unit-tests.txt, session-unit-tests.txt. A broken-pipe warning during the deliberate stream disconnect is expected and closes that client without affecting other connections.

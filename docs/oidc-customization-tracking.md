# OIDC, customized meals, and real-time tracking

## Authentication architecture

Spring Security owns the OIDC authorization-code exchange with S256 PKCE, state, nonce and ID-token validation. A confidential client secret is required outside local mode. Access/ID tokens are never returned to the browser or stored in browser storage. The browser holds an opaque HttpOnly, SameSite=Lax session cookie; production requires Secure. Spring Session persists authentication in PostgreSQL so a request can reach another API instance. API requests use the session, not Basic auth or a browser-selected username. Spring CSRF tokens protect all unsafe requests, including logout. The successful login initializes CSRF before redirect; PostgreSQL-safe session attribute insertion tolerates concurrent requests. Logout invalidates the application session, removes its cookie and redirects through provider logout. Login/logout are audited without logging tokens.

Only roles in the verified ID-token `roles` claim are mapped: CUSTOMER, RESTAURANT, DRIVER, ADMIN, LOGS. Identity is the OIDC `sub`, scoped by the one configured trusted issuer. Restaurant owner, customer and assigned-driver subjects are checked in PostgreSQL. Another customer's order details and SSE return 404; mutations require explicit ownership. Logs require LOGS and ledger requires ADMIN. Usernames have no authorization significance. Existing pre-OIDC customer fixture orders are deliberately not rebound to identities by matching usernames.

Production configuration requires APP_ORIGIN, OIDC_ISSUER, OIDC_CALLBACK, OIDC_CLIENT_SECRET. All three URLs must be HTTPS, and session cookies must be Secure. Register the exact callback and logout URL with the provider. Keep the app and `/api`, `/oauth2`, `/login`, `/logout` behind one HTTPS origin using the included Next rewrites or a trusted ingress; disable SSE buffering/compression and set upstream timeouts above 120 seconds. Do not trust arbitrary forwarded headers. The application currently uses one configured issuer; multi-issuer identity must use an issuer+subject key before adding providers.

`--spring.profiles.active=local` explicitly enables loopback-only HTTP settings. The Compose Keycloak realm imports local users customer, restaurant, driver, admin and three negative-test accounts; password `local-demo`. The client has direct password grants disabled and requires PKCE S256. These fixture credentials and start-dev IAM settings are not production configuration. Do not import this realm as a production identity directory. Realm data is ephemeral; restarting the same container preserves its writable layer but replacing it reimports fixtures.

Session idle timeout is 30 minutes; a tested application filter enforces an eight-hour absolute limit from verified ID-token issuance and SSE rechecks that limit. Provider-initiated OIDC back-channel logout is now implemented; see provider-revocation-routing.md. Role changes/account disabling must be configured to trigger provider logout. Callback failure/retry handling and organization-specific revocation policy remain deployment concerns. JDBC session payloads contain identity material and must be protected by database least privilege, encrypted storage/backups and a trusted database writer boundary. Production TLS, provider MFA, secret rotation, ingress policies and penetration testing have not been validated here. This is a production-oriented OIDC integration, not production security certification.

## Customization API and invariants

Menu responses include groups with minimum/maximum choice counts and available options. Portion is required; extras are optional. `/api/quote` validates that options are distinct, available and belong to the chosen item; enforces group bounds, quantity 1–20, plain-text instructions <=300 characters; and returns the authoritative price in paise. Client prices are ignored. Checkout repeats these checks under shared menu/option locks and stores selected option names/prices, item name, base/unit prices, notes and cutlery as an immutable snapshot.

Example body for quote and checkout (use actual IDs from menu):

```json
{"item":1,"quantity":2,"options":[5,9],"instructions":"No chilli please","cutlery":true}
```

The checkout idempotency fingerprint covers item, quantity, sorted option IDs, normalized notes and cutlery. Retrying an already successful checkout returns its original result even if catalog prices/availability subsequently change. Changing any cart field under the same key returns 409. A PostgreSQL transaction-scoped advisory lock serializes concurrent retries across application instances. A paneer bowl ₹289 + large ₹80 + cheese ₹40, quantity 2, delivery ₹39 totals ₹857 in the live test. Payment remains a simulation.

The current cart supports one customized item line with quantity, not multiple different meal lines. Required group administration, inventory quantities, allergens and food photography remain outside this increment.

## Tracking API and recovery

- GET `/api/orders/{id}`: authorized current snapshot, without delivery OTP.
- GET `/api/orders/{id}/events`: authenticated SSE snapshot and update events, each with monotonically increasing per-order ID.
- Reconnect using Last-Event-ID or `?after=<version>`. Persisted events replay in batches of 100; a gap resets to the current snapshot.
- POST `/api/orders/{id}/location`: assigned driver only, in ASSIGNED or PICKED_UP; latitude/longitude bounds, finite numbers, sample recency, increasing timestamps and at least two seconds between samples are enforced.

State and location mutations increment the same order version and persist a tracking event in the same PostgreSQL transaction. Every stream checks current resource ownership and stored-session existence on every polling cycle. Logout/expiry terminates active access. Streams last under two minutes, heartbeat once per second, and automatically reconnect in the client. Limits are 200 active streams per API instance and five per subject; a bounded worker pool and one in-flight send per stream cap work. They are local per-instance limits, not global quotas. At high scale replace per-client database polling with notification/fanout and validate slow-client behavior under load.

The UI shows live state, GPS coordinates with sample timestamp, a local schematic map and a modeled ETA. Browser location is obtained only when the driver explicitly clicks Share current location and grants permission. Simulate movement offers a no-account local fallback. Browser background tracking and push are not reliable mobile substitutes; native foreground/background location and platform permission handling are still required operationally.

ETA uses an explicitly simulated destination (12.978,77.606) until address checkout is implemented. Preparing states use fixed estimates; after assignment, location-derived estimates use 1.3× straight-line distance, 200m/min and a three-minute buffer. Delivered ETA is zero. These are demonstration assumptions, not navigation accuracy. Location events are retained in PostgreSQL without an automated deletion policy yet; configure and verify privacy retention before real location use. Data retention, tracking load, multi-AZ failover and native background delivery remain unverified.

## Verification

Run Maven verify, Next build, then `node tests/oidc-workflows.cjs` with the local services and Chrome installed. The suite signs in through actual Keycloak UI, verifies PKCE parameters, customized pricing, immutable choices, repeated/concurrent checkout, rejected option combinations, missing CSRF, cross-user/cross-restaurant/cross-driver denial, live customer state/GPS, invalid GPS, event replay, wrong proof, delivery completion and logout-cookie replay denial. Tests use the seeded local identities; do not point them at production. See verification.md for actual run evidence.

Implementation references: Security.java, TransportPolicy.java, Meals.java, Marketplace.java, Tracking.java, TrackingStream.java, migrations V2–V4, and the Next customer/partner workspace.

## Subsequent increment

Provider back-channel logout, shared revocation tombstones, live OSRM road geometry/directions, Leaflet street maps and automatic simulator fallback were added and verified after this document was first written. See provider-revocation-routing.md and verification.md for the current implementation and evidence.

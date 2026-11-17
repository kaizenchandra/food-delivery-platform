# Provider-initiated revocation and road routing

## What changed

Spring's OIDC back-channel filter validates the provider signature, issuer, audience, mandatory logout claims, event type and nonce prohibition. The configured handler then applies a ten-minute receipt-age / one-minute future-skew limit and records `jti` idempotently. It does not accept unsigned logout instructions from a browser. No CSRF bypass was added to ordinary application mutations.

`ProviderSessions` implements the OIDC session registry with PostgreSQL correlations: configured issuer, client, provider `sid`, subject and application session ID. A signed `sid` token revokes only matching sessions for that issuer/client; a signed subject-only token revokes that subject's sessions authenticated at or before token issuance. Session deletion, durable revocation and audit records commit in one database transaction. Active session requests consult this shared state, so a concurrently resaved servlet session cannot restore access. Receipt tombstones also prevent a login callback racing revocation from becoming usable. Old logout-token retries return 200 without repeating side effects or invalidating a newly authenticated provider session.

SSE rechecks revocation/shared session existence on its one-second tick, emits a `revoked` signal, and closes. The browser clears private order/cart/map state and presents sign-in. Ordinary API requests with old cookies return 401. Security permits only internal ASYNC/ERROR redispatch; initial route/SSE authorization still runs normally. V5 adds the tables/indexes. Old pre-V5 sessions have no correlation and must sign in again.

Correlations/receipts are pruned after 24 hours, longer than the eight-hour maximum application session and ten-minute accepted token age. Native cookie-based application logout still works. Native token validation uses provider JWKS; this implementation does not roll its own signature verification.

## Provider configuration

Configure this confidential client's back-channel URL:

```text
https://your-application.example/logout/connect/back-channel/keycloak
```

Do not point the production provider at localhost, the test gateway or an arbitrary forwarded Host. Permit the provider's HTTPS requests through ingress, apply body/rate limits, keep accurate clocks and use the correct issuer/client registration. The local realm uses `http://host.docker.internal:8088/...` solely so the development Keycloak container can reach the host API.

Keycloak's `backchannel.logout.session.required=true` provides `sid`-scoped revocation. The acceptance suite additionally sets this to false for its subject-wide test, verifies all subject sessions close, then restores client settings. The application deliberately honors token scope; it never interprets a single `sid` logout as a command to revoke every session for that subject. If a provider fails to deliver a callback or sends only a narrower scope, the application cannot infer a wider revocation. Operational monitoring and provider retry policy remain important; role changes/account disabling must be configured to trigger logout rather than assumed to do so automatically.

## Road routing

Set `ROUTING_URL` to a trusted OSRM-compatible service base URL. Only operator configuration chooses the host. HTTPS is mandatory outside explicit local loopback mode. With no URL configured, the full local experience continues using the simulator; no paid account is needed.

`GET /api/orders/{id}/route?mode=auto` rechecks ownership before cache lookup. Coordinates are read from authoritative order/restaurant data, not accepted as arbitrary request destinations. ASSIGNED routes to restaurant pickup; after pickup the route goes to the delivery destination. Unknown partner position is explicitly labeled a restaurant estimate. The customer destination is still the existing **demo address fixture**, until saved-address checkout is built.

The adapter requests OSRM driving routes with GeoJSON, geometry and maneuver steps. It validates response size, numeric coordinates, distances, endpoint proximity, duration, point count and step bounds. The client receives only a fixed response schema with route source, reason, distance, travel time, geometry, directions and timestamp. It never receives provider secrets or arbitrary request URLs. Step text is rendered as escaped text.

Limits: eight outbound requests in flight; 700ms connect timeout; two-second request / 2.3-second total wait; 1MiB response; 2000 geometry points; 80 instructions; five-second failure backoff. Routes are cached by order/version/mode, at most 256 entries, for 15 seconds on success / five seconds on fallback. No provider call occurs inside checkout or fulfillment database transactions. These are per-process limits, not global routing quotas or performance guarantees.

On outage, malformed/no-route response, timeout, backpressure, missing configuration, or explicit simulator selection, the result is marked SIMULATOR. It uses a straight-line approximation (1.3 distance factor, ~12 km/h), never claims to be a road route, and displays the reason. ROAD results use road-network durations without live traffic, plus documented preparation allowances before pickup. ETAs are estimates, not a delivery promise. Terminal orders do not request routing.

Leaflet renders the actual route geometry with pan/zoom. OpenStreetMap tiles are opt-in: the browser explains that the viewed area is shared with OSM. Without tiles or when tile loading fails, route geometry/directions remain usable. Attribution is visible. Location publishing still requires browser consent; background/native navigation and map matching remain outside this implementation.

For local demonstration only, `ROUTING_URL=https://router.project-osrm.org` works without credentials. Do not use public demo routing or community tiles as production infrastructure: run a regional OSRM deployment or a properly licensed/provider-supported service, with appropriate egress privacy and usage policies. There is no automatic third-party egress when ROUTING_URL is blank and street tiles are not selected.

## Reproduce verification

Normal local run remains `docker compose up -d`, Maven verify, and Java with the explicit local profile. To exercise actual provider-side administration in a disposable local IAM container, generate an operator password (or supply your own through the environment):

```sh
python3 - <<'PY'
import pathlib, secrets, os
p=pathlib.Path('/tmp/saffron-iam-admin.env')
p.write_text('LOCAL_IAM_ADMIN_PASSWORD='+secrets.token_urlsafe(24)+'\n')
os.chmod(p,0o600)
PY
docker compose --env-file /tmp/saffron-iam-admin.env -f compose.yml -f compose.iam-test.yml up -d --force-recreate keycloak
```

This replaces only the local development IAM container and reimports the seed realm; do not run it against an identity directory containing real users. It does not delete PostgreSQL/order data.

In separate terminals from this project directory:

```sh
# Test-only, loopback-only gateway. Keeps logout JWTs only in memory.
python3 tests/support/provider_gate.py

# Use this backend instance for this test; do not also run another API on 8088.
ROUTING_URL=http://127.0.0.1:5090 java -jar backend/target/marketplace-0.1.0.jar --spring.profiles.active=local

# Frontend
cd frontend && npm run dev

# Test from project root (requires Chrome and the ephemeral local admin env above)
node tests/revocation-routing.cjs
```

The gateway forwards successful road requests to **live OSRM**, then deliberately injects failure and recovery. Its provider callback relay is protected by an ephemeral path secret; control/replay actions require a separate secret. It stores no logout token on disk and logs no token/secret/path. The test temporarily configures the local client's callback to that relay and restores it in `finally`. This gateway is not application infrastructure and must not be deployed. The local-only test credentials are not in browser/application code.

Executed results are in `docs/results/revocation-routing.json` and `.txt`. The recorded single-run revocation delay is an observation, not an SLO or measured production capacity. Multi-pod failover, provider downtime/retry exhaustion, JWKS rotation, load, live traffic ETA, production TLS and native background navigation were not validated.

## Protocol and library references

- [Spring Security OIDC logout](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/logout.html)
- [OIDC Back-Channel Logout specification](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [Keycloak administration](https://www.keycloak.org/docs/26.4.7/server_admin/index.html)
- [OSRM Route API](https://project-osrm.org/docs/v26.4.0/http)
- [Leaflet guide](https://leafletjs.com/examples/quick-start/)
- [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/)

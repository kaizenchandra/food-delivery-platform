# Saved addresses, persistent cart, and authoritative checkout

Payment behavior in this historical increment is superseded by [provider-backed test payments](payment-sandbox.md): current cart checkout awaits confirmed payment before fulfillment; it no longer captures immediately.

This increment implements another local vertical slice; the full enterprise master specification remains incomplete. Real payment providers, tax compliance, production settlement and complete onboarding/support are not provided by this slice.

## Customer workflow and routes

- `/profile/addresses`: create, edit and delete owned addresses; optional browser geolocation with consent or manual coordinates. Edits use an expected revision; another customer cannot access/mutate the address. Up to 20 active addresses per customer.
- `/cart`: persist up to 20 customized lines / 100 total items, quantities 1–20 per line, from one restaurant. Navigation/reload and sign-in sessions retain the PostgreSQL cart. Quantity/removal controls, cutlery, coupon and delivery-address selection work through APIs.
- `/checkout`: same functional cart/quote experience at a direct URL. Review a server-authoritative quote before placing the order.
- `/orders/[id]`: live tracking for the created order. Routing now uses the immutable saved-address coordinates for these orders, instead of the old fixed demo destination. `/orders` continues to list owned orders.

Menu customization adds items to the saved cart; it no longer places an order directly. The customization preview is an item estimate, while the checkout quote includes the actual basket fees/tax/discount. Cooking instructions and chosen options are retained in each line snapshot. Updating options for an existing saved line is supported by the cart API; the UI currently supports removal/re-adding with new options rather than a dedicated in-cart option editor.

## Persistence and ownership

V6 adds customer-owned addresses, versioned carts, expiring checkout quotes, immutable order lines and coupon-redemption records. V7 indexes active addresses by owner/update time, customer/restaurant order access and restaurant ownership. Existing orders/data are retained.

All address/cart APIs require verified CUSTOMER role and use the OIDC subject from the server session. No request-supplied customer identity is trusted. CSRF remains enabled. Address changes/deletions audit identifiers and action, not address text. A deleted saved address is unavailable to a future checkout; existing order address snapshots are intentionally unchanged.

The customer and currently assigned driver can read a specific order's address. Driver access ends after completion/release. Restaurant order views and persisted tracking payloads do not contain the address snapshot. Restaurant tracking responses also omit partner GPS fields, so a completed delivery does not reveal the customer's destination by exposing the driver's final position. Saved-address route geometry is restricted to the customer/assigned driver; ordinary restaurant/admin route inspection is denied for these orders.

Historical address snapshots, transient quotes, account export/erasure and production retention/encryption policies remain privacy work. Deleting a saved address does not implement account erasure. No address/media is forwarded to external routing unless the operator configures ROUTING_URL; optional browser tiles have their own consent/explanation.

## API contract

All unsafe requests need the session's CSRF header/token from `/api/session`.

| Method | Path | Body / behavior |
|---|---|---|
| GET | `/api/addresses` | Owned active addresses |
| POST | `/api/addresses` | label, recipient, line1/line2, city, postalCode, latitude/longitude |
| POST | `/api/addresses/{id}` | `{version,address:{...}}`; 404 foreign/deleted, 409 stale revision |
| POST | `/api/addresses/{id}/delete` | `{version}`; soft delete |
| GET | `/api/cart` | `{version,content,itemNames}` scoped to customer |
| POST | `/api/cart` | `{version,content}`; compare-and-set, 409 stale tab or mixed restaurant |
| POST | `/api/cart/quote` | `{version}`; revalidate catalog, owned address, serviceability, coupon |
| POST | `/api/cart/checkout` | `{quoteId}` + Idempotency-Key; durable atomic checkout |
| GET | `/api/orders/{id}/lines` | Authorized immutable item snapshots |
| GET | `/api/orders/{id}/delivery-address` | Customer/active assigned driver only |

Cart content example (read actual menu/option/address IDs from APIs):

```json
{
  "lines": [
    {"item": 1, "quantity": 2, "options": [5, 9], "instructions": "No chilli please", "cutlery": false},
    {"item": 2, "quantity": 1, "options": [2], "instructions": "", "cutlery": false}
  ],
  "addressId": "customer-owned-address-uuid",
  "coupon": "HELLO10",
  "cutlery": true
}
```

The old `/api/checkout` single-item API remains available **only in explicit loopback local mode** for previous demos/regression tests. Outside that mode it returns 410 and directs clients to saved-address cart checkout. It has the old fee-only demo pricing and must not be used for production integration. `/api/quote` remains a single-meal customization preview; `/api/cart/quote` is the final basket authority.

## Correctness boundaries

1. Cart writes lock the customer cart row and compare its version. A stale tab cannot overwrite a newer cart. Client prices/totals are not accepted.
2. Quote validates an owned active address, online restaurant and PostGIS 8km serviceability, menu/option availability and selection bounds. Catalog locks are taken in consistent item order. It stores a canonical pricing/address/item snapshot with the cart version and a ten-minute expiry.
3. Checkout locks the idempotency operation and cart, verifies quote ownership/expiry/version, re-evaluates authoritative pricing/address, and requires exact canonical equality. A changed price, name, address revision or availability requires a new reviewed quote.
4. Capture simulation, order/lines/address snapshots, coupon redemption, ledger, audit and outbox commit in one PostgreSQL transaction. The cart clears only on successful commit.
5. Repeating the original key returns the same logical order. Reusing a key with another quote conflicts. A quote cannot produce two orders even with different keys; a consumed quote with a new key returns 409. Distinct quotes for the same cart version cannot both consume that cart.
6. Coupon rows are locked before redemption; the global bound and unique `(coupon, customer)` record prevent overuse. The seeded HELLO10 is a demo 10% discount, capped at ₹100, minimum spend ₹200, one use per customer, global cap 50,000, valid until 2028. No stacking or cancellation-based coupon restoration is implemented.

## Money policy and allocation

Amounts are integer paise; percentage calculations round half up with checked integer arithmetic. This is a clearly labeled **local demo policy**, not a claim about legally applicable tax: food subtotal minus coupon, 5% demo tax on that amount, and one ₹39 delivery fee per basket.

Verified basket:

- Two large paneer meals with cheese: ₹818.00
- One chicken meal: ₹349.00
- Subtotal ₹1,167.00; discount ₹100.00; demo tax ₹53.35; delivery ₹39.00
- Final capture simulation: **₹1,159.35**

At delivery, food after discount becomes RESTAURANT_PAYABLE; demo tax is TAX_PAYABLE; delivery is PLATFORM_DELIVERY_REVENUE. Their sum equals SIMULATED_CAPTURE. Pre-delivery cancellation creates one equal negative SIMULATED_REFUND. These are allocation/reconciliation records in the simulator, **not** a complete double-entry financial ledger, gateway reconciliation, tax filing or payout execution.

## Verification and remaining work

Executed: 33 JUnit tests, production Next.js build, real Chrome `tests/cart-address-checkout.cjs`, and the updated OIDC/tracking regression. The new test uses its own tagged restaurant/menu/coupons for price/availability faults, preserving unrelated catalog data. It tests persistence across reload, two customized lines, exact pricing, address ownership/revision, snapshot privacy/routing, stale cart, mixed restaurant, expired/changed/unavailable quotes, 16 duplicate checkout requests, a concurrent one-redemption coupon race, cancellation refund, fulfillment and allocation equality.

Screenshots were visually inspected; button contrast and a direct-route initialization race were corrected. Test evidence is in results/cart-address-checkout.json/.txt and results/cart-auth-regression.txt. The new browser suite uses seeded local customer identities and resets those dedicated carts; do not run against real customer data.

Not verified here: Testcontainers/rolling-pod faults, load/production latency, full accessibility, address geocoding/pin selection, browser geolocation permission flow, live provider charges/ambiguity, real tax rules, coupon policy management UI, privacy deletion/export, expired quote cleanup, native background GPS or production AWS deployment.

Run after the normal local stack/API/frontend startup:

```sh
mvn -f backend/pom.xml verify
cd frontend && npm run build
# From project root, with Chrome and local Docker available:
node tests/cart-address-checkout.cjs
node tests/oidc-workflows.cjs
```

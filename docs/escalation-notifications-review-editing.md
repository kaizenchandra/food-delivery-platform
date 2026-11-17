# Support escalation, durable notifications and review editing

The in-app-only channel limitation below describes V10. V11 adds [email/SMS/browser-push adapters, consent and local simulators](notification-channels.md), with native SMTP fixture verification and explicitly limited external-provider evidence.

This increment extends the order-care workflow without adding another deployment. PostgreSQL remains authoritative for the support decision, review version and the notifications those changes create. The runnable notification channel is an authenticated **in-app inbox**; email, SMS, browser background push and external paging are not configured or claimed.

## Escalation workflow

An owning customer or administrator can escalate an OPEN support thread once, supplying the expected thread version and an explanation (up to 1000 characters). Escalation state is separate from financial and ticket state:

`NONE → REQUESTED → ACKNOWLEDGED → COMPLETED`

An administrator acknowledges a REQUESTED escalation with an explanation and expected version. Acknowledgment does not resolve the issue or approve a refund. Resolving the support thread or approving its refund completes an existing escalation; normal un-escalated resolution leaves escalation NONE. Closed threads cannot be escalated or acknowledged. Actor, timestamps, explanations and generic audit records are persisted. Escalated requests are prioritized in the bounded support list; the thread shows escalation history and its deadline.

The actual scheduled worker checks up to 20 overdue OPEN/NONE tickets every five seconds. The unresolved-ticket escalation deadline is fixed at creation, defaults to 24 hours, and is configured through `support.escalation-after` (ISO duration, 5 minutes–7 days). Replies do not reset it: this is an unresolved-ticket deadline, not a measured first-response SLA. Existing V9 threads receive a deadline of creation +24 hours during migration. A due time is rechecked inside the locked transaction. Multi-worker/manual races use the same order-first locks and version check; only one escalation and its audience notifications commit. Unexpected errors are left to the scheduler's error handling and retry on its next tick; conflicts are harmless.

## Persistent notification inbox

`/notifications` works for every authenticated role; administrators additionally see the operator audience. Notifications cover checkout/order transitions, payment confirmation/failure/expiry, refund updates/confirmation, support opening/replies/decisions/escalation/acknowledgment and review creation/editing/moderation. Generic templates contain no customer messages, escalation reasons, address, OTP, card data or provider secret. Links are generated and constrained on the server.

Notifications commit **inside the business database transaction**, with a unique event/audience key. An approval that rolls back creates no approval notification. Duplicate callbacks and repeated operations cannot multiply the corresponding inbox event. This is a durable in-app delivery boundary: committed means retrievable in the authenticated inbox, not email delivery, push receipt or human acknowledgment. Source state does not commit and then rely on an unpersisted async callback to create the notification.

Notifications have a monotonic database sequence and exclusive cursor pagination (50 records/page). Read receipts are unique by `(notification_id,subject)`, so concurrent marking is idempotent and an administrator's read does not mark the shared operator event read for other administrators. Backend predicates restrict both listing and read mutations to the recipient or a current ADMIN audience member. Customer/private notices are not disclosed to administrators simply because they are administrators. Unread counts apply across the authorized inbox, not only the displayed page. Read state survives reload/session changes. The active inbox polls every three seconds; it does not run in the browser background after navigating away.

Payment/refund templates distinguish pending approval/status change from confirmed return. Actual money remains authoritative in the existing payment ledger and provider reconciliation. Business changes use existing Kafka outbox publication for their earlier events; inbox delivery itself does not claim Kafka/notification-consumer semantics. Historical pre-V10 notifications are not backfilled.

## Review editing and moderation

The author edits rating/text through the delivered-order review card. Changes require the current review version, validate rating 1–5/text ≤2000 characters and serialize on the review row. Editing and moderation use the same version, so neither can silently overwrite an intervening action. A stale edit returns 409; the UI retains the draft until the customer explicitly refreshes or cancels. Repeating identical content at the current version is a no-op; stale retries still return 409.

A published edit updates the public review and restaurant aggregate immediately. A hidden review remains HIDDEN after editing, with a new operator notification; only an explicit administrator restoration can republish it. The administrator must inspect the current version, and stale moderation is rejected. Plain text remains escaped by React, including edited text.

`review_revisions` snapshots creation and every new edit/moderation version. It preserves previous rating, body, visibility, actor, action and time. Existing reviews are backfilled with their current V9 snapshot, labeled MIGRATED_SNAPSHOT; earlier content cannot be reconstructed from that migration. Authors can view history in the order UI; owning-customer and administrator APIs expose the latest 100 revisions. Customer history excludes actor identities. Restaurant/public review APIs expose only current published content, not private history or order identity. History/notification retention and erasure policies remain unfinished.

## New APIs

All mutations require the existing OIDC session and CSRF protection. UUIDs, ownership and role checks are enforced server-side.

| Method | Path | Contract |
|---|---|---|
| POST | `/api/support/{id}/escalate` | Owned CUSTOMER or ADMIN; `{version,reason}` |
| POST | `/api/support/{id}/acknowledge` | ADMIN; `{version,reason}` |
| POST | `/api/orders/{id}/review/edit` | Owning CUSTOMER; `{version,rating,body}` |
| GET | `/api/orders/{id}/review/history` | Owning CUSTOMER; latest 100 revisions |
| GET | `/api/admin/reviews/{id}/history` | ADMIN; latest 100 revisions |
| GET | `/api/notifications?before={sequence}` | Current subject plus ADMIN audience where authorized; `{items,unreadCount,nextCursor}` |
| POST | `/api/notifications/{id}/read` | Authorized recipient/current ADMIN audience member; idempotent receipt |

V10 adds escalation fields/index, notification records/receipts/indexes and revision history. It preserves prior migrations and business records. No new paid account, library, provider SDK or infrastructure is required.

## Run and verification

Use [the local setup](../README.md), build Java 21 with `mvn -f backend/pom.xml verify`, start the backend in local mode and run `npm run build` in frontend. Serve with `./node_modules/.bin/next start -H 127.0.0.1 -p 3008` or use development mode. From the project root run:

```sh
node tests/escalation-notifications-edits.cjs
node tests/aftercare.cjs
node tests/oidc-workflows.cjs
```

Run suites sequentially: their local customer/driver fixtures are shared. The new browser suite performs real local hosted payment and restaurant/driver fulfillment, customer review edits, hidden-edit/moderation conflicts, review history, support escalation/acknowledgment/refund, and inbox navigation/read/pagination. For the automatic-deadline case it advances only its own persisted fixture deadline and waits for the actual scheduled worker. It also exercises concurrent edits, escalations and receipt creation, cross-user/role denial, duplicate callback notification suppression and no notification for a rolled-back refund decision. Saved acceptance addresses are soft-deleted; historical order/review/support/audit/inbox fixtures remain.

Exact executed results are recorded in [verification.md](verification.md). No load/availability/push-delivery benchmark is implied by browser polling or scheduler intervals.

## Six-pillar decision review and remaining work

| Pillar | Decision/evidence | Risk or remaining validation |
|---|---|---|
| Operational excellence | Audited escalation/acknowledgment, revision history, durable inbox, reproducible deadline fixture | Metrics, notification lag dashboards, operator assignment/escalation runbooks and paging integrations incomplete |
| Security | Owned history/inbox, ADMIN audience checks, CSRF, fixed templates/links, text escaping | Preferences, channel consent, retention/export/erasure, rate controls and production ingress remain incomplete |
| Reliability | Atomic source+notification commit, unique event keys/read receipts, shared optimistic versions, recoverable deadline scan | No multi-pod or database-outage certification; no external email/SMS/push acknowledgment semantics |
| Performance | Indexed due queue, bounded batches, cursor inbox, bounded history | Polling, unread counts, review-history growth and list plans need load validation |
| Cost | Reuses existing API/PostgreSQL; no new queue/provider needed for in-app delivery | Storage retention, polling and future external-channel costs unmeasured |
| Sustainability | No additional always-on deployable; polls only active inbox | Adaptive/event-driven refresh and history lifecycle remain future work |

Escalation currently has one level and a shared administrator queue: no supervisor IAM role, individual assignment, repeated escalation, reopening, business-hours calendars or external incident paging. Notifications lack preferences, email/SMS/native push, archive/delete UI and streaming delivery. Existing support/review APIs still have bounded non-cursor lists, while the new inbox has cursor pagination. Full enterprise specification and production readiness remain incomplete.

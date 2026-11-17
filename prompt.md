````
# FINAL MASTER PROMPT
# Design, Develop, Test, and Deploy an Enterprise Food Delivery Platform Like Zomato/Swiggy with an Integrated Distributed Log Aggregation and Observability Platform

## 1. YOUR ROLE

Act as a cross-functional enterprise engineering team consisting of:

- Principal Software Architect and Technology Lead
- Distributed Systems Architect
- Java 21 and Spring Boot Expert
- Domain-Driven Design and Microservices Architect
- PostgreSQL, PostGIS, Redis, and Kafka Specialist
- Payment and Financial Systems Architect
- Geospatial Dispatch Engineer
- Observability and Log Infrastructure Architect
- Next.js, React, and TypeScript Engineer
- Senior Product Designer and UI/UX Architect
- AWS Well-Architected Solutions Architect
- Security Engineer
- Site Reliability Engineer
- Quality Engineering Lead

Your mission is to **design, implement, verify, secure, benchmark, document, and deploy a complete enterprise-grade food delivery marketplace with integrated centralized logging and observability infrastructure**.

This is an engineering execution assignment, not just an HLD or LLD exercise.

If a repository is available, inspect the existing application, applicable instructions, dependencies, and conventions before modifying anything. Preserve unrelated work.

Deliver runnable source code, working applications, database schemas, event pipelines, automated tests, functional UI, deployment infrastructure, and documentation.

Do not claim that features are implemented or tests passed unless they actually are.

---

# 2. PRODUCT VISION

Build a food delivery marketplace comparable in functional scope to Zomato or Swiggy.

The solution must provide four primary applications:

1. **Customer application:** Discover restaurants, order food, pay, track deliveries, review orders, and request support.
2. **Restaurant partner portal:** Onboard restaurants, manage outlets and menus, process incoming orders, and view financial reports.
3. **Delivery partner application:** Manage availability, accept deliveries, navigate to restaurants/customers, verify pickup and delivery, and inspect earnings.
4. **Platform administrator portal:** Manage users, restaurants, partners, orders, disputes, finances, serviceability, policies, and operations.

In addition, build an **integrated enterprise operational platform** that centralizes application and infrastructure logs, supports full-text search, live tailing, log-based alerting, distributed trace correlation, multi-tenant access control, retention, and archival.

The platform must support a complete local demonstration without paid service accounts or live payment transactions.

---

# 3. NON-NEGOTIABLE TECHNOLOGY STACK

## Backend

- Java 21
- Spring Boot — latest compatible stable release verified against official documentation
- Maven
- Spring Web MVC; reactive APIs only where justified
- Spring Security and OAuth2/OIDC
- Spring Data JPA/JDBC or jOOQ where justified
- PostgreSQL
- PostgreSQL PostGIS for geospatial operations
- Flyway
- Apache Kafka
- Redis where justified
- Resilience4j
- Micrometer
- OpenTelemetry
- JUnit 5
- Mockito
- Testcontainers

## Frontend

- Next.js App Router
- React
- TypeScript
- Tailwind CSS
- Accessible component primitives and a consistent design system
- TanStack Query where appropriate
- React Hook Form and Zod where appropriate
- Maps integration with a local simulator fallback
- Playwright for end-to-end testing

## Log Aggregation and Observability

- OpenTelemetry Collector
- Fluent Bit or a supported alternative where required
- Kafka for durable log transport
- OpenSearch for log indexing and search
- PostgreSQL for operational metadata
- S3-compatible storage for archives
- Prometheus and Grafana
- Structured JSON logging
- W3C Trace Context

## Infrastructure

- Docker and Docker Compose
- Kubernetes
- Helm and/or Terraform
- AWS reference architecture
- CI/CD
- Secure configuration and secrets
- Environment-specific deployment manifests

Verify compatible versions before implementation.

Do not introduce unnecessary infrastructure solely to satisfy a technology checklist.

---

# 4. APPLY THE SIX AWS WELL-ARCHITECTED PILLARS

Every significant architectural decision must be evaluated against:

### 4.1 Operational Excellence

- CI/CD
- Automated testing
- Deployment automation
- Monitoring
- Distributed tracing
- Audit logging
- Runbooks
- Incident response
- Feature flags
- Rollback procedures

### 4.2 Security

- OAuth2/OIDC
- Resource-level authorization
- Service identity
- Tenant isolation
- TLS
- Secure secrets
- Payment webhook verification
- PII protection
- API security
- Auditability
- Log redaction

### 4.3 Reliability

- Multi-AZ architecture
- Transactional Outbox
- Idempotent consumers
- Saga orchestration where justified
- Bounded retries
- Circuit breakers
- Bulkheads
- Timeout budgets
- Dead-letter handling
- Data reconciliation
- Backup and recovery

### 4.4 Performance Efficiency

- Database indexing
- PostGIS spatial indexes
- Query optimization
- Redis caching
- Asynchronous processing
- Kafka partitioning
- OpenSearch indexing
- Autoscaling
- Efficient images
- CDN integration
- Frontend rendering optimization

### 4.5 Cost Optimization

- Right-sized services
- Managed versus self-managed infrastructure
- Log retention tiers
- Search cluster sizing
- Event volume control
- Cache efficiency
- Map API costs
- Payment/notification costs
- Infrastructure usage metrics

### 4.6 Sustainability

- Efficient resource allocation
- Event-driven processing
- Autoscaling down
- Reduced polling
- Efficient network transfer
- Storage lifecycle management
- Optimized application execution

Create a six-pillar architecture review with risks, trade-offs, and verification evidence.

---

# 5. FUNCTIONAL REQUIREMENTS AND PRIORITIES

Preserve the supplied requirement priority model:

- P0: MVP-critical
- P1: Important second-phase capability
- P2: Advanced expansion

Preserve requirement IDs where supplied and maintain a traceability matrix from each ID to implementation, API, UI, and test.

Do not silently downgrade or omit P0 requirements.

## 5.1 Customer — Authentication and Profile

Implement CUS-001 through CUS-009:

- Mobile OTP registration/login/logout
- Profile management
- Saved addresses
- GPS location selection with consent
- Notification preferences
- Optional Google/Apple login
- Account deletion and data export
- Payment method references through gateway-issued tokens

Use an approved IAM/OTP adapter and a deterministic local OTP simulator.

Do not implement insecure production OTP issuance.

## 5.2 Customer — Discovery and Home Page

Implement CUS-010 through CUS-021:

- Location-aware home page
- Nearby serviceable restaurants
- Cuisine/category browsing
- Estimated delivery times
- Ratings, fees, and offers
- Restaurant banners
- Popular restaurants
- Recently ordered restaurants
- Favorites
- Veg-only discovery mode
- Curated collections
- Optional personalized recommendations

Use PostGIS for spatial filtering and documented ETA assumptions.

## 5.3 Customer — Search

Implement CUS-022 through CUS-030:

- Restaurant name search
- Dish search
- Cuisine/dietary search
- Veg/nonveg filtering
- Rating/price filters
- Delivery time/offer filters
- Autocomplete
- Sorting
- Trending searches
- Synonym/typo handling as an advanced capability

Use PostgreSQL search initially. Introduce OpenSearch for food/restaurant search only if justified; do not conflate it with the separate logging index.

## 5.4 Customer — Restaurant and Menu

Implement CUS-031 through CUS-040:

- Restaurant profile, address, operating hours
- Categorized menu
- Food photography
- Descriptions and pricing
- Food variants
- Add-ons/customization
- Availability
- Dietary/allergen information
- Menu search
- Ratings/reviews
- Add to cart

## 5.5 Customer — Cart

Implement CUS-041 through CUS-050:

- Add/remove items
- Change quantity
- Update customizations
- Persist cart across navigation
- Single-restaurant cart for MVP
- Cooking instructions
- Cutlery preference
- Subtotal, tax, fees
- Coupons
- Revalidate menu, pricing, and availability at checkout

## 5.6 Customer — Checkout and Payment

Implement CUS-051 through CUS-061:

- Delivery address
- Itemized pricing
- ETA
- Coupons
- UPI
- Card payments
- Eligible wallets/net banking
- Eligible cash on delivery
- Order confirmation
- Payment error handling
- Safe retry
- Duplicate-order prevention

Use a local payment simulator for complete end-to-end tests and a provider sandbox adapter when configured.

## 5.7 Customer — Order Tracking

Implement CUS-062 through CUS-072:

- Real-time order status
- Restaurant acceptance
- Preparing and ready
- Delivery assignment
- GPS location
- ETA updates
- Masked contact/chat where available
- Notifications
- Cancellation
- Delivery OTP/proof
- Report missing/damaged/incorrect items

## 5.8 Customer — History, Reviews, Support

Implement CUS-073 through CUS-082:

- Order history
- Reorder
- Invoices
- Ratings
- Written reviews
- Support tickets
- Refund requests
- Refund tracking
- Customer care integration

Advanced customer features include subscriptions, scheduled orders, gift cards, group orders, train-station delivery, AI recommendations, loyalty, multilingual support, and dark mode.

---

# 6. RESTAURANT PARTNER REQUIREMENTS

Preserve RES-001 through RES-042.

## 6.1 Restaurant Onboarding

- Restaurant owner registration
- Verification documents
- Restaurant profile
- Address and geolocation
- Cuisine and images
- Operating hours
- Multiple branches
- Online/offline status
- Temporary closures
- Preparation time
- Kitchen capacity
- Staff permissions

## 6.2 Menu and Inventory

- Menu categories
- Food items
- Images
- Prices
- Taxes
- Variants
- Add-ons
- Out-of-stock status
- Time-based menus
- Bulk import/export
- POS integration extension

## 6.3 Order Operations

- Real-time incoming orders
- Accept/reject with reason
- Preparation time
- Preparing state
- Ready-for-pickup state
- Verified driver handoff
- Item unavailability
- Cancellations
- Order history
- Kitchen ticket printing
- Scheduled orders as an extension

## 6.4 Restaurant Financials

- Sales reports
- Completed/cancelled orders
- Commissions
- Fees
- Deductions
- Settlement history
- Downloadable settlement reports
- Ratings
- Promotions

Enforce restaurant ownership and staff-level permissions on every API.

---

# 7. DELIVERY PARTNER REQUIREMENTS

Preserve DEL-001 through DEL-026.

## 7.1 Partner Onboarding

- OTP login
- Identity and vehicle verification
- Profile and vehicle information
- Bank/payout references
- Online/offline availability

## 7.2 Assignment and Fulfillment

- Receive offers
- Accept/reject assignments
- Restaurant navigation
- Pickup verification
- Delivery instructions
- Customer navigation
- GPS location publishing
- Masked contact
- Delivery OTP/proof
- Delivery completion
- Failed delivery handling
- Reassignment

## 7.3 Partner Earnings

- Delivery history
- Earnings
- Payout history
- Incentives
- Disputes
- Support

Use a mobile-first PWA in Next.js for the runnable baseline.

Document limitations of browser background geolocation and push delivery. Provide a native-mobile extension design if those capabilities are required operationally.

---

# 8. ADMINISTRATOR REQUIREMENTS

Implement the administration capabilities from the supplied FRD:

- Admin authentication and RBAC
- Customer management
- Restaurant verification and approval
- Delivery partner verification
- Order monitoring
- Dispute resolution
- Refund oversight
- Commission policies
- Settlement reconciliation
- Promotions
- Serviceability zones
- Delivery policies
- Content moderation
- Business analytics
- Fraud investigation
- Feature flags
- Operational incident management

Administrative operations must be audited and resource-authorized.

---

# 9. RECOMMENDED BACKEND ARCHITECTURE

Compare a modular monolith, microservices, and a hybrid architecture.

Choose a minimal number of deployment boundaries that supports reliable implementation and future scaling.

Recommended logical bounded contexts:

1. Identity and Customer
2. Restaurant and Catalog
3. Discovery
4. Cart, Pricing, and Promotion
5. Order Management
6. Payments and Refunds
7. Delivery and Dispatch
8. Notification
9. Reviews and Support
10. Settlement and Ledger
11. Administration and Policy
12. Log Aggregation and Observability

Do not create 12 independently deployed applications without explaining why each needs its own operational boundary.

Recommended starting deployables:

- API Gateway/BFF
- Customer/Restaurant/Catalog backend
- Order and Checkout backend
- Payment adapter/service
- Delivery/Dispatch backend
- Notification worker
- Log Ingestion
- Log Processing/Indexer
- Log Query/Alerting

Allow modular consolidation when justified.

Define service ownership, persistence boundaries, contracts, and failure paths.

---

# 10. DOMAIN-DRIVEN DESIGN AND PATTERNS

Apply pragmatically:

- DDD bounded contexts
- Aggregates and value objects
- Hexagonal Architecture
- Repository Pattern
- Strategy Pattern for pricing and dispatch
- State Pattern or explicit state machines for orders
- Saga for distributed checkout
- Transactional Outbox
- Transactional Inbox
- Idempotent Consumer
- Optimistic/Pessimistic locking
- Cache-aside
- Circuit Breaker
- Retry with jitter
- Bulkhead
- Deadline propagation
- CQRS for projections where justified
- Anti-Corruption Layer for external providers

Provide Architecture Decision Records explaining when each pattern is used.

Do not force Event Sourcing or TCC into every domain.

---

# 11. CRITICAL DISTRIBUTED BUSINESS WORKFLOWS

## 11.1 Checkout and Order Placement

Implement:

1. Validate customer.
2. Resolve restaurant serviceability.
3. Revalidate cart.
4. Recalculate server-authoritative pricing.
5. Validate coupons.
6. Create an idempotent checkout operation.
7. Persist order snapshot.
8. Coordinate payment.
9. Obtain restaurant acceptance.
10. Confirm order.
11. Trigger dispatch.
12. Publish events reliably.
13. Notify customer.
14. Recover failed/ambiguous operations.

Use durable state and transaction boundaries.

## 11.2 Payment and Refund

Implement:

- Provider payment references
- Signature verification
- Webhook authenticity
- Webhook deduplication
- Status lookup
- Reconciliation
- Full/partial refunds
- Separate financial and fulfillment states
- Durable monetary records

Do not trust frontend redirects as payment proof.

## 11.3 Restaurant Processing

Support acceptance deadlines, rejection reasons, concurrent staff operations, preparation estimates, out-of-stock handling, and pickup verification.

## 11.4 Dispatch

Implement:

1. Find eligible available partners.
2. Rank by proximity, capacity, and ETA.
3. Offer assignment.
4. Atomically claim accepted assignment.
5. Retry a bounded number of partners.
6. Support reassignment.
7. Verify pickup.
8. Track delivery.
9. Verify completion.
10. Recover from driver cancellation or disconnection.

Do not allow conflicting partner assignments unless explicitly permitted by batching rules.

## 11.5 Cancellation and Compensation

Define policies for cancellation:

- Before restaurant acceptance
- During preparation
- Before partner pickup
- After pickup
- After delivery attempt

Separate refunds from order-status changes.

Implement recoverable financial and operational compensation.

---

# 12. KAFKA AND EVENT CONTRACTS

Implement versioned events such as:

- OrderPlaced
- PaymentSucceeded
- PaymentFailed
- RestaurantOrderAccepted
- RestaurantOrderRejected
- OrderPreparing
- OrderReadyForPickup
- DeliveryPartnerAssigned
- OrderPickedUp
- OrderDelivered
- OrderCancelled
- RefundCompleted
- RestaurantMenuUpdated

Event envelopes should include:

- Event ID
- Aggregate ID
- Aggregate version
- Event type
- Schema version
- Correlation ID
- Causation ID
- Timestamp
- Payload

Use transactional outbox where state changes must publish integration events.

Consumers must tolerate duplicates, delays, and out-of-order delivery.

Use real database constraints and version-aware transitions to prevent duplicate side effects.

---

# 13. INTEGRATED LOG AGGREGATION PLATFORM

Build a complete centralized log pipeline for the food delivery system.

The platform must ingest logs from:

- API Gateway
- Customer/Identity components
- Restaurant/Catalog
- Order/Checkout
- Payment
- Delivery/Dispatch
- Notifications
- Settlement
- Kubernetes infrastructure

## 13.1 Collection

Use OpenTelemetry Collector and/or Fluent Bit.

Support:

- Structured JSON
- Plain text
- Java multiline exceptions
- Kubernetes stdout/stderr
- OTLP Logs
- Trusted resource metadata
- Local buffering
- Batching
- TLS
- Authentication

## 13.2 Ingestion

Implement a Spring Boot ingestion API supporting:

- Authenticated batch ingestion
- Payload limits
- Source identity
- Tenant/project/environment scope
- Explicit durability acknowledgment
- Rate limits
- Backpressure
- Rejection metrics

Define whether an acknowledgment means validated, accepted to Kafka, indexed, or searchable.

Use a clearly documented durable Kafka acceptance boundary.

## 13.3 Log Processing

Implement:

- JSON parsing
- Plain-text fallback
- Timestamp normalization
- Severity normalization
- Java stack-trace handling
- Resource enrichment
- Trace correlation
- Sensitive-data redaction
- Duplicate-safe processing
- Schema versioning

Do not execute commands embedded in logs.

## 13.4 OpenSearch Indexing

Implement:

- Versioned index templates
- Bounded field mappings
- Time-based indices/data streams
- Bulk indexing
- Partial-batch retry
- Stable document IDs where available
- Index lifecycle
- Shard sizing
- Replica strategy
- Storage alerts

Do not index arbitrary nested JSON fields without limits.

## 13.5 Search

Implement:

- Full-text search
- Severity
- Service
- Environment
- Namespace
- Time-range filtering
- Trace ID
- Correlation ID
- Structured field filters
- Aggregations
- Cursor pagination
- Saved queries
- Query timeouts
- Search cost limits

Enforce authorization on the backend and search storage boundary.

## 13.6 Live Tail

Use SSE for the initial implementation.

Support:

- Authenticated streaming
- Filters
- Pause/resume
- Reconnection
- Bounded buffers
- Slow-client management
- Missed-log recovery through historical search

## 13.7 Alerting

Implement log-based alerts:

- Payment-provider timeout surge
- Order checkout errors
- Failed dispatch assignments
- Kafka consumer errors
- Database deadlocks
- Excessive HTTP 5xx
- Settlement reconciliation failures

Support alert thresholds, grouping, deduplication, cooldowns, acknowledgments, and runbooks.

## 13.8 Retention and Archival

Support:

- Tenant/environment policies
- Hot storage
- Warm searchable storage where supported
- S3 archive
- Archive manifests
- Restore workflow
- Deletion
- Legal hold where applicable

Account for data in Kafka, backups, archives, dead-letter storage, and Collector buffers.

## 13.9 Log Security

Protect against:

- Cross-tenant leakage
- Forged source identity
- Stored XSS
- Log injection
- Sensitive-data exposure
- Query DSL injection
- Oversized payloads
- Mapping explosion
- Unbounded queries
- Unauthorized exports

Never expose OpenSearch credentials to Next.js browser code.

---

# 14. OBSERVABILITY ACROSS BUSINESS WORKFLOWS

Instrument Java 21 services with:

- OpenTelemetry
- Micrometer
- Structured JSON logging
- Trace ID
- Span ID
- Correlation ID
- Relevant business-operation metadata

Trace example:

Customer Request → Gateway → Order → Payment → Restaurant → Dispatch → Kafka → Notification

Propagate context correctly through HTTP and Kafka.

Use Span Links where asynchronous trace relationships require them.

Do not build extremely long-running traces across hours of business processing.

Track:

- Checkout latency
- Order failures
- Payment ambiguity
- Restaurant acceptance time
- Dispatch assignment latency
- Kafka lag
- OpenSearch indexing lag
- Database saturation
- JVM metrics
- Log loss/rejection
- SLO burn rates

Create a workflow for investigating a failed order starting from an alert, navigating to logs, finding the trace, and inspecting relevant service events.

---

# 15. NEXT.JS APPLICATIONS

Build five functional application experiences.

## 15.1 Customer Web

Route inventory:

- `/`
- `/login`
- `/restaurants`
- `/search`
- `/restaurants/[slug]`
- `/cart`
- `/checkout`
- `/orders`
- `/orders/[id]`
- `/profile`
- `/profile/addresses`
- `/favorites`
- `/support`
- `/offers`

Use working backend APIs.

## 15.2 Restaurant Portal

- Restaurant onboarding
- Outlet management
- Menu editor
- Availability controls
- Incoming orders
- Preparation workflow
- Earnings and settlements
- Reviews

## 15.3 Delivery Partner PWA

- Partner onboarding
- Availability
- Assignment feed
- Offer acceptance
- Pickup
- Navigation
- Delivery verification
- Earnings
- Support

## 15.4 Admin Portal

- User and restaurant approvals
- Partner verification
- Order monitoring
- Refunds
- Disputes
- Settlements
- Serviceability
- Analytics
- Fraud review
- Operational policies

## 15.5 Observability Console

Implement:

- Log Explorer
- Live Tail
- Service Error Dashboard
- Log Search
- JSON inspection
- Trace navigation
- Saved Queries
- Alert Rules
- Alert History
- Ingestion Sources
- Retention Policies
- Tenant Usage
- System Health

All interfaces must be functional rather than static mockups.

---

# 16. FOOD DELIVERY UI/UX DESIGN REQUIREMENTS

Build a polished food delivery UI influenced by the visual patterns of Zomato and Swiggy.

Prioritize:

- Clean white/off-white background
- Warm red/coral accents
- Premium food imagery
- Prominent location selection
- Powerful search
- Attractive restaurant cards
- Cuisine carousels
- Ratings and delivery ETA
- Offer badges
- Intuitive menus
- Sticky cart controls
- Clear checkout summary
- Responsive tracking screens
- Accessible interactive elements

Use original brand identity and authorized imagery.

Do not copy proprietary source code, logos, or artwork.

Implement a consistent design system covering:

- Typography
- Colors
- Spacing
- Shadows
- Border radius
- Cards
- Buttons
- Forms
- Loading states
- Empty states
- Error states
- Mobile interactions
- Reduced motion
- Accessibility

Design mobile-first but verify desktop and tablet layouts.

---

# 17. DATABASE AND STORAGE ARCHITECTURE

## Food Delivery

Use PostgreSQL/PostGIS for:

- Customers
- Addresses
- Restaurants
- Menus
- Carts
- Promotions
- Orders
- Payments
- Refunds
- Delivery partners
- Assignments
- Reviews
- Support
- Settlements
- Outbox/Inbox
- Audit

## Log Platform

Use PostgreSQL for:

- Tenants
- Projects
- Environments
- Log sources
- Ingestion credentials
- Parsing rules
- Redaction rules
- Retention policies
- Saved searches
- Alerts
- Usage
- Audit metadata

Use OpenSearch for log records.

Use S3-compatible storage for approved media, exports, and log archives.

Do not store all high-volume log bodies in PostgreSQL.

Enforce clear database ownership and transactional boundaries.

---

# 18. CAPACITY PLANNING

Calculate capacity for the food delivery marketplace and logging infrastructure separately.

## Marketplace design targets

- 10 million registered customers
- 100,000 restaurants
- 1 million daily orders
- 1,000 peak order creations/sec
- 50,000 restaurant search RPS
- Multi-AZ deployment
- 99.99% availability design target

## Logging design targets

- 1,000 monitored services scale scenario
- 1 million events/sec architectural peak scenario
- 1–10 TB logs/day scenario
- 7–30 days searchable retention
- 90–365 days archival where policy requires
- p95 search under 2 seconds for defined workloads
- Explicit indexing-lag SLO

All targets require validation.

Calculate:

- RPS and concurrency
- Storage
- Network
- Kafka partition requirements
- Database throughput
- Redis capacity
- OpenSearch indexed storage
- Search shard sizing
- S3 archive
- Logs per business transaction
- Telemetry overhead
- Infrastructure costs

Do not present estimates as measured capacity.

---

# 19. SECURITY AND PRIVACY

Implement:

- OAuth2/OIDC
- Spring Security
- RBAC
- Resource-level authorization
- Secure session handling
- Payment-provider webhook verification
- Secure OTP adapter
- TLS
- Secrets management
- Least privilege
- Input validation
- Upload validation
- PII redaction
- Tenant isolation
- Log access control
- Audit logging
- Location privacy
- Retention and deletion
- Rate limiting
- Secure administrative endpoints

Customers must never access other customers' orders.

Restaurants must never access another restaurant's private operations.

Delivery partners must only access authorized assignments.

Observability operators must have explicit permission to inspect production logs.

---

# 20. TESTING REQUIREMENTS

## Backend Unit Tests

Test:

- Restaurant availability
- Menu pricing
- Cart rules
- Coupons
- Order transitions
- Payment idempotency
- Saga recovery
- Dispatch assignment
- Cancellation/refund
- Log parsing
- Redaction
- Search validation
- Alert rules

## Integration Tests

Use Testcontainers for:

- PostgreSQL
- Kafka
- Redis
- OpenSearch

Verify:

- Checkout
- Payment simulation
- Restaurant acceptance
- Delivery assignment
- Event recovery
- Log collection
- Kafka ingestion
- Indexing
- Search
- Live tail
- Alert evaluation
- Tenant isolation

## Concurrency Tests

Verify:

- Duplicate checkout across pods
- Limited coupon redemption
- Double delivery-partner assignment
- Concurrent cancellation
- Duplicate payment webhook
- Duplicate log ingestion
- Concurrent indexing
- Quota enforcement
- Alert-evaluation races

## Frontend Tests

Use:

- TypeScript checking
- ESLint
- Component tests
- Playwright
- Accessibility testing
- Responsive viewport tests

Cover all five Next.js application experiences.

## Fault Injection

Test:

- Payment timeout
- Restaurant rejection
- Kafka outage
- PostgreSQL outage
- Redis outage
- OpenSearch outage
- Collector restart
- Log ingestion backlog
- Driver disconnect
- API timeout
- Worker crash
- Rolling deployment

Report exactly which tests were run.

---

# 21. CRITICAL END-TO-END ACCEPTANCE SCENARIOS

The system must demonstrate:

1. Customer logs in and discovers nearby restaurants.
2. Customer searches and filters restaurants.
3. Customer customizes a meal and adds it to the cart.
4. Backend recalculates authoritative pricing.
5. Customer places an order using an idempotency key.
6. Payment succeeds through a simulator or configured sandbox.
7. Restaurant receives and accepts the order.
8. Restaurant prepares and marks food ready.
9. Delivery partner is assigned without conflicting allocation.
10. Partner confirms pickup.
11. Customer observes delivery progress.
12. Partner completes delivery using proof/OTP.
13. Order reaches a valid completed state.
14. Financial records reconcile.
15. Customer sees order history and can submit support/review.
16. Duplicate checkout cannot create duplicate logical orders.
17. Duplicate payment callbacks do not cause duplicate charges or fulfillment.
18. Restaurant rejection triggers recoverable cancellation/refund.
19. Driver cancellation triggers reassignment.
20. Payment ambiguity does not silently lose money or duplicate financial operations.
21. Business services emit correlated structured logs.
22. Collectors forward logs to the ingestion pipeline.
23. Kafka buffering survives supported retries and restarts.
24. OpenSearch indexes records correctly.
25. Next.js Log Explorer can search a failed order's events.
26. Logs can be filtered by payment service, time, severity, and trace ID.
27. Live Tail shows supported real-time application events.
28. A log-based payment-error alert fires under a controlled fault scenario.
29. A user without log permissions cannot inspect protected logs.
30. Retention and archival operate according to documented policies.

---

# 22. RECOMMENDED MONOREPO STRUCTURE

```text
food-delivery-platform/
├── README.md
├── docs/
│   ├── requirements/
│   ├── architecture/
│   ├── domain-model/
│   ├── api-contracts/
│   ├── event-contracts/
│   ├── security/
│   ├── payments/
│   ├── delivery-dispatch/
│   ├── logging/
│   ├── capacity-planning/
│   ├── aws-well-architected/
│   ├── runbooks/
│   └── decisions/
│
├── backend/
│   ├── pom.xml
│   ├── gateway/
│   ├── customer-restaurant/
│   ├── order-service/
│   ├── payment-service/
│   ├── delivery-service/
│   ├── notification-worker/
│   ├── log-ingestion/
│   ├── log-processing/
│   ├── log-query/
│   └── integration-tests/
│
├── frontend/
│   ├── apps/
│   │   ├── customer-web/
│   │   ├── restaurant-portal/
│   │   ├── delivery-partner-pwa/
│   │   ├── admin-portal/
│   │   └── observability-console/
│   ├── packages/
│   │   ├── ui/
│   │   ├── api-client/
│   │   └── design-tokens/
│   └── package.json
│
├── infrastructure/
│   ├── postgres/
│   ├── redis/
│   ├── kafka/
│   ├── opensearch/
│   ├── otel-collector/
│   ├── object-storage/
│   ├── prometheus/
│   ├── grafana/
│   ├── docker/
│   ├── helm/
│   └── terraform/
│
├── demo-data/
├── contract-tests/
├── end-to-end-tests/
├── benchmarks/
└── .github/workflows/
```

Adjust modules to the actual architectural choices.

Do not generate empty modules or unused abstractions merely to match the reference tree.

---

# 23. IMPLEMENTATION PHASES

Execute the project incrementally.

### Phase 1 — Repository Assessment and Requirements

Review both source specifications and the existing repository.

Create a requirements traceability matrix covering CUS, RES, DEL, admin, and log-platform requirements.

### Phase 2 — HLD and LLD

Define bounded contexts, schemas, APIs, events, consistency models, deployment architecture, and the six-pillar review.

### Phase 3 — Project Foundation

Implement Java 21, Spring Boot, Next.js, Docker Compose, PostgreSQL migrations, IAM integration, and CI checks.

### Phase 4 — Customer and Restaurant Foundation

Implement authentication, profiles, addresses, restaurant onboarding, menus, and catalog.

### Phase 5 — Discovery and Search

Implement PostGIS-based nearby restaurants, filters, search, and customer home page.

### Phase 6 — Cart and Checkout

Implement item customizations, pricing, coupons, cart persistence, and authoritative checkout quotes.

### Phase 7 — Order Management

Implement durable order state, idempotency, cancellation policies, and events.

### Phase 8 — Payments and Refunds

Implement simulated/sandbox provider, webhook verification, reconciliation, and financial correctness.

### Phase 9 — Restaurant Operations

Implement real-time order queue, acceptance, preparation, ready-for-pickup, and handoff.

### Phase 10 — Delivery and Dispatch

Implement driver onboarding, availability, matching, assignment, pickup, and delivery confirmation.

### Phase 11 — Real-Time Tracking

Implement SSE/WebSocket order updates, GPS simulation, ETA updates, and reconnection.

### Phase 12 — Customer/Restaurant/Driver/Admin UI

Complete the four primary functional Next.js applications.

### Phase 13 — Structured Logging

Instrument all backend services with OpenTelemetry and structured JSON logs.

### Phase 14 — Log Collection and Ingestion

Configure OpenTelemetry Collector, source authentication, Spring Boot ingestion, and Kafka buffering.

### Phase 15 — Log Processing and Indexing

Implement parsing, redaction, enrichment, bulk indexing, and recovery.

### Phase 16 — Log Search and Live Tail

Implement tenant-safe search APIs, SSE streaming, saved queries, and the Next.js Log Explorer.

### Phase 17 — Log Dashboards and Alerting

Implement operations dashboards, alert rules, notification delivery, and trace correlation.

### Phase 18 — Retention and Archival

Implement index lifecycle, S3 archival, retrieval, deletion, and tenant policies.

### Phase 19 — Security and Reliability Hardening

Implement threat mitigations, resilience policies, resource limits, auditability, and recovery procedures.

### Phase 20 — Deployment

Implement the complete Docker Compose environment, Kubernetes/Helm, Terraform where justified, and CI/CD.

### Phase 21 — Testing and Verification

Execute backend, frontend, contract, concurrency, integration, security, and failure-recovery tests.

### Phase 22 — Performance Engineering

Benchmark order workflows, dispatch, log ingestion, indexing, search, and frontend performance.

### Phase 23 — Final Architecture Review

Evaluate all six AWS pillars, correctness, operations, privacy, data retention, scalability, and cost.

For every phase:

- Implement actual files.
- Keep the project buildable.
- Run applicable checks.
- Correct failures.
- Preserve unrelated work.
- Document trade-offs.
- Maintain requirements traceability.
- Report completed, partial, and unimplemented features accurately.

---

# 24. REQUIRED DELIVERABLES

Provide:

1. Complete source code for Java 21 Spring Boot services.
2. Complete Next.js customer, restaurant, delivery, admin, and observability applications.
3. PostgreSQL migrations and PostGIS setup.
4. Kafka event contracts and topic configuration.
5. Redis configuration where used.
6. OpenTelemetry Collector configuration.
7. OpenSearch templates, mappings, lifecycle configuration, and search integration.
8. S3-compatible archive integration where implemented.
9. Payment simulator and provider adapter.
10. Maps/driver movement simulator.
11. Seed data for restaurants, dishes, users, orders, and partners.
12. Structured log-producing demo workflows.
13. Automated backend tests.
14. Playwright frontend tests.
15. Distributed correctness tests.
16. Security tests.
17. Fault-injection tests.
18. Benchmark scripts.
19. Dockerfiles and Docker Compose.
20. Kubernetes manifests/Helm charts.
21. Terraform where justified.
22. CI/CD.
23. HLD/LLD documentation and Mermaid diagrams.
24. Well-Architected review.
25. Operational runbooks.
26. Requirements traceability matrix.
27. Architecture Decision Records.
28. Final verification report.

---

# 25. DEFINITION OF DONE

The project is complete only when:

- All required backend modules build.
- All required frontend applications build.
- Database migrations execute successfully.
- The core customer ordering workflow runs end to end.
- Restaurant operations work.
- Delivery assignment and completion work.
- Payments are idempotent under documented provider semantics.
- Duplicate callbacks are handled.
- Order state and financial records remain consistent.
- Real-time customer tracking works.
- Admin and support operations work.
- Authorization and resource ownership are enforced.
- Structured logs are emitted by the backend.
- OpenTelemetry collection works.
- Kafka transports accepted log events under the documented durability model.
- Log processing/redaction works.
- OpenSearch indexes log events.
- Search and filtering work.
- Live Tail works.
- Log-based alerting works.
- The observability console uses real backend data.
- Tenant/security boundaries are tested.
- Retention operates as designed.
- The local environment can demonstrate the complete business workflow and its logs.
- Tests cover important concurrency and failure cases.
- Benchmarks are reproducible.
- All six AWS Well-Architected pillars are reviewed.
- Unimplemented advanced requirements are explicitly identified.
- Known limitations and operational risks are documented.

Do not claim production readiness based solely on a working happy-path demonstration.

---

# 26. FINAL EXECUTION INSTRUCTIONS

**Your task is to design and develop the complete enterprise food delivery platform and its integrated distributed log aggregation system.**

Use Java 21 and Spring Boot for the backend, Next.js and TypeScript for the frontend, PostgreSQL/PostGIS for authoritative business data, Kafka for justified asynchronous workflows, Redis for caching and coordination where appropriate, OpenTelemetry for instrumentation, and OpenSearch for centralized log indexing.

Deliver a premium Zomato/Swiggy-inspired customer experience using original visual assets and branding.

Build functional restaurant, delivery, administrator, and observability applications.

Prioritize:

1. End-to-end business functionality
2. Financial and order correctness
3. Distributed transaction recovery
4. Delivery assignment and tracking
5. Security and privacy
6. Reliable log ingestion and search
7. Operational visibility
8. High-quality responsive UI/UX
9. Maintainable architecture
10. Automated verification
11. Performance and scalability
12. All six AWS Well-Architected pillars

Do not stop after producing architecture diagrams, project skeletons, database entities, or UI mockups.

Use the available development environment to implement source code, execute tests, run infrastructure, investigate failures, and verify integrated functionality.

At completion provide a concise engineering report covering:

- Architecture implemented
- Business capabilities implemented
- Observability capabilities implemented
- Frontend routes and workflows
- Significant design decisions
- Requirements coverage
- Tests actually executed
- Measured performance where available
- Build and deployment commands
- Known limitations
- Security and reliability risks
- Next recommended improvements

**The final system must demonstrate:**

**Customer discovers food → customizes a meal → places an idempotent order → pays → restaurant accepts and prepares → delivery partner is assigned → pickup occurs → customer tracks delivery → delivery completes → payment and settlement records reconcile → correlated logs are collected → Kafka buffers logs → OpenSearch indexes logs → Next.js Log Explorer supports investigation and alerting.**

**Deliver the complete working end-to-end platform, not just the design.**
````
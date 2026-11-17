# Version assessment

Official https://docs.spring.io/spring-boot/system-requirements.html reported Spring Boot 4.1.2 compatible with Java 21 on 2026-11-17. Implementation was upgraded from cached 4.0.9 to 4.1.2; Maven package and live acceptance checks passed with 4.1.2. Java runtime 21.0.13.0.3, Maven 3.10.0. Next.js 16.4.6 / React 19.3.1 locally available; official Next installation docs require Node >=20.9. Node here: 26.13.1. Infrastructure tags pinned in compose but not digest-pinned; no production compatibility certification.

Payment HTTP contract pinned to Stripe API `2026-10-28.endive` after consulting official Checkout/refund/webhook/idempotency documentation. No Stripe SDK added. Local provider uses Python 3.12 standard library and SQLite in its own Docker volume. Local shape/semantics verified separately; external Stripe account interoperability is unverified.

Notification channels add Spring Boot managed mail dependencies, web-push 5.1.2, Bouncy Castle bcprov-jdk18on 1.86 (legacy jdk15on excluded), and jose4j 0.9.7. Versions were checked against the upstream library/Bouncy Castle release and Maven Central metadata; Java 21 build, VAPID signature/encryption preparation and STARTTLS transport tests passed. No vulnerability-scan or external-provider compatibility certification is implied.

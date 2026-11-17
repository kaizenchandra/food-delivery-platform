CREATE TABLE payments(id uuid PRIMARY KEY,order_id uuid NOT NULL UNIQUE REFERENCES orders,
 provider text NOT NULL,amount bigint NOT NULL CHECK(amount>0),currency varchar(3) NOT NULL DEFAULT 'inr',
 state text NOT NULL DEFAULT 'CREATING',session_id text UNIQUE,intent_id text UNIQUE,checkout_url text,
 captured_amount bigint NOT NULL DEFAULT 0,refunded_amount bigint NOT NULL DEFAULT 0,
 cancel_requested boolean NOT NULL DEFAULT false,attempts int NOT NULL DEFAULT 0,
 lease_until timestamptz NOT NULL DEFAULT '-infinity',next_attempt timestamptz NOT NULL DEFAULT now(),
 next_reconcile timestamptz NOT NULL DEFAULT now(),last_reconciled timestamptz,last_error text,
 created_at timestamptz NOT NULL DEFAULT now(),CHECK(captured_amount BETWEEN 0 AND amount),CHECK(refunded_amount BETWEEN 0 AND captured_amount));
CREATE INDEX payments_due ON payments(next_attempt,next_reconcile,lease_until);
CREATE TABLE refunds(id uuid PRIMARY KEY,payment_id uuid NOT NULL REFERENCES payments,order_id uuid NOT NULL REFERENCES orders,
 amount bigint NOT NULL CHECK(amount>0),state text NOT NULL DEFAULT 'REQUESTED',idempotency_key varchar(100) NOT NULL,
 reason varchar(200) NOT NULL,requested_by text NOT NULL,provider_id text UNIQUE,
 attempts int NOT NULL DEFAULT 0,lease_until timestamptz NOT NULL DEFAULT '-infinity',next_attempt timestamptz NOT NULL DEFAULT now(),
 last_error text,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),UNIQUE(payment_id,idempotency_key));
CREATE INDEX refunds_due ON refunds(state,next_attempt,lease_until);
CREATE INDEX refunds_order ON refunds(order_id);
CREATE TABLE payment_webhook_inbox(provider text NOT NULL,event_id varchar(200) NOT NULL,event_type varchar(100) NOT NULL,
 payload_hash varchar(64) NOT NULL,status text NOT NULL,payment_id uuid,received_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(provider,event_id));
CREATE TABLE payment_issues(id uuid PRIMARY KEY,payment_id uuid NOT NULL REFERENCES payments,code text NOT NULL,resource text NOT NULL,
 state text NOT NULL DEFAULT 'OPEN',seen_count bigint NOT NULL DEFAULT 1,last_seen timestamptz NOT NULL DEFAULT now(),UNIQUE(payment_id,code,resource));
CREATE TABLE payment_reconciliations(id bigserial PRIMARY KEY,payment_id uuid NOT NULL REFERENCES payments,
 result text NOT NULL,provider_state text NOT NULL,local_capture bigint NOT NULL,local_refunded bigint NOT NULL,checked_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX payment_reconciliation_latest ON payment_reconciliations(payment_id,checked_at DESC);

CREATE TABLE support_tickets(
 id uuid PRIMARY KEY,order_id uuid NOT NULL UNIQUE REFERENCES orders,customer_subject text NOT NULL,
 category text NOT NULL CHECK(category IN ('MISSING','DAMAGED','INCORRECT','OTHER')),
 requested_refund bigint NOT NULL CHECK(requested_refund>=0),status text NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED','REFUND_APPROVED')),
 refund_id uuid UNIQUE REFERENCES refunds,version bigint NOT NULL DEFAULT 0,idempotency_key varchar(100) NOT NULL,fingerprint varchar(64) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),UNIQUE(customer_subject,idempotency_key));
CREATE INDEX support_customer ON support_tickets(customer_subject,created_at DESC);
CREATE INDEX support_queue ON support_tickets(status,updated_at);
CREATE TABLE support_messages(id uuid PRIMARY KEY,ticket_id uuid NOT NULL REFERENCES support_tickets,author_role text NOT NULL CHECK(author_role IN ('CUSTOMER','ADMIN')),body varchar(3000) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX support_thread ON support_messages(ticket_id,created_at,id);
CREATE TABLE order_reviews(id uuid PRIMARY KEY,order_id uuid NOT NULL UNIQUE REFERENCES orders,restaurant_id bigint NOT NULL REFERENCES restaurants,customer_subject text NOT NULL,
 rating int NOT NULL CHECK(rating BETWEEN 1 AND 5),body varchar(2000) NOT NULL,state text NOT NULL DEFAULT 'PUBLISHED' CHECK(state IN ('PUBLISHED','HIDDEN')),version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX reviews_restaurant ON order_reviews(restaurant_id,created_at DESC) WHERE state='PUBLISHED';
CREATE TABLE review_moderation(id uuid PRIMARY KEY,review_id uuid NOT NULL REFERENCES order_reviews,actor text NOT NULL,reason varchar(500) NOT NULL,hidden boolean NOT NULL,created_at timestamptz NOT NULL DEFAULT now());

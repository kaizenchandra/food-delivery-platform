CREATE TABLE customer_addresses(id uuid PRIMARY KEY, customer_subject text NOT NULL, version bigint NOT NULL DEFAULT 0,
 label varchar(50) NOT NULL, recipient varchar(100) NOT NULL, line1 varchar(200) NOT NULL, line2 varchar(200) NOT NULL,
 city varchar(80) NOT NULL, postal_code varchar(12) NOT NULL, location geography(Point,4326) NOT NULL,
 deleted_at timestamptz, updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX addresses_customer ON customer_addresses(customer_subject) WHERE deleted_at IS NULL;
CREATE TABLE customer_carts(customer_subject text PRIMARY KEY,version bigint NOT NULL DEFAULT 0,payload text NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE coupons(code varchar(30) PRIMARY KEY,discount_bps int NOT NULL CHECK(discount_bps BETWEEN 1 AND 10000),
 max_discount bigint NOT NULL CHECK(max_discount>0),minimum_subtotal bigint NOT NULL DEFAULT 0,
 max_redemptions int NOT NULL CHECK(max_redemptions>0),redeemed int NOT NULL DEFAULT 0 CHECK(redeemed>=0),
 starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL,enabled boolean NOT NULL DEFAULT true,CHECK(redeemed<=max_redemptions));
INSERT INTO coupons VALUES('HELLO10',1000,10000,20000,50000,0,'2026-01-01','2028-01-01',true);
CREATE TABLE checkout_quotes(id uuid PRIMARY KEY,customer_subject text NOT NULL,cart_version bigint NOT NULL,payload text NOT NULL,expires_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX quotes_customer ON checkout_quotes(customer_subject,created_at);
ALTER TABLE orders ADD checkout_quote_id uuid UNIQUE REFERENCES checkout_quotes, ADD delivery_address_snapshot jsonb,
 ADD pricing_version text, ADD item_subtotal bigint, ADD discount_amount bigint, ADD tax_amount bigint, ADD delivery_fee bigint;
CREATE TABLE order_lines(id uuid PRIMARY KEY,order_id uuid NOT NULL REFERENCES orders,item_id bigint NOT NULL REFERENCES menu,
 quantity int NOT NULL CHECK(quantity BETWEEN 1 AND 20),unit_price bigint NOT NULL CHECK(unit_price>0),amount bigint NOT NULL CHECK(amount>0),snapshot jsonb NOT NULL);
CREATE INDEX order_lines_order ON order_lines(order_id);
CREATE TABLE coupon_redemptions(coupon_code varchar(30) REFERENCES coupons,customer_subject text NOT NULL,order_id uuid NOT NULL REFERENCES orders,amount bigint NOT NULL CHECK(amount>0),PRIMARY KEY(coupon_code,customer_subject),UNIQUE(order_id));

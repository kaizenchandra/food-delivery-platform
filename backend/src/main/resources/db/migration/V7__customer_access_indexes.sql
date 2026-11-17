CREATE INDEX customer_addresses_active_owner ON customer_addresses(customer_subject,updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX orders_customer_created ON orders(customer_name,created_at DESC);
CREATE INDEX orders_restaurant_created ON orders(restaurant_id,created_at DESC);
CREATE INDEX restaurants_owner ON restaurants(owner_name);

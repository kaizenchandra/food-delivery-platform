ALTER TABLE orders RENAME COLUMN driver_lat TO latitude;
ALTER TABLE orders RENAME COLUMN driver_lon TO longitude;
ALTER TABLE order_tracking_events RENAME COLUMN snapshot TO payload;
ALTER TABLE order_tracking_events ALTER COLUMN payload TYPE text USING payload::text;
CREATE INDEX tracking_event_time ON order_tracking_events(created_at);

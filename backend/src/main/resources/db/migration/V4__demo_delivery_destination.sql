-- Local baseline uses one clearly labeled demonstration destination until saved-address checkout is implemented.
ALTER TABLE orders ADD delivery_latitude double precision NOT NULL DEFAULT 12.978,
 ADD delivery_longitude double precision NOT NULL DEFAULT 77.606;

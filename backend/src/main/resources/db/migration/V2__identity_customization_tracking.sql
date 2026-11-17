CREATE TABLE SPRING_SESSION (
 PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY, SESSION_ID CHAR(36) NOT NULL, CREATION_TIME BIGINT NOT NULL, LAST_ACCESS_TIME BIGINT NOT NULL, MAX_INACTIVE_INTERVAL INT NOT NULL, EXPIRY_TIME BIGINT NOT NULL, PRINCIPAL_NAME VARCHAR(100));
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36) NOT NULL REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE, ATTRIBUTE_NAME VARCHAR(200) NOT NULL, ATTRIBUTE_BYTES BYTEA NOT NULL, PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME));
CREATE TABLE menu_option_groups(id bigserial PRIMARY KEY,menu_id bigint NOT NULL REFERENCES menu, name text NOT NULL, min_choices int NOT NULL, max_choices int NOT NULL CHECK(max_choices>=min_choices));
CREATE TABLE menu_options(id bigserial PRIMARY KEY,group_id bigint NOT NULL REFERENCES menu_option_groups, name text NOT NULL, extra_price bigint NOT NULL CHECK(extra_price>=0), available boolean NOT NULL DEFAULT true);
INSERT INTO menu_option_groups(menu_id,name,min_choices,max_choices) SELECT id,'Portion',1,1 FROM menu;
INSERT INTO menu_options(group_id,name,extra_price) SELECT id,'Regular',0 FROM menu_option_groups;
INSERT INTO menu_options(group_id,name,extra_price) SELECT id,'Large',8000 FROM menu_option_groups;
INSERT INTO menu_option_groups(menu_id,name,min_choices,max_choices) SELECT id,'Extras',0,2 FROM menu;
INSERT INTO menu_options(group_id,name,extra_price) SELECT id,'Extra cheese',4000 FROM menu_option_groups WHERE name='Extras';
INSERT INTO menu_options(group_id,name,extra_price) SELECT id,'Mint dip',2000 FROM menu_option_groups WHERE name='Extras';
ALTER TABLE orders ADD customization jsonb NOT NULL DEFAULT '{}', ADD cooking_instructions varchar(300) NOT NULL DEFAULT '', ADD cutlery boolean NOT NULL DEFAULT false, ADD driver_lat double precision, ADD driver_lon double precision, ADD location_at timestamptz;
CREATE TABLE order_tracking_events(order_id uuid NOT NULL REFERENCES orders, version bigint NOT NULL, snapshot jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(order_id,version));
-- Stable local realm subjects; production onboarding binds restaurant ownership to verified subjects.
UPDATE restaurants SET owner_name='00000000-0000-0000-0000-000000000002' WHERE owner_name='restaurant';

ALTER TABLE support_tickets ADD COLUMN escalation_state text NOT NULL DEFAULT 'NONE' CHECK(escalation_state IN ('NONE','REQUESTED','ACKNOWLEDGED','COMPLETED')),
 ADD COLUMN escalation_reason varchar(1000), ADD COLUMN escalated_by text, ADD COLUMN escalated_at timestamptz,
 ADD COLUMN acknowledged_by text, ADD COLUMN acknowledged_at timestamptz, ADD COLUMN acknowledgement varchar(1000),
 ADD COLUMN response_due_at timestamptz NOT NULL DEFAULT now()+interval '24 hours';
UPDATE support_tickets SET response_due_at=created_at+interval '24 hours';
CREATE INDEX support_due_escalation ON support_tickets(response_due_at) WHERE status='OPEN' AND escalation_state='NONE';
CREATE TABLE notifications(id uuid PRIMARY KEY,sequence bigserial UNIQUE NOT NULL,event_key varchar(250) NOT NULL UNIQUE,
 audience text NOT NULL CHECK(audience IN ('SUBJECT','ADMIN')),subject text,type varchar(80) NOT NULL,resource_id uuid NOT NULL,
 title varchar(200) NOT NULL,href varchar(200) NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 CHECK((audience='SUBJECT' AND subject IS NOT NULL) OR (audience='ADMIN' AND subject IS NULL)));
CREATE INDEX notifications_subject ON notifications(subject,sequence DESC) WHERE audience='SUBJECT';
CREATE INDEX notifications_admin ON notifications(sequence DESC) WHERE audience='ADMIN';
CREATE TABLE notification_receipts(notification_id uuid NOT NULL REFERENCES notifications,subject text NOT NULL,read_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(notification_id,subject));
CREATE TABLE review_revisions(review_id uuid NOT NULL REFERENCES order_reviews,version bigint NOT NULL,rating int NOT NULL CHECK(rating BETWEEN 1 AND 5),body varchar(2000) NOT NULL,state text NOT NULL,actor text NOT NULL,action text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(review_id,version));
INSERT INTO review_revisions(review_id,version,rating,body,state,actor,action) SELECT id,version,rating,body,state,customer_subject,'MIGRATED_SNAPSHOT' FROM order_reviews;

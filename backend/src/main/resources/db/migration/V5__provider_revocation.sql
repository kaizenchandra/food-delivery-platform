CREATE TABLE oidc_provider_sessions (
 client_session_id varchar(100) PRIMARY KEY, issuer text NOT NULL, client_id varchar(200) NOT NULL,
 subject varchar(255) NOT NULL, provider_sid varchar(255), authenticated_at timestamptz NOT NULL,
 revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX oidc_sid_lookup ON oidc_provider_sessions(issuer,client_id,provider_sid);
CREATE INDEX oidc_subject_lookup ON oidc_provider_sessions(issuer,client_id,subject);
CREATE TABLE oidc_logout_receipts (
 issuer text NOT NULL, client_id varchar(200) NOT NULL, jti varchar(200) NOT NULL,
 subject varchar(255), provider_sid varchar(255), issued_at timestamptz NOT NULL,
 received_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(issuer,client_id,jti));
CREATE INDEX oidc_logout_sid ON oidc_logout_receipts(issuer,client_id,provider_sid);
CREATE INDEX oidc_logout_subject ON oidc_logout_receipts(issuer,client_id,subject,issued_at);

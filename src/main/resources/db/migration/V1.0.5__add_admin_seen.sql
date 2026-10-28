CREATE TABLE IF NOT EXISTS admin_seen
(
    oid       VARCHAR PRIMARY KEY,
    email     VARCHAR   NOT NULL,
    name      VARCHAR,
    last_seen TIMESTAMP NOT NULL
);

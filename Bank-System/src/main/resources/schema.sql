PRAGMA foreign_keys = OFF;

CREATE TABLE IF NOT EXISTS users (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  full_name       TEXT NOT NULL,
  email           TEXT NOT NULL,
  phone           TEXT NOT NULL,
  date_of_birth   TEXT NOT NULL,
  password_hash   TEXT NOT NULL,
  role            TEXT NOT NULL DEFAULT 'CUSTOMER',
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until    TEXT,
  created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS accounts (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  account_number TEXT NOT NULL,
  user_id        INTEGER NOT NULL,
  account_type   TEXT NOT NULL,
  balance        NUMERIC NOT NULL,
  status         TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS transactions (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  reference_id         TEXT NOT NULL,
  account_id           INTEGER NOT NULL,
  type                 TEXT NOT NULL,
  amount               NUMERIC NOT NULL,
  balance_after        NUMERIC NOT NULL,
  counterparty_account TEXT,
  note                 TEXT,
  created_at           TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  admin_id    INTEGER NOT NULL,
  account_id  INTEGER NOT NULL,
  action      TEXT NOT NULL,
  reason      TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

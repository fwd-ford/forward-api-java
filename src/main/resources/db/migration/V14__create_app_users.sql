-- Migration: 014_create_app_users
-- Application users that authenticate against forward-api itself (the hosted
-- Supabase Auth project was deleted, so the API now issues its own JWTs).
-- Passwords are stored ONLY as BCrypt hashes; the CHECK constraint below makes
-- it impossible to persist a plaintext password by accident.
-- Usuarios da aplicacao (login proprio da API). Senha somente como hash BCrypt.

CREATE TABLE IF NOT EXISTS app_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    full_name       TEXT NOT NULL,
    role            TEXT NOT NULL,
    dealer_id       UUID REFERENCES dealers(id) ON DELETE RESTRICT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT app_users_role_allowed CHECK (role IN ('ATENDENTE', 'GESTOR', 'ADMIN')),
    CONSTRAINT app_users_dealer_required CHECK (role = 'ADMIN' OR dealer_id IS NOT NULL),
    CONSTRAINT app_users_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT app_users_full_name_not_blank CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT app_users_password_is_bcrypt CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')
);

-- Case-insensitive uniqueness without depending on citext.
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_email_lower ON app_users (lower(email));
CREATE INDEX IF NOT EXISTS idx_app_users_dealer ON app_users (dealer_id);
CREATE INDEX IF NOT EXISTS idx_app_users_role ON app_users (role);

DROP TRIGGER IF EXISTS trg_app_users_updated_at ON app_users;
CREATE TRIGGER trg_app_users_updated_at
    BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE app_users IS 'Users that log in to forward-api (ATENDENTE, GESTOR, ADMIN). Passwords stored as BCrypt only.';
COMMENT ON COLUMN app_users.role IS 'ATENDENTE and GESTOR are scoped to dealer_id; ADMIN sees every dealer.';
COMMENT ON COLUMN app_users.password_hash IS 'BCrypt hash ($2a/$2b/$2y). Plaintext is rejected by app_users_password_is_bcrypt.';

-- Migration: 016_app_users_token_version
-- Session revocation for the API's own JWTs. Every token carries the user's token_version
-- (claim "token_version"); the API compares it with this column on each request, so
-- incrementing it revokes every token issued before. The API increments it when an admin
-- changes the user's role, active flag, dealer or password. Idempotent, safe on existing data.
-- Revogacao de sessoes: incrementar token_version invalida os JWTs emitidos antes.

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_token_version_non_negative;
ALTER TABLE app_users
    ADD CONSTRAINT app_users_token_version_non_negative CHECK (token_version >= 0);

COMMENT ON COLUMN app_users.token_version IS
    'Incremented to revoke every JWT issued before (role, active, dealer or password change).';

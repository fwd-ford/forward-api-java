-- Extra demo/test data (profiles demo and test only, Flyway location db/seed), applied after
-- db/bootstrap (repeatable migrations run in description order: "bootstrap" < "seed").
-- Adds users that exist only in local demos and automated tests:
--   gestor2@forward.dev  GESTOR     F0002
--   inativo@forward.dev  ATENDENTE  F0001, inactive (login answers 401 AUTH_USER_DISABLED)
-- Password: placeholder demo_users_password (Forward@2026 in demo/test; bcrypt via pgcrypto).
-- Idempotent: existing users are never updated.
-- Dados extras de demo/teste (usuarios gestor2 e inativo); nunca roda em producao.

SET LOCAL search_path = public, extensions;

INSERT INTO app_users (id, email, password_hash, full_name, role, dealer_id, active)
SELECT v.id::uuid, v.email, crypt(p.pw, gen_salt('bf', 10)), v.full_name, v.role, d.id, v.active
FROM (VALUES
    ('ad000000-0000-4000-8000-000000000005', 'gestor2@forward.dev', 'Helena Costa', 'GESTOR',    'F0002', TRUE),
    ('ad000000-0000-4000-8000-000000000006', 'inativo@forward.dev', 'Igor Lima',    'ATENDENTE', 'F0001', FALSE)
) AS v(id, email, full_name, role, dealer_code, active)
JOIN dealers d ON d.code = v.dealer_code
CROSS JOIN (SELECT '${demo_users_password}'::text AS pw) p
WHERE p.pw <> ''
  AND NOT EXISTS (SELECT 1 FROM app_users u WHERE lower(u.email) = lower(v.email))
ON CONFLICT DO NOTHING;

-- Demo / test seed. Loaded by Flyway ONLY when the "demo", "test" or "seed" profile
-- adds classpath:db/seed to spring.flyway.locations. Never runs in production.
-- Synthetic data: Brazilian dealer names, fake CNPJs/CPFs, plausible VINs.
-- Idempotent (ON CONFLICT DO NOTHING) so it is safe to re-apply.
--
-- Demo users (password for all: Forward@2026, stored as BCrypt hashes only):
--   admin@forward.dev       ADMIN      (no dealer, sees everything)
--   gestor@forward.dev      GESTOR     dealer F0001
--   atendente@forward.dev   ATENDENTE  dealer F0001
--   atendente2@forward.dev  ATENDENTE  dealer F0002
--   gestor2@forward.dev     GESTOR     dealer F0002
--   inativo@forward.dev     ATENDENTE  dealer F0001 (inactive, login answers 401)
--
-- Seed de demo/teste. Carregado pelo Flyway apenas nos perfis demo, test ou seed.

-- ---------------------------------------------------------------------------
-- Dealers (fixed ids so tests and docs can reference them)
-- ---------------------------------------------------------------------------
INSERT INTO dealers (id, code, name, cnpj, city, state, region, phone, email, active) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'F0001', 'Ford Morumbi São Paulo',    '12345678000101', 'São Paulo',      'SP', 'Sudeste',      '1133330001', 'morumbi@dealer.forward.dev',   TRUE),
    ('d0000000-0000-4000-8000-000000000002', 'F0002', 'Ford Barra Rio',            '12345678000202', 'Rio de Janeiro', 'RJ', 'Sudeste',      '2133330002', 'barra@dealer.forward.dev',     TRUE),
    ('d0000000-0000-4000-8000-000000000003', 'F0003', 'Ford Savassi BH',           '12345678000303', 'Belo Horizonte', 'MG', 'Sudeste',      '3133330003', 'savassi@dealer.forward.dev',   TRUE),
    ('d0000000-0000-4000-8000-000000000004', 'F0004', 'Ford Batel Curitiba',       '12345678000404', 'Curitiba',       'PR', 'Sul',          '4133330004', 'batel@dealer.forward.dev',     TRUE),
    ('d0000000-0000-4000-8000-000000000005', 'F0005', 'Ford Moinhos Porto Alegre', '12345678000505', 'Porto Alegre',   'RS', 'Sul',          '5133330005', 'moinhos@dealer.forward.dev',   TRUE),
    ('d0000000-0000-4000-8000-000000000006', 'F0006', 'Ford Boa Viagem Recife',    '12345678000606', 'Recife',         'PE', 'Nordeste',     '8133330006', 'boaviagem@dealer.forward.dev', TRUE),
    ('d0000000-0000-4000-8000-000000000007', 'F0007', 'Ford Iguatemi Fortaleza',   '12345678000707', 'Fortaleza',      'CE', 'Nordeste',     '8533330007', 'iguatemi@dealer.forward.dev',  TRUE),
    ('d0000000-0000-4000-8000-000000000008', 'F0008', 'Ford Umarizal Belém',       '12345678000808', 'Belém',          'PA', 'Norte',        '9133330008', 'umarizal@dealer.forward.dev',  TRUE),
    ('d0000000-0000-4000-8000-000000000009', 'F0009', 'Ford Asa Sul Brasília',     '12345678000909', 'Brasília',       'DF', 'Centro-Oeste', '6133330009', 'asasul@dealer.forward.dev',    TRUE),
    ('d0000000-0000-4000-8000-000000000010', 'F0010', 'Ford Campinas',             '12345678001010', 'Campinas',       'SP', 'Sudeste',      '1933330010', 'campinas@dealer.forward.dev',  TRUE)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Customers (12)
-- ---------------------------------------------------------------------------
INSERT INTO customers (id, full_name, cpf, birth_date, email, phone, city, state, opt_in_whatsapp, opt_in_email, lgpd_consent_at) VALUES
    ('11111111-1111-1111-1111-111111111001', 'João da Silva',     '11122233301', '1985-03-14', 'joao@example.com',     '+5511999990001', 'São Paulo',      'SP', TRUE,  TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111002', 'Maria Oliveira',    '11122233302', '1990-07-22', 'maria@example.com',    '+5511999990002', 'São Paulo',      'SP', TRUE,  FALSE, NOW()),
    ('11111111-1111-1111-1111-111111111003', 'Carlos Souza',      '11122233303', '1978-11-05', 'carlos@example.com',   '+5521999990003', 'Rio de Janeiro', 'RJ', FALSE, TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111004', 'Fernanda Alves',    '11122233304', '1995-02-19', 'fernanda@example.com', '+5531999990004', 'Belo Horizonte', 'MG', TRUE,  TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111005', 'Rafael Pereira',    '11122233305', '1982-09-30', 'rafael@example.com',   '+5541999990005', 'Curitiba',       'PR', TRUE,  FALSE, NOW()),
    ('11111111-1111-1111-1111-111111111006', 'Patrícia Gomes',    '11122233306', '1988-01-09', 'patricia@example.com', '+5511999990006', 'São Paulo',      'SP', TRUE,  TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111007', 'Lucas Martins',     '11122233307', '1993-06-27', 'lucas@example.com',    '+5519999990007', 'Campinas',       'SP', TRUE,  FALSE, NOW()),
    ('11111111-1111-1111-1111-111111111008', 'Juliana Rocha',     '11122233308', '1987-12-03', 'juliana@example.com',  '+5521999990008', 'Niterói',        'RJ', TRUE,  TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111009', 'Bruno Fernandes',   '11122233309', '1979-04-15', 'bruno@example.com',    '+5521999990009', 'Rio de Janeiro', 'RJ', FALSE, FALSE, NOW()),
    ('11111111-1111-1111-1111-111111111010', 'Camila Ribeiro',    '11122233310', '1996-08-21', 'camila@example.com',   '+5531999990010', 'Contagem',       'MG', TRUE,  TRUE,  NOW()),
    ('11111111-1111-1111-1111-111111111011', 'Thiago Almeida',    '11122233311', '1984-10-11', 'thiago@example.com',   '+5511999990011', 'São Paulo',      'SP', TRUE,  FALSE, NOW()),
    ('11111111-1111-1111-1111-111111111012', 'Larissa Barbosa',   '11122233312', '1991-05-30', 'larissa@example.com',  '+5541999990012', 'Curitiba',       'PR', FALSE, TRUE,  NOW())
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Vehicles (one per customer)
-- ---------------------------------------------------------------------------
INSERT INTO vehicles (vin, customer_id, current_dealer_id, model, year, version, color, license_plate, discontinued, purchase_date, last_service_at) VALUES
    ('9BFZZZ5SZJB000001', '11111111-1111-1111-1111-111111111001', 'd0000000-0000-4000-8000-000000000001', 'Ka',           2018, 'SE 1.0',          'Prata',  'FWD1A01', TRUE,  '2018-05-10', '2022-06-10 14:30:00-03'),
    ('9BFZZZ5SZJB000002', '11111111-1111-1111-1111-111111111002', 'd0000000-0000-4000-8000-000000000001', 'EcoSport',     2019, 'Titanium',        'Branco', 'FWD1A02', TRUE,  '2019-08-20', '2023-09-15 10:00:00-03'),
    ('9BFZZZ5SZJB000003', '11111111-1111-1111-1111-111111111003', 'd0000000-0000-4000-8000-000000000002', 'Fiesta',       2017, 'Sedan',           'Preto',  'FWD1A03', TRUE,  '2017-11-11', NULL),
    ('9BFZZZ5SZJB000004', '11111111-1111-1111-1111-111111111004', 'd0000000-0000-4000-8000-000000000003', 'Ranger',       2023, 'Limited',         'Azul',   'FWD1A04', FALSE, '2023-03-01', '2024-09-12 11:45:00-03'),
    ('9BFZZZ5SZJB000005', '11111111-1111-1111-1111-111111111005', 'd0000000-0000-4000-8000-000000000004', 'Territory',    2022, 'Titanium',        'Cinza',  'FWD1A05', FALSE, '2022-12-15', '2024-12-20 09:30:00-03'),
    ('9BFZZZ5SZJB000006', '11111111-1111-1111-1111-111111111006', 'd0000000-0000-4000-8000-000000000001', 'Ranger',       2021, 'XLT 3.2',         'Branco', 'FWD1A06', FALSE, '2021-04-18', '2023-02-11 08:20:00-03'),
    ('9BFZZZ5SZJB000007', '11111111-1111-1111-1111-111111111007', 'd0000000-0000-4000-8000-000000000001', 'Maverick',     2023, 'Lariat Hybrid',   'Azul',   'FWD1A07', FALSE, '2023-07-02', '2025-01-09 15:10:00-03'),
    ('9BFZZZ5SZJB000008', '11111111-1111-1111-1111-111111111008', 'd0000000-0000-4000-8000-000000000002', 'Territory',    2023, 'SEL',             'Vermelho','FWD1A08', FALSE, '2023-01-25', '2024-07-30 13:00:00-03'),
    ('9BFZZZ5SZJB000009', '11111111-1111-1111-1111-111111111009', 'd0000000-0000-4000-8000-000000000002', 'Ka',           2019, 'Sedan SE 1.5',    'Prata',  'FWD1A09', TRUE,  '2019-03-03', '2021-10-05 10:40:00-03'),
    ('9BFZZZ5SZJB000010', '11111111-1111-1111-1111-111111111010', 'd0000000-0000-4000-8000-000000000003', 'EcoSport',     2020, 'Freestyle 1.5',   'Cinza',  'FWD1A10', TRUE,  '2020-09-14', '2023-05-22 09:00:00-03'),
    ('9BFZZZ5SZJB000011', '11111111-1111-1111-1111-111111111011', 'd0000000-0000-4000-8000-000000000001', 'Bronco Sport', 2022, 'Wildtrak',        'Verde',  'FWD1A11', FALSE, '2022-06-06', '2025-03-18 11:30:00-03'),
    ('9BFZZZ5SZJB000012', '11111111-1111-1111-1111-111111111012', 'd0000000-0000-4000-8000-000000000004', 'Fiesta',       2016, 'Hatch SE 1.6',    'Preto',  'FWD1A12', TRUE,  '2016-02-12', '2020-11-27 16:15:00-03')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Service orders (history)
-- ---------------------------------------------------------------------------
INSERT INTO service_orders (id, vin, dealer_id, order_type, status, scheduled_at, completed_at, mileage_km, total_amount_brl, maintenance_number, main_source) VALUES
    ('5e000000-0000-4000-8000-000000000001', '9BFZZZ5SZJB000001', 'd0000000-0000-4000-8000-000000000001', 'scheduled_maintenance', 'completed', '2022-06-10 14:00:00-03', '2022-06-10 14:30:00-03', 45000,  850.00, 4, 'legacy'),
    ('5e000000-0000-4000-8000-000000000002', '9BFZZZ5SZJB000002', 'd0000000-0000-4000-8000-000000000001', 'scheduled_maintenance', 'completed', '2023-09-15 09:30:00-03', '2023-09-15 10:00:00-03', 38000,  920.00, 3, 'legacy'),
    ('5e000000-0000-4000-8000-000000000003', '9BFZZZ5SZJB000004', 'd0000000-0000-4000-8000-000000000003', 'scheduled_maintenance', 'completed', '2024-09-12 11:15:00-03', '2024-09-12 11:45:00-03', 18000, 1250.00, 2, 'dealer_app'),
    ('5e000000-0000-4000-8000-000000000004', '9BFZZZ5SZJB000005', 'd0000000-0000-4000-8000-000000000004', 'scheduled_maintenance', 'completed', '2024-12-20 09:00:00-03', '2024-12-20 09:30:00-03', 22000, 1100.00, 2, 'dealer_app'),
    ('5e000000-0000-4000-8000-000000000005', '9BFZZZ5SZJB000006', 'd0000000-0000-4000-8000-000000000001', 'recall',                'completed', '2023-02-11 08:00:00-03', '2023-02-11 08:20:00-03', 31000,    0.00, 0, 'manual'),
    ('5e000000-0000-4000-8000-000000000006', '9BFZZZ5SZJB000007', 'd0000000-0000-4000-8000-000000000001', 'scheduled_maintenance', 'completed', '2025-01-09 14:30:00-03', '2025-01-09 15:10:00-03', 12000,  990.00, 1, 'dealer_app'),
    ('5e000000-0000-4000-8000-000000000007', '9BFZZZ5SZJB000008', 'd0000000-0000-4000-8000-000000000002', 'warranty_repair',       'completed', '2024-07-30 12:00:00-03', '2024-07-30 13:00:00-03', 26000,    0.00, 0, 'n8n'),
    ('5e000000-0000-4000-8000-000000000008', '9BFZZZ5SZJB000011', 'd0000000-0000-4000-8000-000000000001', 'scheduled_maintenance', 'scheduled', '2026-10-15 09:00:00-03', NULL,                     40000, NULL,   3, 'dealer_app'),
    ('5e000000-0000-4000-8000-000000000009', '9BFZZZ5SZJB000009', 'd0000000-0000-4000-8000-000000000002', 'paid_repair',           'completed', '2021-10-05 10:00:00-03', '2021-10-05 10:40:00-03', 52000, 1780.00, 0, 'legacy')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Churn scores (one current score per customer)
-- ---------------------------------------------------------------------------
INSERT INTO churn_scores (id, customer_id, vin, model_version, segment, churn_probability, confidence, is_current) VALUES
    ('5c000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111001', '9BFZZZ5SZJB000001', 'v1.0', 'esquecido', 0.78, 0.82, TRUE),
    ('5c000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111002', '9BFZZZ5SZJB000002', 'v1.0', 'fiel',      0.15, 0.91, TRUE),
    ('5c000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111003', '9BFZZZ5SZJB000003', 'v1.0', 'abandono',  0.95, 0.88, TRUE),
    ('5c000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111004', '9BFZZZ5SZJB000004', 'v1.0', 'fiel',      0.12, 0.94, TRUE),
    ('5c000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111005', '9BFZZZ5SZJB000005', 'v1.0', 'economico', 0.55, 0.79, TRUE),
    ('5c000000-0000-4000-8000-000000000006', '11111111-1111-1111-1111-111111111006', '9BFZZZ5SZJB000006', 'v1.0', 'abandono',  0.88, 0.85, TRUE),
    ('5c000000-0000-4000-8000-000000000007', '11111111-1111-1111-1111-111111111007', '9BFZZZ5SZJB000007', 'v1.0', 'economico', 0.47, 0.76, TRUE),
    ('5c000000-0000-4000-8000-000000000008', '11111111-1111-1111-1111-111111111008', '9BFZZZ5SZJB000008', 'v1.0', 'esquecido', 0.72, 0.81, TRUE),
    ('5c000000-0000-4000-8000-000000000009', '11111111-1111-1111-1111-111111111009', '9BFZZZ5SZJB000009', 'v1.0', 'abandono',  0.91, 0.87, TRUE),
    ('5c000000-0000-4000-8000-000000000010', '11111111-1111-1111-1111-111111111010', '9BFZZZ5SZJB000010', 'v1.0', 'esquecido', 0.66, 0.80, TRUE),
    ('5c000000-0000-4000-8000-000000000011', '11111111-1111-1111-1111-111111111011', '9BFZZZ5SZJB000011', 'v1.0', 'fiel',      0.22, 0.90, TRUE),
    ('5c000000-0000-4000-8000-000000000012', '11111111-1111-1111-1111-111111111012', '9BFZZZ5SZJB000012', 'v1.0', 'abandono',  0.83, 0.84, TRUE)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Leads (18) across 4 dealers, every status and priority represented.
--   F0001: 8 leads, F0002: 5 leads, F0003: 3 leads, F0004: 2 leads
-- ---------------------------------------------------------------------------
INSERT INTO leads (id, customer_id, vin, dealer_id, score_id, priority, status, reason, expected_value_brl, assigned_at, converted_at, expires_at, notes, created_at) VALUES
    ('a1000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111001', '9BFZZZ5SZJB000001', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000001', 'high',     'new',       'Revisão atrasada há 18 meses (score 0.78).',            1200.00, NULL,                    NULL,                    NOW() + INTERVAL '30 days', NULL,                                      NOW() - INTERVAL '2 days'),
    ('a1000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111006', '9BFZZZ5SZJB000006', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000006', 'critical', 'new',       'Cliente sem retorno à rede desde 2023 (score 0.88).',  2500.00, NULL,                    NULL,                    NOW() + INTERVAL '30 days', NULL,                                      NOW() - INTERVAL '1 day'),
    ('a1000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111007', '9BFZZZ5SZJB000007', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000007', 'medium',   'assigned',  'Plano de revisão econômica (score 0.47).',              800.00, NOW() - INTERVAL '3 days', NULL,                    NOW() + INTERVAL '20 days', NULL,                                      NOW() - INTERVAL '5 days'),
    ('a1000000-0000-4000-8000-000000000004', '11111111-1111-1111-1111-111111111011', '9BFZZZ5SZJB000011', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000011', 'low',      'contacted', 'Cliente fiel: oferta de pacote de revisão.',            650.00, NOW() - INTERVAL '8 days', NULL,                    NOW() + INTERVAL '15 days', 'Cliente pediu retorno na próxima semana.', NOW() - INTERVAL '10 days'),
    ('a1000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111002', '9BFZZZ5SZJB000002', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000002', 'low',      'converted', 'Revisão de 40 mil km.',                                  920.00, NOW() - INTERVAL '20 days', NOW() - INTERVAL '12 days', NULL,                  'Revisão agendada e realizada.',          NOW() - INTERVAL '25 days'),
    ('a1000000-0000-4000-8000-000000000006', '11111111-1111-1111-1111-111111111001', '9BFZZZ5SZJB000001', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000001', 'medium',   'lost',      'Oferta de troca de veículo.',                           NULL,    NOW() - INTERVAL '40 days', NULL,                   NULL,                        'Cliente vendeu o veículo.',              NOW() - INTERVAL '45 days'),
    ('a1000000-0000-4000-8000-000000000007', '11111111-1111-1111-1111-111111111006', '9BFZZZ5SZJB000006', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000006', 'high',     'contacted', 'Recall pendente de atualização de software.',          1800.00, NOW() - INTERVAL '4 days', NULL,                    NOW() + INTERVAL '10 days', NULL,                                      NOW() - INTERVAL '6 days'),
    ('a1000000-0000-4000-8000-000000000008', '11111111-1111-1111-1111-111111111007', '9BFZZZ5SZJB000007', 'd0000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000007', 'medium',   'expired',   'Lead expirado sem contato.',                             700.00, NULL,                    NULL,                    NOW() - INTERVAL '1 day',   NULL,                                      NOW() - INTERVAL '60 days'),
    ('a1000000-0000-4000-8000-000000000009', '11111111-1111-1111-1111-111111111003', '9BFZZZ5SZJB000003', 'd0000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000003', 'critical', 'new',       'Score 0.95: cliente em abandono.',                     2200.00, NULL,                    NULL,                    NOW() + INTERVAL '30 days', NULL,                                      NOW() - INTERVAL '3 hours'),
    ('a1000000-0000-4000-8000-000000000010', '11111111-1111-1111-1111-111111111008', '9BFZZZ5SZJB000008', 'd0000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000008', 'high',     'assigned',  'Revisão de 30 mil km pendente.',                       1500.00, NOW() - INTERVAL '2 days', NULL,                    NOW() + INTERVAL '25 days', NULL,                                      NOW() - INTERVAL '4 days'),
    ('a1000000-0000-4000-8000-000000000011', '11111111-1111-1111-1111-111111111009', '9BFZZZ5SZJB000009', 'd0000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000009', 'critical', 'contacted', 'Oferta de troca por Territory.',                       3500.00, NOW() - INTERVAL '6 days', NULL,                    NOW() + INTERVAL '12 days', 'Cliente interessado, aguardando proposta.', NOW() - INTERVAL '7 days'),
    ('a1000000-0000-4000-8000-000000000012', '11111111-1111-1111-1111-111111111003', '9BFZZZ5SZJB000003', 'd0000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000003', 'medium',   'lost',      'Revisão com desconto.',                                 NULL,    NOW() - INTERVAL '50 days', NULL,                   NULL,                        'Sem interesse.',                         NOW() - INTERVAL '55 days'),
    ('a1000000-0000-4000-8000-000000000013', '11111111-1111-1111-1111-111111111008', '9BFZZZ5SZJB000008', 'd0000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000008', 'high',     'converted', 'Revisão de garantia.',                                 1450.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '28 days', NULL,                  'Revisão realizada.',                     NOW() - INTERVAL '33 days'),
    ('a1000000-0000-4000-8000-000000000014', '11111111-1111-1111-1111-111111111010', '9BFZZZ5SZJB000010', 'd0000000-0000-4000-8000-000000000003', '5c000000-0000-4000-8000-000000000010', 'high',     'new',       'Revisão atrasada (score 0.66).',                       1100.00, NULL,                    NULL,                    NOW() + INTERVAL '30 days', NULL,                                      NOW() - INTERVAL '12 hours'),
    ('a1000000-0000-4000-8000-000000000015', '11111111-1111-1111-1111-111111111004', '9BFZZZ5SZJB000004', 'd0000000-0000-4000-8000-000000000003', '5c000000-0000-4000-8000-000000000004', 'low',      'assigned',  'Pacote de revisão Ranger.',                             900.00, NOW() - INTERVAL '1 day',  NULL,                    NOW() + INTERVAL '29 days', NULL,                                      NOW() - INTERVAL '2 days'),
    ('a1000000-0000-4000-8000-000000000016', '11111111-1111-1111-1111-111111111010', '9BFZZZ5SZJB000010', 'd0000000-0000-4000-8000-000000000003', '5c000000-0000-4000-8000-000000000010', 'medium',   'contacted', 'Aguardando retorno do cliente.',                        950.00, NOW() - INTERVAL '5 days', NULL,                    NOW() + INTERVAL '9 days',  NULL,                                      NOW() - INTERVAL '9 days'),
    ('a1000000-0000-4000-8000-000000000017', '11111111-1111-1111-1111-111111111012', '9BFZZZ5SZJB000012', 'd0000000-0000-4000-8000-000000000004', '5c000000-0000-4000-8000-000000000012', 'critical', 'new',       'Score 0.83: risco alto de evasão.',                    1300.00, NULL,                    NULL,                    NOW() + INTERVAL '30 days', NULL,                                      NOW() - INTERVAL '6 hours'),
    ('a1000000-0000-4000-8000-000000000018', '11111111-1111-1111-1111-111111111005', '9BFZZZ5SZJB000005', 'd0000000-0000-4000-8000-000000000004', '5c000000-0000-4000-8000-000000000005', 'medium',   'assigned',  'Plano econômico de manutenção.',                        780.00, NOW() - INTERVAL '2 days', NULL,                    NOW() + INTERVAL '28 days', NULL,                                      NOW() - INTERVAL '3 days')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Application users (BCrypt hashes of the demo password, cost 10)
-- ---------------------------------------------------------------------------
INSERT INTO app_users (id, email, password_hash, full_name, role, dealer_id, active) VALUES
    ('ad000000-0000-4000-8000-000000000001', 'admin@forward.dev',      '$2a$10$k.d.GfGseMnF8e2ymwWjlOR30S0sRcpCzzavvIudR95Emidwmbc4a', 'Ana Paula Ribeiro', 'ADMIN',     NULL,                                   TRUE),
    ('ad000000-0000-4000-8000-000000000002', 'gestor@forward.dev',     '$2a$10$xw1xyBw83zSaj9jhjOM9mOKn0WPrktioCkTCwcOuIW1mP8..XPbwu', 'Gustavo Mendes',    'GESTOR',    'd0000000-0000-4000-8000-000000000001', TRUE),
    ('ad000000-0000-4000-8000-000000000003', 'atendente@forward.dev',  '$2a$10$dMxgLZHu4Jd3dzcbuVRh8eZv/P0Z20.6jd/DXcScrjwF5VXVPKcVC', 'Beatriz Santos',    'ATENDENTE', 'd0000000-0000-4000-8000-000000000001', TRUE),
    ('ad000000-0000-4000-8000-000000000004', 'atendente2@forward.dev', '$2a$10$sUAIK4EhE/GxQbzlLjcxZO32NwsE0r9nUMLTlam9vJ9BMtjLJu5I2', 'Diego Carvalho',    'ATENDENTE', 'd0000000-0000-4000-8000-000000000002', TRUE),
    ('ad000000-0000-4000-8000-000000000005', 'gestor2@forward.dev',    '$2a$10$6wQWiBLU4wUmZ8F0QtpuneuK25qX7qk7nd936yY0Afs5.O7kzOOzC', 'Helena Costa',      'GESTOR',    'd0000000-0000-4000-8000-000000000002', TRUE),
    ('ad000000-0000-4000-8000-000000000006', 'inativo@forward.dev',    '$2a$10$JnE8/itDAdcPvp2WgM5.euyeqbEuP6F0aUkMs2WGbyqwkuWOBY/ai', 'Igor Lima',         'ATENDENTE', 'd0000000-0000-4000-8000-000000000001', FALSE)
ON CONFLICT DO NOTHING;

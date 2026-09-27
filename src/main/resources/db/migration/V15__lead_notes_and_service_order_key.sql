-- Migration: 015_lead_notes_and_service_order_key
-- 1) Free-text notes on leads, written by dealer staff through PATCH /api/v1/leads/{id}.
--    Status changes are recorded in audit_log (action lead.updated), so no extra
--    history table is needed.
-- 2) Natural key for service orders so the same event cannot be registered twice
--    (POST /api/v1/service-events answers 409 on duplicates).
-- Notas nos leads e chave natural para eventos de servico (evita duplicidade).

ALTER TABLE leads ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE leads DROP CONSTRAINT IF EXISTS leads_notes_length;
ALTER TABLE leads
    ADD CONSTRAINT leads_notes_length CHECK (notes IS NULL OR char_length(notes) <= 2000);

-- Existing databases may already hold duplicated orders; in that case the index is skipped
-- (the API still rejects new duplicates with 409) instead of failing the deploy.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM service_orders
         WHERE scheduled_at IS NOT NULL
         GROUP BY vin, dealer_id, order_type, scheduled_at
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_service_orders_natural_key
            ON service_orders (vin, dealer_id, order_type, scheduled_at);
    ELSE
        RAISE NOTICE 'uq_service_orders_natural_key skipped: duplicated service orders exist';
    END IF;
END $$;

COMMENT ON COLUMN leads.notes IS 'Follow-up notes written by dealer staff (max 2000 chars).';

-- V17 : aligne la nullabilite de trois colonnes sur les entites JPA (nullable = false).
-- Chaque colonne est comblee uniquement avec une valeur deja definie par le schema ou derivee d'une
-- donnee existante ; aucune valeur metier n'est inventee. Les cas ambigus font echouer la migration.

-- invoice_lines.metadata : la valeur par defaut declaree par V7 est '{}' (comme quote_lines.metadata).
UPDATE invoice_lines SET metadata = '{}'::jsonb WHERE metadata IS NULL;
ALTER TABLE invoice_lines ALTER COLUMN metadata SET NOT NULL;

-- payments.refunded_amount : valeur par defaut declaree par V6 = 0. Un paiement marque rembourse
-- sans montant est ambigu : il doit etre corrige manuellement avant la migration.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM payments WHERE refunded_amount IS NULL AND refunded_at IS NOT NULL) THEN
        RAISE EXCEPTION 'V17: des paiements ont refunded_at renseigne sans refunded_amount ; renseigner le montant avant de migrer';
    END IF;
END;
$$;
UPDATE payments SET refunded_amount = 0 WHERE refunded_amount IS NULL;
ALTER TABLE payments ALTER COLUMN refunded_amount SET NOT NULL;

-- quotes.vehicle_id : QuoteEntity la derive toujours du vehicule de la reservation liee.
UPDATE quotes q SET vehicle_id = b.vehicle_id FROM bookings b WHERE b.id = q.booking_id AND q.vehicle_id IS NULL;
ALTER TABLE quotes ALTER COLUMN vehicle_id SET NOT NULL;

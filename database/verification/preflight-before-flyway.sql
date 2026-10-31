-- Contrôle de pré-migration Elite Tunisia (LECTURE SEULE).
--
-- À exécuter AVANT de pointer l'application (Flyway) vers une base existante :
--   psql -h <hote> -p <port> -U <utilisateur> -d <base> -f preflight-before-flyway.sql
--
-- Aucune donnée n'est modifiée : la transaction est en lecture seule et annulée.
-- Chaque constat est émis sous la forme  NOTICE:  PREFLIGHT|<GRAVITE>|<CODE>|<detail>
--   BLOCKER    : la migration indiquée échouera avec les données/objets présents.
--   DATA_RISK  : la migration réussit mais des données deviennent invisibles de l'application.
--   INFO       : information sans effet bloquant.
-- Aucun constat BLOCKER/DATA_RISK ne signifie que les migrations en attente sont compatibles ;
-- ce contrôle ne couvre que les risques identifiés dans docs/postgres-stabilization-report.md.

BEGIN READ ONLY;

DO $preflight$
DECLARE
    applied int := 0;
    n bigint;
    user_tables bigint;
    has_history boolean := to_regclass('public.flyway_schema_history') IS NOT NULL;
    rec record;
BEGIN
    SELECT count(*) INTO user_tables FROM pg_tables
        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history';

    IF has_history THEN
        EXECUTE 'SELECT coalesce(max(version::int), 0) FROM public.flyway_schema_history WHERE success' INTO applied;
    ELSIF user_tables > 0 THEN
        RAISE NOTICE 'PREFLIGHT|BLOCKER|FLYWAY_NO_HISTORY|% tables sans flyway_schema_history (Flyway refusera ou rejouera V1)', user_tables;
    END IF;

    -- V2 : colonnes NOT NULL sans valeur par défaut ajoutées à employees ; noms clients rendus obligatoires.
    IF applied < 2 AND to_regclass('public.employees') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM public.employees' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V2_EMPLOYEES_NOT_NULL|% employe(s) : first_name/last_name/phone NOT NULL sans defaut', n;
        END IF;
    END IF;
    IF applied < 2 AND to_regclass('public.customers') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM public.customers WHERE first_name IS NULL OR last_name IS NULL OR phone IS NULL' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V2_CUSTOMER_NULLS|% client(s) avec prenom/nom/telephone NULL', n;
        END IF;
    END IF;

    -- V3 : price_per_week et currency NOT NULL sans defaut ; contraintes CHECK renforcees.
    IF applied < 3 AND to_regclass('public.vehicles') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM public.vehicles' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V3_VEHICLES_NOT_NULL|% vehicule(s) : price_per_week/currency NOT NULL sans defaut', n;
        END IF;
        EXECUTE 'SELECT count(*) FROM public.vehicles WHERE price_per_day <= 0' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V3_PRICE_NOT_POSITIVE|% vehicule(s) avec price_per_day <= 0', n;
        END IF;
        EXECUTE 'SELECT count(*) FROM public.vehicles WHERE model_year NOT BETWEEN 1886 AND 2100' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V3_YEAR_OUT_OF_RANGE|% vehicule(s) hors 1886-2100', n;
        END IF;
        EXECUTE $q$SELECT count(*) FROM public.vehicles
            WHERE status NOT IN ('AVAILABLE','RESERVED','RENTED','MAINTENANCE','UNAVAILABLE')
               OR transmission NOT IN ('AUTOMATIC','MANUAL','SEMI_AUTOMATIC')
               OR fuel NOT IN ('PETROL','DIESEL','HYBRID','ELECTRIC','PLUG_IN_HYBRID')$q$ INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V3_ENUM_VALUES|% vehicule(s) avec status/transmission/fuel hors liste', n;
        END IF;
    END IF;

    -- V4 : extension btree_gist, statuts autorises, exclusion anti-chevauchement des reservations bloquantes.
    IF applied < 4 THEN
        SELECT count(*) INTO n FROM pg_available_extensions WHERE name = 'btree_gist';
        IF n = 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V4_BTREE_GIST_UNAVAILABLE|extension btree_gist non installee sur le serveur';
        END IF;
        IF to_regclass('public.bookings') IS NOT NULL THEN
            EXECUTE $q$SELECT count(*) FROM public.bookings
                WHERE status NOT IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','IN_PROGRESS','COMPLETED')$q$ INTO n;
            IF n > 0 THEN
                RAISE NOTICE 'PREFLIGHT|BLOCKER|V4_STATUS_INVALID|% reservation(s) avec un statut hors liste', n;
            END IF;
            EXECUTE $q$SELECT count(*) FROM public.bookings a JOIN public.bookings b
                ON a.vehicle_id = b.vehicle_id AND a.id < b.id
               AND a.status IN ('CONFIRMED','IN_PROGRESS') AND b.status IN ('CONFIRMED','IN_PROGRESS')
               AND tstzrange(a.starts_at, a.ends_at, '[)') && tstzrange(b.starts_at, b.ends_at, '[)')$q$ INTO n;
            IF n > 0 THEN
                RAISE NOTICE 'PREFLIGHT|BLOCKER|V4_OVERLAPPING_BLOCKING_BOOKINGS|% paire(s) de reservations CONFIRMED/IN_PROGRESS qui se chevauchent', n;
            END IF;
        END IF;
    END IF;

    -- V5 : la migration refuse explicitement toute base contenant des devis historiques.
    IF applied < 5 AND to_regclass('public.quotes') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM public.quotes' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V5_QUOTES_PRESENT|% devis historique(s) : V5 leve volontairement une exception', n;
        END IF;
    END IF;

    -- V6 / V7 : renommage vers legacy_* sans copie de donnees.
    IF applied < 6 THEN
        IF to_regclass('public.legacy_payments') IS NOT NULL THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V6_LEGACY_TABLE_EXISTS|legacy_payments existe deja';
        END IF;
        IF to_regclass('public.payments') IS NOT NULL THEN
            EXECUTE 'SELECT count(*) FROM public.payments' INTO n;
            IF n > 0 THEN
                RAISE NOTICE 'PREFLIGHT|DATA_RISK|V6_PAYMENTS_STRANDED|% paiement(s) resteront dans legacy_payments, invisibles de l''application', n;
            END IF;
        END IF;
    END IF;
    IF applied < 7 THEN
        IF to_regclass('public.legacy_invoices') IS NOT NULL THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V7_LEGACY_TABLE_EXISTS|legacy_invoices existe deja';
        END IF;
        IF to_regclass('public.invoices') IS NOT NULL THEN
            EXECUTE 'SELECT count(*) FROM public.invoices' INTO n;
            IF n > 0 THEN
                RAISE NOTICE 'PREFLIGHT|DATA_RISK|V7_INVOICES_STRANDED|% facture(s) resteront dans legacy_invoices, invisibles de l''application', n;
            END IF;
        END IF;
    END IF;

    -- Permissions inserees sans ON CONFLICT : echec si la cle existe deja avant la migration concernee.
    IF to_regclass('public.permissions') IS NOT NULL THEN
        FOR rec IN
            SELECT * FROM (VALUES
                (2,'admin:access'),(2,'users:read'),(2,'users:manage'),(2,'profile:read:self'),(2,'profile:update:self'),
                (3,'vehicles:read'),(3,'vehicles:manage'),(3,'brands:read'),(3,'brands:manage'),(3,'categories:read'),(3,'categories:manage'),
                (4,'bookings:read'),(4,'bookings:create'),(4,'bookings:manage'),(4,'bookings:cancel'),
                (5,'quotes:read'),(5,'quotes:create'),(5,'quotes:manage'),(5,'quotes:send'),(5,'quotes:accept'),(5,'quotes:reject'),
                (10,'payments:create'),(13,'invoices:read'),(14,'drivers:manage'),(15,'customers:read')
            ) AS t(v, k)
        LOOP
            IF applied < rec.v THEN
                EXECUTE 'SELECT count(*) FROM public.permissions WHERE permission_key = $1' INTO n USING rec.k;
                IF n > 0 THEN
                    RAISE NOTICE 'PREFLIGHT|BLOCKER|PERMISSION_ALREADY_EXISTS|%@V%', rec.k, rec.v;
                END IF;
            END IF;
        END LOOP;
    END IF;

    -- V16 : CREATE TABLE transfers (non idempotent, deja appliquee ailleurs : ne pas la modifier).
    -- Une table creee auparavant par Hibernate (ddl-auto=update) la fait echouer.
    IF applied < 16 AND to_regclass('public.transfers') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM public.transfers' INTO n;
        RAISE NOTICE 'PREFLIGHT|BLOCKER|V16_TRANSFERS_PRE_EXISTING|% ligne(s) : V16 echouera (relation deja existante) ; sauvegarder, renommer la table puis recopier apres migration', n;
        EXECUTE $q$SELECT count(*) FROM public.transfers t
            WHERE (t.driver_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.drivers d WHERE d.id = t.driver_id))
               OR (t.vehicle_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.vehicles v WHERE v.id = t.vehicle_id))$q$ INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V16_TRANSFER_ORPHAN_REFERENCES|% transfert(s) referencent un chauffeur/vehicule inexistant (la recopie echouerait aussi)', n;
        END IF;
    END IF;

    -- V19 : contraintes de statut et de passagers ajoutees NOT VALID (aucun echec), mais l'application lit le statut
    -- comme une enumeration : une ligne a statut libre ne pourra plus etre chargee par l'API.
    IF applied < 19 AND to_regclass('public.transfers') IS NOT NULL THEN
        EXECUTE $q$SELECT count(*) FROM public.transfers
            WHERE status IS NULL OR status NOT IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED')$q$ INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|DATA_RISK|V19_TRANSFER_STATUS_UNKNOWN|% transfert(s) avec un statut hors liste : conserves mais illisibles par l''API tant qu''ils ne sont pas normalises', n;
        END IF;
    END IF;

    -- V20 : permissions inserees avec ON CONFLICT DO NOTHING : aucun risque d'echec, aucun controle necessaire.

    -- V17 : alignement de nullabilite avec les entites JPA.
    IF applied < 17 AND to_regclass('public.payments') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'payments' AND column_name = 'refunded_amount') THEN
        EXECUTE 'SELECT count(*) FROM public.payments WHERE refunded_amount IS NULL AND refunded_at IS NOT NULL' INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'PREFLIGHT|BLOCKER|V17_REFUND_AMOUNT_AMBIGUOUS|% paiement(s) rembourses sans montant : a renseigner manuellement', n;
        END IF;
    END IF;

    RAISE NOTICE 'PREFLIGHT|INFO|DONE|derniere migration Flyway reussie = %', applied;
END
$preflight$;

ROLLBACK;

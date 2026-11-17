# Rapport final de validation MVP — Elite Tunisia (lots 1 à 8)

Statut : **backend validé jusqu'au lot 6 inclus ; frontend non vérifiable dans l'environnement ; aucun commit, push ni déploiement.**

## 1. Environnement de validation

- Branche `main`, HEAD `78da39b`. Aucun commit effectué.
- JDK 21 utilisé via variables de processus uniquement (`JAVA_HOME` global non modifié, JDK 8).
- PostgreSQL : uniquement le cluster isolé `127.0.0.1:15432`, bases temporaires `et_a4_it_*` créées et supprimées par les tests.
- Interdits respectés : port 5432, `et_test_db`, `et_dev_db`, `scripts/test-backend-postgres.ps1` (jamais exécuté).
- Node.js : 18.20.8 / npm 10.9.4 (non modifiés). Le frontend exige `^22.22.3 || ^24.15.0 || >=26` (`frontend/package.json`, champ `engines`).

## 2. Résultats par lot

| Lot | Contenu | Statut | Preuves réelles |
|---|---|---|---|
| 1 | A4 : tests PostgreSQL automatisés (V1 à V22) | Terminé | `PaymentQuotePostgresA4Test` : 6 exécutés, 0 échec |
| 2 | Partner : V23, API, sécurité, historique | Terminé (backend) | `PartnerSecurityMockMvcTest` 13, `PartnerAssignmentHistoryTest` 3, `PartnerMigrationPostgresTest` 8, `PartnerServiceTest` |
| 3 | Catalogue : V24 ARCHIVED, API publique, cycle de vie | Terminé (backend) | `CataloguePublicMockMvcTest` 7, tests de cycle de vie 14 |
| 4 | Garanties et transferts : V25, triggers | Terminé (backend) | 68 tests ciblés verts ; suite 267 exécutés à la fin du lot |
| 5 | Holds et disponibilité : V26, expiration, concurrence PG | Terminé (backend) | `BookingHoldPostgresTest` 15/15 sur 15432 ; suite 285 exécutés |
| 6 | Paiement simulé intégral, confirmation gardée | Terminé (backend) | `PaymentBookingPostgresTest` 4/4 sur 15432 ; `CustomerPaymentControllerMockMvcTest` 5/5 ; suite 295 exécutés |
| 7 | Frontend Angular (partenaires, catalogue, paiement) | **Non vérifié** | Node 18.20.8 incompatible ; aucun build, aucun test, aucune installation |
| 8 | Rapport final et harmonisation documentaire | Ce document | Doc 04 harmonisée avec V23 à V26 |

### Suite backend complète (dernière exécution, lot 6)

Commande : `mvnw.cmd -B clean test` depuis `backend/`, JDK 21, variables `ELITE_A4_PG_*` pointant sur 127.0.0.1:15432, `ELITE_POSTGRES_TEST` absent.

- **295 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0.**
- Totaux Surefire XML concordants avec le résumé Maven (log `lot6-full-suite2.log`).
- Les 10 ignorés sont tous conditionnés à `ELITE_POSTGRES_TEST` (BookingEngineTests 2, InvoicePostgresIntegrationTests 2, PaymentPostgresIntegrationTests 5, QuoteEngineTests 1). Ils ne constituent pas une preuve PostgreSQL, et leur base cible `et_test_db` est interdite.

## 3. Migrations

| Version | Fichier | Rôle |
|---|---|---|
| V1 à V22 | `database/migrations/` | Socle existant ; V21 et V22 concernent les paiements |
| V23 | `V23__partner_foundations.sql` | Partenaires INDIVIDUAL/COMPANY, `vehicles.partner_id`, historique des affectations |
| V24 | `V24__vehicle_archived_status.sql` | Statut ARCHIVED |
| V25 | `V25__vehicle_guarantees.sql` | Garanties, triggers de chevauchement, immutabilité, durée max 90 jours |
| V26 | `V26__booking_holds.sql` | Holds PENDING persistants, `guard_bookings_availability` |

Aucune migration déjà appliquée n'a été modifiée. Le lot 6 n'ajoute aucune migration.

## 4. Points de sécurité et d'intégrité vérifiés

- Confirmation d'une réservation impossible sans paiement CAPTURED pour le dernier devis accepté (`PAYMENT_REQUIRED`).
- Paiement refusé sur une réservation non PENDING ou dont le hold est échu (`BOOKING_NOT_PAYABLE`, `BOOKING_HOLD_EXPIRED`).
- Un seul CAPTURED par devis (index partiel V21, SQLSTATE 23505, prouvé en concurrence réelle sur deux connexions).
- Suppression d'un devis ayant un paiement refusée (FK RESTRICT V22, SQLSTATE 23001).
- Devis d'un autre client : 404 (`QUOTE_NOT_FOUND`), testé en MockMvc.
- Expiration de hold idempotente et sans effet sur une réservation payée ou confirmée.
- Isolation partenaire et rôle PARTNER lecture seule : testées en MockMvc (lot 2).

## 5. Limites et risques restants

### Blocages confirmés
- **Frontend non vérifiable** : Node 18.20.8 ne peut pas exécuter Angular 22 (exige ≥ 22.22.3). Aucun build, aucun test unitaire, aucun E2E n'a été exécuté. Le lot 7 n'est donc pas validé. Non modifié sans autorisation.
- **Tests PostgreSQL gardés par `ELITE_POSTGRES_TEST`** : non exécutés (base `et_test_db` interdite). `BookingLockOrderPostgresIT` et `BookingAuditAtomicityPostgresIT` ne sont donc pas vérifiés pour le comportement du lot 6 (ils compilent).

### Tests PostgreSQL probablement obsolètes (non exécutés, non corrigés)
- `BookingEngineTests#concurrentConfirmationsAllowOnlyOneBlockingBookingOnPostgres`
- `QuoteEngineTests#acceptedQuotesStillAllowOnlyOneConcurrentConfirmationOnPostgres`
Une seconde création sur les mêmes dates entre en conflit avec le hold actif.

### Risques métier et techniques
- **Pas de remboursement ni pénalité** (D-PA-09). Une annulation d'une réservation payée laisse le paiement CAPTURED pour réconciliation manuelle.
- **Réservation payée dont le début est passé** : non expirée automatiquement, confirmation refusée par `holdDue`. Traitement manuel requis.
- **Paiement sur devis non récent** : non refusé au paiement ; la confirmation échoue proprement.
- **Inversion d'ordre de verrouillage** : `PaymentService` verrouille devis puis réservation ; `docs/architecture/booking-lock-order.md` prévoit Véhicule, Réservation, Devis. Aucun cycle identifié, mais la doc et le code doivent être réconciliés.
- **Facturation non déclenchée** : `InvoiceService.createFromQuote` n'a aucun appelant de production. Décision propriétaire requise.
- **Mapping trompeur** : `ApiExceptionHandler` mappe tout SQLSTATE 23P01 vers `VEHICLE_NOT_AVAILABLE`, y compris un chevauchement de garantie ou de hold. Risque de libellé, pas d'intégrité.
- **Planificateur non testé** : `BookingHoldExpirationWorker` (`@Scheduled`) : la logique d'expiration est testée, pas le déclenchement.
- **Simulation** : `SimulatedPaymentProvider` ne représente aucun prestataire réel. Un PROVIDER_ERROR laisse le paiement PENDING sans relance.
- **Statut `RESERVED`** : jamais écrit ; à déprécier ou documenter.
- **Documents obsolètes** : `business-decision-analysis.md` (D10) et `partner-vehicle-availability-analysis.md` (D5) listent la confirmation après paiement comme ouverte, alors que D-PA-10 la fixe.
- **Écart de comptage** : 211 tests annoncés au lot 2 contre 236 au lot 3, partiellement réconcilié. Le total vérifié actuel est 295.
- **`backend/src/main/resources/db/migration`** : copie trompeuse, exclue du build.
- **Mise à jour Mission 8** : le texte « code métier partenaires toujours non implémenté » de `18-mvp-tracking.md` section 7 est antérieur aux lots 2 à 6 et n'a pas été réécrit ici.

### Données et environnement
- **Base `et_a4_it_20261104`** présente sur 127.0.0.1:15432 : origine inconnue, ne correspond pas au format des bases créées par les tests. **Non supprimée.** Décision propriétaire.
- **Logs à la racine du dépôt** (`lot*.log`, `full-lot2.log`, etc.) : non suivis, conservés. À supprimer sur accord.
- Sauvegarde et restauration d'une base : non exécutées (procédure à documenter, aucune base réelle touchée).
- Copie représentative de données historiques : non disponible. Les migrations ont été testées sur base vide uniquement.

## 6. Décisions propriétaire à arbitrer
1. Facturation déclenchée ou non par paiement et confirmation.
2. Remboursement et pénalités (actuellement hors MVP).
3. Traitement des réservations annulées ou refusées après paiement.
4. Traitement des réservations payées dont le début est passé.
5. Validation des valeurs commerciales (tarifs, acompte, solde).
6. Suppression du doublon `database/proposed/` et des logs racine.
7. Sort de la base `et_a4_it_20261104`.
8. Passage à Node ≥ 22.22.3 pour débloquer le frontend (non fait sans autorisation).

## 7. Avancement réel du MVP

Estimation : **environ 60 %**.

Justification :
- Backend : lots 1 à 6 validés par tests réels, soit six lots sur huit du périmètre backend.
- Frontend : aucun écran partenaire, catalogue ou paiement vérifié. C'est le lot 7 complet, non validé.
- Lot 8 : ce rapport et l'harmonisation de la doc 04 sont faits, mais la validation de bout en bout (E2E, parcours utilisateur réel) ne l'est pas.
- Ce pourcentage est une estimation, pas une mesure. Il ne vaut pas garantie d'absence de bug.

## 8. Commandes de validation

Depuis `backend/`, JDK 21, variables `ELITE_A4_PG_*` pointant sur 127.0.0.1:15432 (mot de passe lu depuis `%LOCALAPPDATA%\EliteTunisiaTools\pg-test-cluster\pgpass.local`, jamais affiché) :

```
.\mvnw.cmd -B clean test
.\mvnw.cmd -B -Dtest=PaymentBookingPostgresTest test
.\mvnw.cmd -B -Dtest=CustomerPaymentControllerMockMvcTest test
.\mvnw.cmd -B -Dtest=BookingHoldPostgresTest test
```

Preuve attendue : rapports `target/surefire-reports/TEST-*.xml` avec `tests`, `failures`, `errors`, `skipped`. Un exit 0 avec `tests="0"` ou des ignorés n'est pas une preuve.

Frontend (à exécuter uniquement après passage à Node ≥ 22.22.3, sur autorisation) : `npm ci`, `npm run build`, `npm test`, `npm run e2e`.

## 9. Preuves nécessaires pour déclarer chaque étape réussie

- Compilation : `BUILD SUCCESS` et absence d'erreur `ERROR` dans le log Maven.
- Tests : totaux Surefire exacts, zéro échec, zéro erreur, ignorés expliqués.
- PostgreSQL : résultats sur 127.0.0.1:15432 uniquement, base `et_a4_it_*` supprimée après test.
- Migrations : chaîne V1 à V26 appliquée sur base vide, aucune migration appliquée modifiée.
- Frontend : sortie de `npm run build` et de `npm test` sur Node ≥ 22.22.3.
- Git : `git status --short` avant et après, aucun commit, push ni déploiement.

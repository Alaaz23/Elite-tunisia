# Stabilisation backend avant frontend — rapport

> **Verdict** : le backend est **stabilisé pour le périmètre testé** et peut passer au développement frontend. Il n'est **pas déclaré prêt pour la préproduction** : les tests passent, mais plusieurs points ne sont pas exécutés (voir section 4 et 5).
>
> Convention : `CONFIRMÉ` = démontré par code lu, test exécuté ou log. `RISQUE` = plausible, non démontré. `NON VÉRIFIÉ` = impossible à vérifier dans l'environnement actuel.

## 0. Protection et état Git

- Branche `main`, HEAD `78da39b`. Aucun commit, push, déploiement, `git reset`, `git clean` ni checkout destructif.
- `git status --short` : 112 entrées (95 signalées au départ, plus les fichiers modifiés et créés par cette mission). Aucun fichier ni log supprimé.
- Base de test : uniquement le cluster isolé `127.0.0.1:15432`, utilisateur `et_test`. Port 5432, `et_test_db` et `et_dev_db` non touchés.
- Bases temporaires : chaque JVM de test crée `et_a4_it_<12 hex>` et la supprime à l'arrêt. Aucune base `et_a4_it_<12 hex>` issue des tests de cette mission ne subsiste.
- Bases restantes sur 15432, non supprimées (origine à confirmer par le propriétaire) : `et_a4_it_20261104`, `et_a4_flyway_v1_22`, `et_restore_check`.

## 1. Risques confirmés et corrections

| # | Risque confirmé | Preuve avant correction | Correction | Statut |
|---|---|---|---|---|
| 1 | Les tests PostgreSQL étaient **silencieusement ignorés** : le profil Surefire effaçait `ELITE_POSTGRES_TEST` et `DATABASE_*`. | Run initial : 107 exécutés, 7 échecs, 13 erreurs ; tests PG absents des résultats. | `backend/pom.xml` : profil `postgres-test` transmet les variables à Surefire et Failsafe. `PostgresTestHelper` : base temporaire par JVM, migrée par Flyway, supprimée au shutdown. | Corrigé et vérifié |
| 2 | `*IT` non exécutés par `mvn clean test`. | Surefire seul : 300 tests ; les `*IT` relèvent de Failsafe. | Validation finale par `verify` (Surefire + Failsafe). | Documenté |
| 3 | Ordre de verrouillage incohérent : `PaymentService.createForQuote` prenait Devis puis Réservation. | Code `PaymentService` avant correction ; `docs/architecture/booking-lock-order.md` affirmait Véhicule → Réservation → Devis. | `PaymentService` : Véhicule → Réservation → Devis (projection non verrouillée `QuoteRepository.findBookingIdForCustomer`). Doc corrigée. | Corrigé |
| 4 | Chemins Transfer et Driver : Réservation → Transfert → Chauffeur, sans verrou véhicule. | `TransferService`, `DriverAssignmentService` (lecture statique). | Aucune correction. Aucun cycle démontré (aucun chemin ne prend Réservation avant Véhicule). Limite documentée. | Risque résiduel, décision propriétaire |
| 5 | Tests de concurrence `BookingEngineTests` et `QuoteEngineTests` obsolètes (une seconde création sur les mêmes dates ne devait pas réussir, et la confirmation sans paiement réussissait). | Tests gardés par `ELITE_POSTGRES_TEST`, non exécutés. | Réécrits selon les règles actuelles : un gagnant, paiement intégral avant confirmation (helper `TestFixtures.payInFull`), idempotence de confirmation après paiement. | Corrigé et exécuté |
| 6 | `ApiExceptionHandler` mappait tout SQLSTATE 23P01 en `VEHICLE_NOT_AVAILABLE`. | `handleConflict` avant correction. | Mapping par message et contrainte : garantie chevauchante → `GUARANTEE_OVERLAP` ; période bloquée ou `bookings_no_blocking_overlap` → `VEHICLE_NOT_AVAILABLE` ; 23514 « aucune garantie active » → `VEHICLE_NOT_AVAILABLE` (détail spécifique) ; autres cas → 409 générique sans détail SQL ni champ `code`. | Corrigé et testé (6 cas) |
| 7 | Planificateur des holds non testé de bout en bout. | Aucun test du `@Scheduled`. | `BookingHoldSchedulerPostgresIT` : déclenchement réel, expiration, réservations payées et confirmées conservées, idempotence, reprise (hold DB-only repris par le scan), hold actif non expiré, concurrence (au plus un gagnant). Horloge : rendre le hold échu par SQL, sans temporisation fragile. | Corrigé et exécuté (5/0/0/0) |
| 8 | Doc de planification avec numéros de migrations faux (T-C03/T-C04/T-D01/T-D02, benchmark §8.2, `partner-availability/11`). | Lecture de `implementation-roadmap.md`, `international-benchmark-and-target-architecture.md` et `partner-availability/11-implementation-roadmap.md`. | Notes de correspondance ajoutées et lignes corrigées avec les migrations réelles. Doc 04 déjà correcte. Aucune migration appliquée modifiée. | Corrigé (documentaire) |

## 2. Tests réellement exécutés

Environnement : JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot`, variables de processus uniquement), PostgreSQL isolé `127.0.0.1:15432`, Maven Wrapper `backend/mvnw.cmd`.

| Lot de tests | Commande | Résultat réel | Preuve |
|---|---|---|---|
| Conflits SQLSTATE (MockMvc, sans PG) | `mvnw.cmd -B test -Dtest=SqlConflictMappingMockMvcTest` | 6 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0 | `stab-sqlconflict.log` |
| Planificateur des holds (PG, failsafe) | `ELITE_POSTGRES_TEST=true ... verify -Dit.test=BookingHoldSchedulerPostgresIT` | 5 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0 | `stab-scheduler.log` |
| Unitaires paiement | `PaymentServiceUnitTest` et associés | 13 exécutés, 0 échec | `stab-unit-payment.log` |
| Familles A et C (PG) | ciblées | 30 exécutés, 0 échec | `stab-fixA-C.log` |
| Famille B et QuoteEngine (PG) | ciblées | 21 exécutés, 0 échec | `stab-pg-family2.log` |
| Suite complète sans IT (PG) | `clean test -Ppostgres-test` | 300 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0 | `stab-full-final.log` |
| **Suite complète finale** | `clean verify -Ppostgres-test` | **Surefire 300 + Failsafe 41 = 341 exécutés, 0 échec, 0 erreur, 0 ignoré, BUILD SUCCESS, exit 0** | `stab-full-verify.log` |

Détail Failsafe (41) : `BookingHoldSchedulerPostgresIT` 5, `BookingLockOrderPostgresIT` 6, `BookingAuditAtomicityPostgresIT` 4, `MigrationWithExistingDataIT` 20 (migrations V1 à V26, chaîne complète et données historiques simulées), `PostgresInvoiceConcurrencyIT` 1, `PaymentQuoteCapturePostgresIT` 1, `PostgresIdempotencyIT` 1, `LastSuperAdminConcurrencyIT` 2, `DriverAssignmentPostgresIT` 1.

Lecture des logs : les 12 lignes `ERROR` de `stab-full-verify.log` proviennent des scénarios de `MigrationWithExistingDataIT` qui échouent volontairement (migration fautive annulée par Flyway, attendue par le test). Le code de sortie et le compte des rapports Surefire et Failsafe confirment le succès.

## 3. Contrats API validés (couverts par tests)

- Erreurs de conflit (`SqlConflictMappingMockMvcTest`) : `GUARANTEE_OVERLAP`, `VEHICLE_NOT_AVAILABLE` (période bloquée, aucune garantie active), 409 générique sans détail SQL.
- Paiement client (`CustomerPaymentControllerMockMvcTest`, 5 tests, lot 6) : 404 `QUOTE_NOT_FOUND` pour un devis d'un autre client ; 409 `BOOKING_HOLD_EXPIRED`.
- Erreurs de paiement et confirmation : `PAYMENT_REQUIRED`, `BOOKING_NOT_PAYABLE`, `BOOKING_HOLD_EXPIRED`, `PAYMENT_IN_PROGRESS`, `QUOTE_ALREADY_PAID`, `QUOTE_NOT_ACCEPTED`, `QUOTE_SUPERSEDED` (409 via le mapping par défaut).
- Partenaires, garanties, affectations et catalogue public : tests MockMvc et services des lots 2 à 4 inclus dans la suite complète (341 au total, 0 échec).

Limite : aucune matrice exhaustive des endpoints n'a été produite pour cette mission. Un inventaire complet des routes et de leurs tests reste à faire avant la préproduction.

## 4. Tests non exécutés ou non réalisés

- **Pipeline CI** (`.github/workflows/ci.yml`, job `backend-postgres`) : configuration modifiée, non exécutée. Impossible localement. Le conteneur CI utilise `POSTGRES_DB=et_test_db` pour la disponibilité : à confirmer (voir section 7).
- **Verrouillage mixte dynamique** (Transfer ou Driver concurrent avec Booking ou Payment) : aucun test PostgreSQL. Seule l'analyse statique a été faite.
- **Cycle de vie du paiement sur PostgreSQL** : succès, échec, PROVIDER_ERROR, double capture, annulation après paiement, réservation payée dont le début est passé, hold étendu, confirmation refusée, historique financier. Ces points sont vérifiés par analyse statique et par les tests unitaires du lot 6, pas par un test PG de cycle de vie complet.
- **Redémarrage réel de la JVM** pendant une expiration : non simulé. La reprise est testée par un hold DB-only repris par le scan.
- **Sauvegarde puis restauration** : non exercée. `et_restore_check` existe sur 15432 mais son origine est inconnue ; non utilisée.
- **Migrations sur copie de données historiques** : non disponible. Les tests `MigrationWithExistingDataIT` utilisent des données synthétiques.
- **Frontend** : aucun build, aucun test, aucune installation (Node 18.20.8 ; Angular 22 exige `^22.22.3 || ^24.15.0 || >=26`).
- **Dépendances** : aucun audit `npm audit` ni OWASP Dependency-Check exécuté dans cette mission.

## 5. Risques restants

| Priorité | Risque | Nature |
|---|---|---|
| P0 | Décision de facturation absente : `InvoiceService.createFromQuote` n'a aucun appelant. Ni paiement ni confirmation ne produisent de facture. | Décision propriétaire (voir section 6) |
| P0 | Remboursement et pénalités absents. Une annulation ou un refus d'une réservation payée laisse le paiement CAPTURED pour réconciliation manuelle. | Décision propriétaire (D-PA-09) |
| P1 | Réservation PENDING payée dont le début est passé : hold étendu jusqu'au début, confirmation refusée, pas d'expiration automatique. Traitement manuel. | Décision propriétaire |
| P1 | PROVIDER_ERROR laisse le paiement PENDING, sans relance ni rapprochement automatique ; les nouvelles tentatives sont bloquées (`PAYMENT_IN_PROGRESS`). | Exploitation |
| P1 | Ordre de verrouillage non uniforme (Transfer et Driver sans verrou véhicule). Pas de cycle démontré, mais non garanti par construction. | Architecture |
| P1 | `RESERVED` défini mais jamais écrit (statut véhicule). | Code à vérifier |
| P1 | Sauvegarde et restauration non éprouvées ; migrations non testées sur données réelles. | Exploitation |
| P1 | CI non exécutée ; conteneur CI nommé `et_test_db` (base éphémère). | Infrastructure |
| P2 | Valeurs commerciales (tarifs, caution, solde) à valider par le propriétaire. | Métier |
| P2 | Documents historiques contradictoires (`business-decision-analysis.md` D10, `partner-vehicle-availability-analysis.md` D5) encore « ouverts » alors que D-PA-10 est appliquée. | Documentation |
| P2 | `scripts/test-backend-postgres.ps1` recrée `et_test_db` : **ne jamais l'exécuter**. `scripts/e2e-postgres.ps1` utilise `DATABASE_URL` : vérifier la cible avant usage. | Sécurité opérationnelle |
| P2 | `ProductionProfileHardeningTest` contient une chaîne `DATABASE_URL` H2 non liée. | Nettoyage |
| P2 | Avertissement Mockito (auto-attach) sous JDK 21 : cosmétique. | Cosmétique |

Point de sécurité à valider avant préproduction (non revu en détail dans cette mission) : limiteur de connexion en mémoire (non partagé entre instances), confiance aux en-têtes proxy, CSP absente, Actuator limité à `health` et `info`. Ces points viennent de l'étude benchmark et restent à vérifier.

## 6. Décisions propriétaire requises (non tranchées ici)

1. **Déclencheur de facturation** : au paiement capturé, à la confirmation, ou à la restitution. Le mécanisme reste non déclenché.
2. **Remboursement et pénalités** : hors MVP (D-PA-09). Confirmer le périmètre avant préproduction.
3. **Réservation payée dont la date de début est passée** : traitement manuel, ou règle automatique.
4. **Valeurs commerciales** : tarifs, caution, solde.
5. **Node** : passer à Node 22.22.3 ou plus (ou 24.15.0 ou plus) pour le frontend. Aucune installation effectuée sans autorisation.
6. **Ordre de verrouillage Transfer et Driver** : aligner sur Véhicule en premier, ou documenter comme acceptable.
7. **Nettoyage** : `database/proposed/`, logs racine (`lot*.log`, `stab-*.log`, `full-lot2.log`, `history-partner.log`, `pg-lot2.log`, `mockmvc-partner.log`), bases `et_a4_it_20261104`, `et_a4_flyway_v1_22`, `et_restore_check`, `scripts/test-backend-postgres.ps1`.
8. **Nom du conteneur CI** : conserver `et_test_db` ou utiliser un nom dédié.

## 7. Prérequis frontend

Constat réel (lecture seule, aucune modification d'Angular) :

- `frontend/package.json` : Angular `^22.2.0` (core, common, forms, router, platform-browser, compiler), `@angular/build` et `@angular/cli` `^22.2.1`, TypeScript `~6.0.2`, Vitest `^5.0.0`, Playwright `^1.63.0`, jsdom `^30.0.0`, rxjs `~7.8.0`. `engines.node` : `^22.22.3 || ^24.15.0 || >=26`.
- Machine : Node `v18.20.8`. **Build Angular impossible** en l'état. Aucune installation.
- Architecture : `frontend/src/app/` avec `core/` (service d'authentification, gardes, intercepteurs, jeton `API_BASE_URL`, services booking, catalogue, quote, invoice, customer-admin, driver, service-catalogue, health), `features/` (admin, auth, bookings, brands, customer, home, quotes, vehicles), `layout/` (admin, customer, public), `shared/`.
- Fichiers Angular déjà modifiés dans l'espace de travail (non revus dans cette mission) : `frontend/src/app/core/services/catalogue-api.service.ts`, `frontend/src/app/features/quotes/quote-detail.component.ts`.

Endpoints backend à consommer, sans écran ni service frontend aujourd'hui :

| Domaine | Endpoints backend | Manque côté frontend |
|---|---|---|
| Partenaires (admin) | `/api/admin/partners` | Service, liste, création, modification, activation |
| Portail partenaire (lecture seule) | `/api/partner/**` | Garde `PARTNER`, routes, écrans lecture |
| Garanties (admin) | `/api/admin/guarantees` | Service, écran de gestion |
| Affectations véhicule-partenaire | `/api/admin/vehicles/{id}/partner-assignments` | Service, historique, changement |
| Transferts (admin) | `/api/admin/transfers` | Service, écran |
| Paiement client | `/api/customer/quotes/{quoteId}/payments` (`quote-api.service.ts`, `createPayment`) | Remplacer la page placeholder « Paiements » (`app.routes.ts`) par le flux réel |
| Holds et expiration | Statut de réservation et `holdExpiresAt` côté `/api/bookings` | Affichage de l'échéance, état expiré |
| Conflits | Codes `GUARANTEE_OVERLAP`, `VEHICLE_NOT_AVAILABLE`, `BOOKING_HOLD_EXPIRED`, `PAYMENT_REQUIRED` | Messages utilisateur dédiés |

Plan d'implémentation frontend proposé (après installation d'une version Node compatible et autorisation) :

1. Installer ou activer Node ≥ 22.22.3 (décision propriétaire). Vérifier `npm ci` et `npm run build`.
2. Ajouter les services `partner-api.service.ts`, `guarantee-api.service.ts`, `transfer-api.service.ts` sur le modèle des services existants.
3. Garde `PARTNER` et routes `/partner/**`, en lecture seule, avec le même contrôle côté backend.
4. Écrans admin : partenaires, garanties, affectations, transferts.
5. Flux de paiement réel et gestion des holds dans le parcours de réservation.
6. Gestion des erreurs : mapper les codes métier listés ci-dessus.
7. Tests : unitaires (Vitest) et E2E (Playwright) sur les parcours critiques, avec le Node compatible.

## 8. Commandes de validation

Depuis `backend` avec JDK 21 (variables de processus uniquement, mot de passe lu depuis le fichier local, jamais affiché) :

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:ELITE_POSTGRES_TEST='true'
$env:ELITE_A4_PG_TEST='true'
$env:ELITE_A4_PG_URL='jdbc:postgresql://127.0.0.1:15432/postgres'
$env:ELITE_A4_PG_USER='et_test'
$env:ELITE_A4_PG_PASSWORD=(Get-Content "$env:LOCALAPPDATA\EliteTunisiaTools\pg-test-cluster\pgpass.local" -Raw).Trim()
cmd /c ".\mvnw.cmd -B -ntp -Ppostgres-test clean verify > ..\stab-full-verify.log 2>&1"
```

Preuves à lire : `backend/target/surefire-reports/*.xml` et `backend/target/failsafe-reports/*.xml` (attributs `tests`, `failures`, `errors`, `skipped`), code de sortie de `cmd`, ligne `BUILD SUCCESS`.

Ciblées :

- `mvnw.cmd -B -ntp test -Dtest=SqlConflictMappingMockMvcTest`
- `mvnw.cmd -B -ntp verify -Ppostgres-test -Dit.test=BookingHoldSchedulerPostgresIT` (avec les variables PG)
- Vérification des bases temporaires après run : aucune `et_a4_it_<12 hex>` créée par les tests ne doit rester.

Ne jamais exécuter `scripts/test-backend-postgres.ps1`.

## 9. Preuves requises pour déclarer chaque étape réussie

| Étape | Preuve obligatoire |
|---|---|
| Compilation | `BUILD SUCCESS`, exit 0, log joint |
| Tests unitaires et MockMvc | Surefire : `tests`, `failures`, `errors`, `skipped` par classe |
| Tests d'intégration et PG | Failsafe : même détail, classe `*PostgresIT` présente, `skipped=0` |
| Concurrence | Test PG avec deux connexions réelles, assertion sur le nombre de gagnants |
| Migrations | `MigrationWithExistingDataIT` vert sur V1 à V26 |
| Frontend | `npm run build` vert sur Node compatible (non réalisé) |
| Préproduction | Test de restauration d'une copie réelle, CI verte, audit de dépendances, décisions propriétaire tranchées (non réalisé) |

## 10. Conclusion

Le backend est stabilisé pour les tests et peut passer au développement frontend. La déclaration de préproduction reste conditionnée aux décisions propriétaire (section 6), à l'exécution de la CI, à un test de restauration, et à l'audit des dépendances. Aucune garantie d'absence de bug n'est donnée.

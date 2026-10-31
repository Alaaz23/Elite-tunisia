# Étape 6B — Audit de sécurité ciblé, transferts, facturation et modules

Date : 2026-10-28. Les résultats de tests cités ont tous été exécutés ; les limites sont indiquées. Rien ici n'autorise un déploiement : aucune base contenant des données réelles n'a été migrée.

## 1. Audit de sécurité ciblé

Périmètre lu : `AdminTransferController`, `TransferService`, `TransferEntity`, contrôleurs et service de facturation, contrôleurs/DTO/service de réservation, `AdminDriverAssignmentController`, `DriverAssignmentService`, `SecurityConfig`, `ApiExceptionHandler`.

| # | Constat | Gravité | Preuve | Traitement |
|---|---|---|---|---|
| 1 | `POST /api/admin/transfers` désérialisait une **entité JPA** (`@RequestBody TransferEntity`) : le client pouvait fournir `status`, `driverId`, `vehicleId` (qui avaient des mutateurs publics) | Élevée (affectation de masse) | lecture du code. Le détournement de `id` (champ sans mutateur) **n'a pas été reproduit** sur l'ancien code et n'est donc pas affirmé | DTO `TransferRequest` / `TransferUpdateRequest` / `TransferResponse` ; test `forgedIdentifierStatusAndVehicleInTheBodyAreIgnored` (valide le nouveau comportement) |
| 2 | Aucune validation (champs nuls, adresses vides, passagers négatifs ou énormes) ; statut en texte libre ; aucun lien avec une réservation (pas de `booking_id`) ; liste non bornée | Moyenne | lecture du code et du schéma V16 | migration V19, validation Bean Validation, énumération, pagination bornée (≤ 100) |
| 3 | Erreurs métier (`RuntimeException("CODE")`) renvoyées en **HTTP 500** : affectation de chauffeur (conflit, chauffeur inconnu, permis expiré…), factures, transferts | Moyenne (fiabilité, messages inexploitables par l'interface) | `ApiExceptionHandler` ne traitait que `Exception` | `DomainException` typée (404/409/400, champ `code`) ; tests |
| 4 | Un compte authentifié **sans** droit recevait une erreur de validation 400 avant le refus 403 (la validation du corps précède `@PreAuthorize`) | Faible (indice sur le contrat d'API) | échec du test `staffWithoutBookingManagementCannotCreateTransfers` | règles par URL dans `SecurityConfig` (transferts et services) ; tests dédiés |
| 5 | `POST /api/admin/bookings/{id}/driver` sans `@Valid` : `driverId` absent → 500 | Faible | lecture du code | `@NotNull` + `@Valid` |
| 6 | Facture d'un autre client : `ACCESS_DENIED` et `INVOICE_NOT_FOUND` toutes deux en 500 | Faible | lecture du code | 404 identique dans les deux cas (aucun indice sur l'existence) |
| 7 | Les contrôleurs de réservation et de facture client utilisent des DTO validés, filtrent par propriétaire (`own(...)`, `getForCustomer`), et bornent les transitions par `BookingStatus` | — | lecture + tests E2E d'isolation existants | aucun défaut démontré |

Limites de l'audit : lecture ciblée des modules listés, pas une revue exhaustive de tout le dépôt ; aucune analyse de dépendances ni test d'intrusion.

Remarque non corrigée : `GET /api/admin/drivers` et la création renvoient encore l'entité `DriverEntity` (champs `licenseNumber`, `notes`) aux détenteurs de `drivers:manage`. Pas d'accès en écriture libre (la création passe par un DTO validé), mais l'exposition directe d'entité reste à remplacer par un DTO.

## 2. Transferts sécurisés (implémenté)

Périmètre : API réservée au personnel, **sans interface Angular** et sans parcours client.

Règles appliquées côté serveur (`TransferService`, testées) :

- Création : réservation existante, en attente ou confirmée ; prise en charge comprise dans la période de la réservation ; passagers ≤ capacité du véhicule ; véhicule **toujours** celui de la réservation ; statut initial `PENDING`.
- Chauffeur : si la réservation a déjà un chauffeur affecté, il est utilisé et tout autre `driverId` est refusé (`TRANSFER_DRIVER_MISMATCH`) ; sinon le chauffeur fourni doit exister, être actif, avoir un permis valide **à la date de prise en charge**, ne pas être affecté à une autre réservation qui se chevauche, ni avoir un autre transfert actif à moins d'une heure.
- Concurrence : verrouillage pessimiste dans le même ordre que l'affectation de chauffeur (réservation, transfert, chauffeur).
- Statuts : `PENDING → CONFIRMED | CANCELLED`, `CONFIRMED → COMPLETED | CANCELLED` ; `COMPLETED` et `CANCELLED` sont terminaux ; répéter la même transition est sans effet.
- Modification : uniquement horaire, adresses et passagers, tant que le transfert n'est ni terminé ni annulé ; mêmes revalidations qu'à la création. Réservation, véhicule, chauffeur et statut ne sont jamais modifiables par ce chemin.
- Lignes antérieures sans réservation : lisibles et annulables, non modifiables (`TRANSFER_LEGACY_BOOKING_REQUIRED`).
- Audit : chaque création, modification et changement d'état est journalisé avec l'acteur.
- Permissions : `bookings:manage` **et** `admin:access` (SUPER_ADMIN, MANAGER, AGENT) ; CONCIERGE et ACCOUNTANT n'ont plus accès.

Endpoints : `POST /api/admin/transfers`, `GET /api/admin/transfers` (filtres `bookingId`, `status`, `page`, `size`), `GET /{id}`, `PUT /{id}`, `POST /{id}/confirm|complete|cancel`.

Migration **V19** (V16 non modifiée) : colonne `booking_id` (nullable, clé étrangère `RESTRICT`), `created_at`, `updated_at`, deux contraintes `CHECK` **`NOT VALID`** (statut et passagers : appliquées aux nouvelles écritures sans échouer sur d'éventuelles lignes existantes), index. Une ligne à statut libre existante est conservée mais ne peut plus être chargée par l'API : le pré-contrôle la signale (`V19_TRANSFER_STATUS_UNKNOWN`).

Tests : `TransferWorkflowTest` (16, H2 et PostgreSQL), `TransferServiceTest2` (3, migrés vers la nouvelle API sans perte de couverture), `AdminTransferControllerSecurityTest` (5), scénario de migration `v18-legacy-transfers`.

Non couvert : interface Angular, E2E des transferts, annulation automatique des transferts quand la réservation est annulée ou rejetée (ils restent actifs : voir décision D-T4).

## 3. Permissions de facturation

Opérations **réellement présentes** dans le code :

| Opération | Point d'entrée | Autorité exigée | Détenteurs actuels |
|---|---|---|---|
| Consulter ses factures (liste, détail, PDF) | `GET /api/invoices…` | rôle CUSTOMER + `invoices:read` | CUSTOMER (ses factures uniquement) |
| Consulter toutes les factures | `GET /api/admin/invoices…` | `admin:access` + `invoices:read` | SUPER_ADMIN (migration V18) |
| Créer une facture | aucune API ; créée par effet de bord : paiement simulé d'un devis accepté (`payments:create`, client) et restitution avec frais (`bookings:manage`) | — | — |
| Corriger, annuler, avoir, changer le statut | **aucune opération n'existe** | — | — |

Droits par rôle aujourd'hui : SUPER_ADMIN lit toutes les factures ; MANAGER, ACCOUNTANT, CONCIERGE, AGENT n'en lisent aucune. Constat : un agent ou un gestionnaire peut **provoquer** la création d'une facture (frais de restitution) sans pouvoir la consulter.

Aucune permission supplémentaire n'a été accordée. Décisions nécessaires :

- **D-F1 — Qui consulte toutes les factures ?**
  - A. SUPER_ADMIN seul (état actuel) : exposition minimale ; MANAGER et ACCOUNTANT ne peuvent pas contrôler la facturation.
  - B. SUPER_ADMIN + MANAGER + ACCOUNTANT : usage attendu d'un rôle « Comptabilité » ; expose noms, montants et références clients à ces rôles ; une migration `ON CONFLICT DO NOTHING` suffit.
  - C. Ajouter une autorité distincte `invoices:read:all` et réserver `invoices:read` au client : évite de confondre « mes factures » et « toutes les factures » ; demande une migration et de modifier les deux contrôleurs et leurs tests.
- **D-F2 — CONCIERGE et AGENT** : aucun besoin identifié dans le code ; l'accès resterait refusé sauf décision contraire.
- **D-F3 — Corrections** : lorsqu'un avoir, une annulation ou une régularisation sera développé, une autorité dédiée (`invoices:manage` ou `invoices:credit`) et un rôle responsable (ACCOUNTANT ? SUPER_ADMIN ?) devront être décidés ; règles fiscales tunisiennes (numérotation, mentions légales, conservation) à valider avec le comptable.
- **D-F4 — Les frais de restitution créent déjà une facture** : confirmer que cela doit rester automatique et qui peut la consulter.

## 4. Inventaire des modules

Statut : **implémenté**, **partiel** ou **non implémenté**. « Décisions » renvoie à la section 5.

| Module | Statut | Rôles | Opérations nécessaires | Règles / décisions | DTO, endpoints, droits | Tests requis |
|---|---|---|---|---|---|---|
| Clients | partiel (annuaire en lecture seule, étape précédente) | SUPER_ADMIN, MANAGER | fiche détaillée, historique des réservations, activation/désactivation du compte, niveau client | D-C1 (sens du niveau `customer_level`), D-C2 (qui désactive un client), D-C3 (conservation / suppression des données personnelles) | `GET /api/admin/customers/{id}`, `PATCH …/status` ; `customers:read` + nouvelle `customers:manage` | backend (droits, filtrage), Angular (fiche), E2E (désactivation → connexion refusée) |
| Services | **implémenté** (cette étape) | lecture : tout le personnel ; gestion : SUPER_ADMIN, MANAGER | créer, modifier tarif/nom/description, désactiver, réactiver | nom actif unique par devise ; pas de suppression ; devise immuable ; prix > 0 (3 décimales) ; modifier le prix n'affecte que les lignes ajoutées ensuite | `/api/admin/services…` ; `services:read` / `services:manage` | fait : 9 backend, 5 Angular, 2 E2E ; manquant : page publique `/services` (placeholder), D-S1 |
| Partenaires | non implémenté | SUPER_ADMIN, MANAGER (supposé) | créer, modifier, activer, consulter | D-P1 (nature : apporteurs d'affaires, propriétaires de véhicules, hôtels ?), D-P2 (assiette et paiement de la commission), D-P3 (lien avec réservations ou véhicules) | à définir après décision | backend (taux 0–100, unicité), Angular, E2E |
| Notifications | non implémenté (page client = placeholder) | clients, personnel | créer lors d'événements métier, lister, marquer lue | D-N1 (canaux : interne seul ou e-mail/SMS), D-N2 (liste des événements et destinataires), D-N3 (langues, désinscription), D-N4 (fournisseur d'envoi) | `GET /api/customer/notifications`, `POST …/{id}/read` ; émission interne depuis réservation/devis | backend (isolation par utilisateur, idempotence), Angular, E2E |
| Documents | non implémenté | clients (pièces), personnel | téléverser, consulter, supprimer | D-D1 (types de pièces, durée de conservation, chiffrement au repos, contrôle de contenu) ; données personnelles sensibles | à définir ; ne pas réutiliser le dossier public d'images | sécurité (accès propriétaire/personnel, types MIME, taille), E2E |
| Documents des chauffeurs | non implémenté | MANAGER, AGENT (`drivers:manage`) | rattacher permis, assurance, échéances | D-D1 + D-D2 (blocage d'affectation si document expiré ? ; aujourd'hui seul `license_expiry` est contrôlé) | dépend de Documents | idem + règle d'affectation |
| Demandes de service | non implémenté (pages publiques chauffeur/transfert = placeholders) | clients, AGENT | demande, traitement, conversion | D-R1 (flux : demande → devis → réservation ? lien avec transferts), D-R2 (statuts) | `POST /api/customer/service-requests`, traitement staff | backend (propriété, transitions), E2E |
| Services de réservation (`booking_services`) | non implémenté | — | — | D-B1 : les services passent déjà par les lignes de devis ; faut-il conserver cette table ? | aucune API tant que D-B1 n'est pas tranchée | — |
| Transferts | partiel (API personnel, cette étape) | SUPER_ADMIN, MANAGER, AGENT | voir section 2 | D-T1 à D-T4 | `/api/admin/transfers…` ; `bookings:manage` | fait : 24 backend ; manquant : Angular, E2E, tarification |

### Premier module livré de bout en bout : catalogue de services

Raison du choix : périmètre sans ambiguïté, règles déductibles du code existant (le moteur de devis lit déjà `services` et n'avait aucun moyen de l'alimenter) et valeur immédiate : sans lui, les services additionnels ne pouvaient pas être configurés sur une base migrée par Flyway.

Réalisé : migration **V20** (`services:read`, `services:manage`, `ON CONFLICT DO NOTHING`) ; DTO validés ; service métier avec audit ; contrôleur ; URL protégées avant validation ; page Angular `/admin/services` avec filtre, pagination, création, modification, désactivation/réactivation, états de chargement et d'erreur, lien dans le menu ; consultation seule pour les rôles sans droit de gestion (le serveur refuse de toute façon les écritures).

Droits (décision par analogie avec le catalogue de véhicules, à confirmer — **D-S2**) : lecture pour tout le personnel, gestion pour SUPER_ADMIN et MANAGER.

## 5. Décisions métier encore nécessaires

| Réf. | Question | Impact |
|---|---|---|
| D-F1 à D-F4 | Voir section 3 | droits de facturation |
| D-T1 | Un transfert doit-il avoir un chauffeur pour être confirmé ? (non exigé aujourd'hui) | cohérence opérationnelle |
| D-T2 | Durée d'un transfert pour détecter les conflits (1 h supposée, valeur héritée du code) | disponibilité des chauffeurs |
| D-T3 | Transfert possible sur une réservation `IN_PROGRESS` ? (refusé aujourd'hui, comme l'affectation de chauffeur) | parcours de location en cours |
| D-T4 | Annuler automatiquement les transferts actifs quand la réservation est annulée ou rejetée ? (non fait : ils restent actifs) | cohérence transverse ; demande de coupler les deux modules |
| D-T5 | Tarification et facturation des transferts ; parcours client | non implémentés |
| D-S1 | Page publique des services : quels services, quel contenu éditorial ? | page `/services` toujours placeholder |
| D-S2 | Confirmer la répartition des droits du catalogue de services | permissions V20 |
| D-S3 | Les services peuvent-ils avoir un tarif par jour plutôt qu'unitaire ? | calcul des devis |
| D-C1 à D-C3 | Niveau client, désactivation, conservation des données personnelles | module clients |
| D-P1 à D-P3 | Définition des partenaires et commissions | module partenaires |
| D-N1 à D-N4 | Canaux, événements, langues, fournisseur de notifications | module notifications |
| D-D1, D-D2 | Documents sensibles : conservation, chiffrement, effet sur l'affectation | modules documents |
| D-R1, D-R2 | Flux des demandes de service | modules demandes et transferts |
| D-B1 | Sort de la table `booking_services` | schéma |

## 6. Résultats réels des tests

| Vérification | Résultat |
|---|---|
| `.\mvnw.cmd -Ppostgres-test verify` (PostgreSQL 18.3 isolé, base recréée, V1→V20) | surefire **118 tests, 0 échec, 0 erreur, 0 ignoré** ; failsafe **23 tests, 0 échec** (dont 20 de `MigrationWithExistingDataIT`) ; BUILD SUCCESS |
| `.\mvnw.cmd -DskipITs test` (H2) | **118 tests, 0 échec, 0 erreur, 10 ignorés** ; BUILD SUCCESS |
| `npm test -- --watch=false` | **38 tests**, 9 fichiers, tous réussis |
| `npm run build` | réussi |
| `.\scripts\e2e-postgres.ps1` (PostgreSQL isolé, Flyway réel) | **20 scénarios réussis**, 0 échec |
| E2E sur H2 | non rejoués dans cette étape |

Nouveaux tests de cette étape : `TransferWorkflowTest` (16), `AdminTransferControllerSecurityTest` (5), `TransferServiceTest2` (3, adaptés), `ServiceCatalogWorkflowTest` (9), 2 scénarios de migration (V19, V20) et l'extension de la matrice de permissions, `service-catalogue-api.service.spec.ts` (5), `services.spec.ts` (2 E2E).

## 7. Risques résiduels

- Aucune base contenant des données réelles n'a été migrée ; les scénarios restent synthétiques.
- Les transferts n'ont ni interface, ni E2E, ni tarification, et ne suivent pas l'annulation de leur réservation.
- Les règles par URL de `SecurityConfig` et les `@PreAuthorize` doivent rester alignées ; `hasAllAuthorities` exige `admin:access` en plus de l'autorité métier.
- Le contrôle d'unicité du nom de service est applicatif (pas de contrainte en base) : deux créations strictement simultanées pourraient produire un doublon.
- `DriverEntity` est toujours exposée telle quelle par l'API chauffeurs.
- Les droits de facturation, de services et de transferts reposent sur des hypothèses listées ci-dessus, à valider.
- Seul PostgreSQL 18.3 a été testé.

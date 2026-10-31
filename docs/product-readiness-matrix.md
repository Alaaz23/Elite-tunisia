# État réel du produit — matrice de fonctionnalités et bilan de sécurité (étape 7)

Date : 2026-10-29. Niveaux de preuve : **exécuté** (test réellement lancé), **lu** (code relu, non exécuté), **non vérifié**. Statut : **complet**, **partiel**, **absent**.

Cette matrice a été établie par relevé du dépôt (contrôleurs, routes Angular, migrations, tests) avant toute correction. Elle ne couvre pas les aspects non vérifiables sans environnement de production.

## 1. Matrice des fonctionnalités

Priorité : P0 = bloque une recette préproduction ; P1 = nécessaire avant la production ; P2 = souhaitable ; P3 = ultérieur.

| Module | Fonctionnalités attendues | Backend | Frontend | Règles métier | Permissions | Tests existants | Lacunes | Risques | Priorité |
|---|---|---|---|---|---|---|---|---|---|
| Authentification | inscription, connexion, session renouvelable, déconnexion, mot de passe oublié, vérification d'e-mail | **partiel** : register, login, refresh avec rotation et détection de rejeu, logout, `/me`, CSRF ; limitation des échecs de connexion (ajoutée à cette étape) | **partiel** : connexion, inscription, connexion admin, guards ; « mot de passe oublié » = placeholder | mot de passe 12 caractères avec classes ; comptes désactivés refusés ; famille de jetons révoquée au rejeu | 6 rôles, droits par migrations | E2E auth (2), `auth.service.spec`, `auth-forms.spec`, `LoginThrottleTest` (6), `LoginThrottleHttpTest` (3), `ProductionProfileHardeningTest` (6) — exécutés | pas de réinitialisation de mot de passe, pas de vérification d'e-mail, pas de liste/révocation des sessions, limitation en mémoire (une instance) | compte client non récupérable ; contournement de la limitation par IP distribuées | **P1** |
| Catalogue véhicules | marques, catégories, véhicules, images, consultation publique, filtres | **complet (lu + E2E)** : CRUD admin, upload d'images, API publique | **complet** : catalogue, détail, éditeur de flotte, taxonomies | prix > 0, énumérations contrôlées, une image principale | `vehicles/brands/categories:read|manage` | E2E catalogue (3), `vehicle-catalogue.component.spec`, `LocalFileStorageServiceTest` | cas limites d'upload non réexaminés à cette étape ; SEO et pagination côté public non vérifiés | faible | P2 |
| Disponibilité et réservations | disponibilité, aperçu tarifaire, demande, confirmation, refus, annulation, démarrage, fin | **complet (exécuté)** : contrainte d'exclusion PostgreSQL, calcul serveur, transitions bornées, verrouillage | **complet** : formulaire, liste, détail, actions agence | demande en attente ne bloque pas ; confirmée/en cours bloque ; durées par tranches de 24 h | `bookings:read/create/manage/cancel` + rôle client | `BookingEngineTests` (8), `BookingService*` (5), E2E bookings (6) | une demande `PENDING` n'expire jamais ; fuseau configurable (`BOOKING_TIME_ZONE`, défaut `Africa/Tunis`) ; le client ne peut pas modifier ses dates | demandes dormantes | P2 |
| Devis | brouillon, versions, envoi, acceptation/refus, demande de modification, expiration | **complet (exécuté)** | **complet** | snapshots versionnés ; confirmation exige un devis accepté cohérent | `quotes:*` | `QuoteEngineTests` (10), E2E quotes (7) | taxe = montant fixe configuré (`QUOTE_DEFAULT_TAX_AMOUNT`), pas de règle fiscale | règle fiscale à valider | P1 |
| Clients | annuaire, fiche, historique, désactivation, données personnelles | **partiel** : annuaire en lecture seule ; profil client modifiable par lui-même | **partiel** : annuaire, profil | — | `customers:read` (SUPER_ADMIN, MANAGER) | `AdminCustomerControllerTest` (3), `customer-admin-api.service.spec`, E2E customers (1) | pas de fiche détaillée, de désactivation ni de gestion des données personnelles | conformité données personnelles non traitée | P1 |
| Chauffeurs | création, désactivation, modification, affectation, certification | **partiel** : création, liste, désactivation, affectation ; **aucune modification** d'un chauffeur existant | **partiel** : page admin (création/désactivation) ; affectation dans le détail de réservation | permis valide, certification optionnelle, pas de chevauchement (verrouillage) | `drivers:manage`, `bookings:manage` | 8 classes backend + `DriverAssignmentPostgresIT` ; E2E bookings | pas de mise à jour (corriger un numéro de permis impose de désactiver et recréer) ; pas de documents | données chauffeur non corrigeables | P1 |
| Transferts | planification liée à une réservation, chauffeur, statuts | **partiel** : API personnel sécurisée par DTO, règles et verrouillage | **absent** | voir `step-6b-security-and-modules.md` (D-T1 à D-T5) | `bookings:manage` + `admin:access` | `TransferWorkflowTest` (16), sécurité (5), `TransferServiceTest2` (3) | pas d'interface, d'E2E, de tarification ; pas d'annulation en cascade | transferts actifs sur réservation annulée | P2 (décisions requises) |
| Prise en charge / restitution | relevés, carburant, dommages, frais | **complet (exécuté)** | **complet** (formulaires du détail de réservation) | kilométrage de retour ≥ prise en charge ; carburant 0–100 ; frais ≥ 0 et bornés ; **règle implicite** : toute note de dommage place le véhicule en maintenance | `bookings:manage` | `BookingOperationServiceTest` (4), contrôleur (2), E2E bookings | pas de photos ni de pièces ; l'audit de ces opérations ne contient pas l'IP et ses erreurs sont ignorées (`try/catch`) ; règle « dommage ⇒ maintenance » non validée par le métier | faible | P2 (décision D-O1) |
| Facturation | facture à partir d'un devis, facture des frais, consultation client et admin, PDF | **partiel (exécuté)** : création, lecture, PDF minimal | **partiel** : liste client et liste admin avec téléchargement (testés de bout en bout à cette étape) | une facture de frais par restitution avec frais > 0 | `invoices:read` (client, SUPER_ADMIN) | `InvoiceAccessControlTest` (5, nouveau), classes `Invoice*` (unité), E2E bookings (PDF client et admin, nouveau) | PDF en texte brut anglais, identifiant client au lieu du nom, sans mentions légales ; liste admin non paginée ; pas d'avoir ni d'annulation ; droits MANAGER/ACCOUNTANT non décidés | **facture non conforme à un usage légal** | **P0 pour la production** |
| Paiements | paiement d'un devis accepté, idempotence, remboursement, suivi admin | **partiel** : paiement **simulé** unique par clé d'idempotence ; pas de remboursement | **partiel** : paiement côté client ; page admin = placeholder | idempotence par `idempotency_key` ; devis accepté requis | `payments:create` (client) | `Payment*`, `CustomerPaymentControllerMockMvcTest`, `PostgresIdempotencyIT` + E2E quotes | aucun fournisseur réel ; aucune vue admin ; aucun remboursement | pas d'encaissement réel | P1 |
| Services additionnels | catalogue administrable, utilisation dans les devis | **complet (exécuté)** | **complet** (`/admin/services`) ; page publique = placeholder | nom actif unique par devise ; pas de suppression ; devise immuable | `services:read|manage` | `ServiceCatalogWorkflowTest` (9), spec Angular (5), E2E services (2) | page publique ; tarif par jour non prévu (D-S3) | faible | P2 |
| Partenaires | gestion et commissions | **absent** (table seule) | **absent** (placeholder) | non définies (D-P1 à D-P3) | — | — | tout | — | P3 (décision requise) |
| Documents et documents des chauffeurs | pièces, échéances | **absent** (tables seules) | **absent** | non définies (D-D1, D-D2) | — | — | tout ; données sensibles | — | P3 (décision requise) |
| Demandes de service | demande client, traitement | **absent** | **absent** (routes chauffeur/transfert = placeholders) | non définies (D-R1, D-R2) | — | — | tout | — | P3 (décision requise) |
| Services de réservation (`booking_services`) | services rattachés à une réservation | **absent** | **absent** | table redondante avec les lignes de devis (D-B1) | — | — | décision de schéma | — | P3 |
| Notifications | événements, centre de notifications, envoi | **absent** | **absent** (placeholder client) | non définies (D-N1 à D-N4) | — | — | tout | clients non informés des changements de statut | P1 (décision requise) |
| Rapports | activité, chiffre d'affaires, utilisation de la flotte | **absent** | **absent** (accueil admin = placeholder) | non définies | — | — | tout | pilotage impossible | P2 |
| Utilisateurs internes | création, rôle, activation | **complet (exécuté)** pour création, modification de rôle, désactivation | **complet** | le bouton de désactivation est inactif pour son propre compte (contrôle côté serveur **non vérifié**) | `users:read|manage` | E2E auth (2) | réinitialisation de mot de passe par un administrateur non examinée | — | P2 |
| Transversal | CI, observabilité, sauvegardes, déploiement | — | — | — | — | — | **aucun pipeline CI** (pas de `.github/`) ; aucun test d'audit Maven ; aucune supervision | régressions non détectées automatiquement | P1 |

Fonctionnalités placeholders côté Angular (routes) : `chauffeur`, `airport-transfer`, `services` (public), `about`, `faq`, `contact`, `forgot-password`, `customer/notifications`, accueil admin, `admin/payments`, `admin/partners`, `admin/reports`.

## 2. Bilan de sécurité (lot B de cette étape)

| # | Constat | Gravité | Preuve | Correction | Test |
|---|---|---|---|---|---|
| 1 | **Détail et PDF de facture en HTTP 500** pour le client **et** pour l'administration : la collection `lines` est paresseuse et `open-in-view` est désactivé (`LazyInitializationException`) | Élevée (fonction cassée en production) | échec du test `InvoiceAccessControlTest` | requête avec chargement des lignes (`findWithLinesById`) | `InvoiceAccessControlTest` (détail et PDF, client et staff) ; E2E téléchargement PDF client et admin |
| 2 | PDF d'une facture inconnue (administration) en HTTP 500 | Moyenne | lecture du code | 404 typé `INVOICE_NOT_FOUND` | `InvoiceDocumentServiceTest`, `InvoiceAccessControlTest` |
| 3 | Aucune limitation des tentatives de connexion | Élevée (force brute) | recherche dans le code : aucune protection | `LoginThrottle` : 5 échecs par 15 min et par couple e-mail + IP (configurable), HTTP 429 avec `Retry-After`, remise à zéro après succès | `LoginThrottleTest` (6), `LoginThrottleHttpTest` (3) |
| 4 | Description complète de l'API (`/api-docs`, Swagger UI) publique en production | Moyenne (divulgation) | `permitAll` dans `SecurityConfig` et Springdoc activé par défaut | désactivé dans `application-prod.yml` | `ProductionProfileHardeningTest` (démarrage réel avec profil prod) |
| 5 | `DriverEntity` sérialisée directement par l'API chauffeurs | Faible | lecture du code | DTO `DriverResponse` (mêmes champs, aucun changement Angular) | `AdminDriverControllerMockMvcTest` |
| 6 | Erreurs métier de facturation (`QUOTE_NOT_FOUND`, `PAYMENT_NOT_CAPTURED`...) en 500 | Faible | lecture du code | `DomainException` typées | tests existants adaptés (`InvoiceAttachPaymentUnitTest` inchangé et réussi) |

Vérifié **sans** défaut (exécuté en profil prod) : cookies `Secure`, `HttpOnly` (jeton de renouvellement) et `SameSite=Strict` constatés sur les **en-têtes réseau réels** ; seule l'actuator `health` est exposée ; les écritures sans poignée de main CSRF sont refusées ; les erreurs ne divulguent ni classe ni trace.

Note de méthode : un premier test en MockMvc concluait à tort à l'absence de `SameSite` et du cookie CSRF. Cause : le post-processeur `csrf()` de Spring Security remplace le dépôt de jetons du filtre pour **tout le contexte partagé**. Le test utilise désormais la vraie poignée de main et un serveur HTTP réel. À retenir pour tout test touchant les cookies.

Défaut du harnais corrigé : la suite PostgreSQL dépassait `max_connections` (« too many clients ») car chaque contexte Spring mis en cache garde un pool de 10 connexions. `PostgresTestHelper` borne désormais les pools à 5.

Non vérifié ou non corrigé :

- **Dépendances** : `npm audit` des dépendances de production = 0 vulnérabilité ; l'ensemble signale 1 avis de gravité haute sur `undici`, transitif de `jsdom` (outil de test, non livré). Aucune dépendance modifiée. Aucun audit des dépendances Maven n'a été exécuté (pas d'outil installé).
- **Proxy et adresse IP** : `request.getRemoteAddr()` alimente l'audit et la limitation de connexion ; derrière un proxy inverse, `server.forward-headers-strategy` doit être défini au déploiement, sinon tous les clients partagent la même IP et un attaquant peut bloquer tout le monde (ou la limitation est inopérante).
- **Liste des factures administrateur non paginée** (`findAll`) : problème de volumétrie, non corrigé (changement de contrat à coordonner avec l'interface).
- **Audit de prise en charge/restitution** : sans IP, erreurs ignorées.
- **Concurrence** : verrouillages et contrainte d'exclusion testés sur PostgreSQL pour réservations, affectations, transferts et paiements ; aucun test de charge.

## 3. Décisions métier à trancher (nouvelles ou rappelées)

| Réf. | Question | Conséquence si non tranchée |
|---|---|---|
| D-SEC1 | Seuils de limitation de connexion (5 échecs / 15 min par e-mail + IP : valeur de départ choisie ici, non validée) | trop strict (clients bloqués) ou trop lâche |
| D-O1 | Une note de dommage place-t-elle toujours le véhicule en maintenance ? (comportement actuel, non spécifié) | véhicules retirés de la location par une simple remarque |
| D-F5 | Contenu légal de la facture (identité, adresse, matricule fiscal, TVA, numérotation, langue) | PDF inutilisable comme pièce comptable |
| D-A1 | Réinitialisation de mot de passe et vérification d'e-mail : fournisseur d'envoi, durée de validité des liens | comptes non récupérables |
| D-CH1 | Qui peut modifier un chauffeur et quels champs | numéros de permis erronés non corrigeables |
| D-F1 à D-F4, D-T1 à D-T5, D-S1 à D-S3, D-C1 à D-C3, D-P1 à D-P3, D-N1 à D-N4, D-D1, D-D2, D-R1, D-R2, D-B1 | voir `step-6b-security-and-modules.md` | modules concernés non développables sans invention de règles |

## 4. Phase E — bases historiques (état)

Rien de nouveau n'a été exécuté sur une base historique. La procédure existante reste : sauvegarde, `preflight-before-flyway.sql`, répétition sur une **copie**, puis migration. Aucune migration de base historique n'est déclarée validée ; seuls des jeux synthétiques ont été migrés (20 scénarios). Préconditions, stratégies de reprise et risques : `postgres-stabilization-report.md` sections 3 à 5 et 11.

Reste à définir avec le propriétaire des données : où se trouve la base à migrer, comment obtenir une copie représentative (pseudonymisée si elle contient des données personnelles), qui valide le mapping `payments`/`invoices` vers `legacy_*`, procédure de restauration testée (`pg_dump` / `pg_restore` sur cluster isolé).

## 5. Résultats réels de cette étape

| Vérification | Résultat |
|---|---|
| `.\mvnw.cmd -Ppostgres-test verify` (PostgreSQL 18.3 isolé, base recréée) | surefire **138 tests, 0 échec, 0 erreur, 0 ignoré** ; failsafe **23 tests, 0 échec** ; BUILD SUCCESS |
| `.\mvnw.cmd -DskipITs test` (H2) | **138 tests, 0 échec, 0 erreur, 10 ignorés** ; BUILD SUCCESS |
| `npm test -- --watch=false` | **38 tests**, 9 fichiers, tous réussis |
| `npm run build` | réussi (avertissements connus : budget SCSS de la page d'accueil, dépendance CommonJS `jsbi`) |
| `.\scripts\e2e-postgres.ps1` (PostgreSQL isolé, Flyway réel) | **21 scénarios réussis**, 0 échec |
| E2E sur H2 | non rejoués |
| Audit des dépendances Maven | non exécuté |

## 6. Prochaines actions recommandées

1. Trancher D-F5, D-A1, D-SEC1 et D-CH1 : ils débloquent les écarts **P0/P1** (facture légale, mot de passe oublié, modification des chauffeurs).
2. Mettre en place une CI exécutant `mvnw verify` (H2), Angular et, si possible, le profil PostgreSQL avec un service PostgreSQL éphémère.
3. Choisir et rejouer une copie pseudonymisée de la base historique avec `preflight-before-flyway.sql`.
4. Décider le périmètre des notifications (le plus visible pour les clients) puis le développer sur le modèle du catalogue de services.
5. Définir le comportement des transferts (D-T1 à D-T5) avant d'ajouter leur interface.

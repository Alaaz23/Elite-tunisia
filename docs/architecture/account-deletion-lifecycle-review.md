# Revue du cycle de vie des comptes : suppression et désactivation

Statut : analyse en lecture seule (point 4 de la revue V1).
Périmètre : `users`, `customers`, `employees`, et leurs dépendances directes et indirectes.
Aucun code, schéma, migration ni donnée n'a été modifié pour cette analyse.

## 1. Synthèse

- Aucune suppression physique de compte (`users`, `customers`, `employees`) n'existe dans l'API, les services Spring Boot ou l'interface Angular. Le seul mécanisme de « retrait » est la désactivation (`users.enabled = false`).
- La suppression physique d'un utilisateur est **bloquée** dès qu'il est lié à des réservations, devis, demandes de service, documents ou opérations : les FK sont en `RESTRICT` (comportement par défaut). Ce blocage est un comportement sûr, mais il rend toute suppression RGPD impossible sans anonymisation préalable.
- Le risque le plus sérieux est **financier** : `payments.quote_id` est en `ON DELETE CASCADE`. Supprimer un devis (par SQL manuel ou par une future fonctionnalité) efface ses paiements sans contrôle.
- `invoices.customer_id` n'a **aucune** clé étrangère : un client supprimé peut laisser des factures orphelines.
- La désactivation est déjà un soft delete partiel : elle bloque la connexion et révoque les refresh tokens, mais ne couvre pas tous les accès (tokens d'accès encore valides, données visibles par les admins, notifications).
- Recommandation : **conserver la désactivation logique comme seul mécanisme de retrait**, interdire toute suppression physique des comptes ayant un historique, et traiter la conformité RGPD par une procédure d'anonymisation séparée, après décisions métier.

## 2. Cartographie des relations

### 2.1 Héritage `users` → `customers` / `employees`

| Table enfant | Colonne | Référence | Comportement |
|---|---|---|---|
| `customers` | `id` (PK) | `users(id)` | `ON DELETE CASCADE` (V1, l.28) |
| `employees` | `id` (PK) | `users(id)` | `ON DELETE CASCADE` (V1, l.36) |
| `user_roles` | `user_id` | `users(id)` | `ON DELETE CASCADE` (V2) |
| `refresh_tokens` | `user_id` | `users(id)` | `ON DELETE CASCADE` (V1, l.223) |
| `notifications` | `user_id` | `users(id)` | `ON DELETE CASCADE` (V1, l.193) |

Conséquence : `DELETE FROM users` supprime en cascade `customers` et `employees`, **sauf** si une FK `RESTRICT` pointe vers `customers` ou `employees` (voir 2.2). Dans ce cas, la suppression échoue entièrement (transaction annulée), ce qui est le comportement souhaité.

### 2.2 Dépendances vers `customers` (et donc vers `users` via l'héritage)

| Table | Colonne | Référence | Comportement | Fichier / migration |
|---|---|---|---|---|
| `bookings` | `customer_id` NOT NULL | `customers(id)` | `RESTRICT` | V1, l.105 |
| `quotes` | `customer_id` NOT NULL | `customers(id)` | `RESTRICT` | V1, l.135 |
| `service_requests` | `customer_id` NULL | `customers(id)` | `RESTRICT` | V1, l.174 |
| `invoices` | `customer_id` NOT NULL | **aucune FK** | — | V7 |

### 2.3 Dépendances vers `users` (acteurs, propriétaires, audit)

| Table | Colonne | Comportement | Fichier |
|---|---|---|---|
| `documents` | `owner_user_id` | `RESTRICT` | V1, l.203 |
| `audit_logs` | `actor_user_id` | `RESTRICT` | V1, l.214 |
| `quote_events` | `actor_id` | `RESTRICT` | V5, l.45 |
| `booking_events` | `actor_id` | `RESTRICT` | V4, l.35 |
| `booking_operations` | `actor_id` NOT NULL | `RESTRICT` | V8, l.4 |

Ces FK empêchent la suppression d'un utilisateur ayant agi sur une réservation, un devis ou un document. Un administrateur ou un chauffeur ayant effectué une opération ne peut donc pas être supprimé physiquement.

### 2.4 Chaîne financière (indirecte)

| Table | Lien | Comportement | Fichier |
|---|---|---|---|
| `payments` | `quote_id` | **`ON DELETE CASCADE`** | V6 |
| `payments` | `booking_id` | `ON DELETE SET NULL` | V6 |
| `bookings` | `quote_id` | à confirmer dans V4/V5 | — |

Chaîne de conséquences : supprimer un `quotes` supprime ses `payments`, y compris les paiements `CAPTURED`. La suppression d'un devis est bloquée seulement si une autre FK le référence. Ce point est le plus grave de l'analyse (voir R-01).

### 2.5 Entités non suivies par FK

- `invoices.customer_id` : pas de FK (V7). Une suppression de client ne serait pas bloquée.
- `invoice_lines`, `invoice_events`, `invoice_payments` : lien vers `invoices` à vérifier dans V7 (non relu en détail pour cette revue).
- Partenaires et véhicules : la relation véhicule → partenaire et la règle d'isolation partenaire n'ont pas été vérifiées dans cette passe. **Point non vérifié.**

## 3. Chemins de suppression et de désactivation dans le code

### 3.1 Backend

| Élément | Preuve | Constat |
|---|---|---|
| Suppression d'utilisateur | aucune occurrence de `delete` sur `UserEntity` / `UserRepository` dans `user/` | Pas de suppression physique |
| Désactivation admin | `AdminUserService.updateStatus`, `PATCH /api/admin/users/{id}/status` | Met `enabled=false`, révoque les refresh tokens, audite `USER_DISABLED` |
| Protection du dernier super-admin | `rejectIfLastSuperAdminLost`, verrou `roles.lockSuperAdminRole()` | Présente dans le flux de modification de rôle ; à vérifier pour le flux de désactivation |
| Blocage de connexion | `SecurityConfig` ligne 50 : `.disabled(!user.isEnabled())` | Un compte désactivé ne peut pas se connecter |
| Profil client | `CustomerProfileController` : `GET` et `PUT /api/customer/profile` | Aucun `DELETE` |
| Employés | création et modification dans `AdminUserService` | Aucun `DELETE` |
| Contrôleur admin clients | `AdminCustomerController` : `GET` uniquement, `customers:read` | Aucune action de désactivation client |

### 3.2 Suppressions physiques présentes dans l'API admin (hors comptes)

Catégories, marques, véhicules, affectations de chauffeurs, chauffeurs (`AdminDriverController` `DELETE`), images. Ces suppressions ne concernent pas les comptes, mais elles montrent que le projet expose déjà des `DELETE` ; le même pattern ne doit pas être étendu aux comptes sans garde.

### 3.3 Frontend Angular

- `driver-api.service.ts` : méthode `deactivate` et suppression de chauffeur (cohérentes avec le backend).
- Aucun bouton de suppression de compte client ou administrateur trouvé dans les composants lus. Les composants `admin-customers` ne proposent que la recherche et la pagination. **Point non vérifié** sur l'ensemble des composants admin utilisateurs.

## 4. Analyse des risques

Criticité : **P0** bloquant avant préproduction ; **P1** à corriger avant production ; **P2** amélioration.

### R-01 — Suppression d'un devis efface ses paiements (P0 si une suppression est possible)

- Preuve : `payments.quote_id ON DELETE CASCADE` (V6).
- Fichiers : `database/migrations/V6__payments.sql`.
- Impact métier : perte de l'historique financier d'un paiement `CAPTURED` ; rupture de la traçabilité comptable.
- Aucun endpoint de suppression de devis trouvé dans le code applicatif ; le risque vient d'un SQL manuel ou d'une future fonctionnalité.
- Correction recommandée : passer `payments.quote_id` en `ON DELETE RESTRICT` par une migration corrective (à valider, non appliquée). Interdire la suppression d'un devis ayant un paiement `CAPTURED` au niveau applicatif.

### R-02 — Suppression RGPD impossible sans anonymisation (P1)

- Preuve : `bookings`, `quotes`, `service_requests` en `RESTRICT` sur `customers`, et `audit_logs`, `booking_operations`, `documents` en `RESTRICT` sur `users`.
- Impact : une demande d'effacement ne peut être satisfaite que par anonymisation (remplacement des données personnelles), la suppression de la ligne étant interdite par la conservation financière et d'audit.
- Correction recommandée : définir une procédure d'anonymisation (nom, téléphone, e-mail, adresse) qui conserve les identifiants techniques. Ne pas la coder sans décision métier sur la durée de conservation.

### R-03 — `invoices.customer_id` sans clé étrangère (P1)

- Preuve : V7, `customer_id uuid NOT NULL` sans `REFERENCES`.
- Impact : une facture peut référencer un client inexistant ; la suppression d'un client n'est pas bloquée par la base ; intégrité référentielle absente.
- Correction recommandée : ajouter `FOREIGN KEY (customer_id) REFERENCES customers(id)` avec `RESTRICT`, après vérification des données existantes (requête d'orphelins en lecture seule). Migration à proposer, non appliquée.

### R-04 — Désactivation incomplète (P1)

- Preuve : `AdminUserService.updateStatus` met `enabled=false` et révoque les refresh tokens. `SecurityConfig` bloque la connexion.
- Risque : un access token JWT déjà émis reste valide jusqu'à expiration ; ce point reste à vérifier dans le filtre JWT et la durée de vie des tokens.
- Correction recommandée : vérifier `enabled` à chaque requête authentifiée (ou durée de vie courte), et tester qu'un token d'un compte désactivé est refusé.

### R-05 — Désactivation d'un administrateur sans garde complète (P1)

- Preuve : la protection `LAST_SUPER_ADMIN` est documentée pour le changement de rôle ; le flux `updateStatus` doit être vérifié pour s'assurer qu'il ne désactive pas le dernier super-admin actif.
- Impact : verrouillage total de l'administration.
- Correction recommandée : appliquer la même garde à `updateStatus` (compte actif avec `SUPER_ADMIN`) et le tester.

### R-06 — Désactivation d'un client : effets sur les réservations en cours (P1, décision métier)

- Preuve : aucune logique n'annule ni ne gèle les réservations, devis ou factures d'un client désactivé.
- Impact : une réservation active peut continuer alors que le compte est bloqué ; les véhicules restent bloqués.
- Décision métier requise : que devient une réservation confirmée d'un client désactivé ?

### R-07 — Suppression d'un employé ou chauffeur encore affecté (P2)

- Preuve : `DELETE` chauffeur existe (`AdminDriverController`), les affectations ont leur propre FK ; la cohérence n'a pas été testée.
- Correction recommandée : refuser la suppression d'un chauffeur ayant des missions non clôturées ; préférer la désactivation.

### R-08 — Absence de tests sur le cycle de vie des comptes (P1)

- Constat : aucun test trouvé pour la désactivation (login refusé, token refusé, dernier super-admin protégé pour `updateStatus`).
- Correction recommandée : tests d'intégration PostgreSQL sur `updateStatus` et sur la révocation des tokens.

## 5. Évaluation du soft delete

### 5.1 Ce qui existe déjà

Le modèle utilise déjà `users.enabled` comme désactivation logique, avec révocation des tokens et audit `USER_DISABLED`. C'est un soft delete suffisant pour bloquer l'accès.

### 5.2 Ce qui manque

- Pas d'horodatage ni d'acteur de désactivation dans la table `users` (l'audit log le couvre, mais pas la donnée elle-même).
- Pas de statut « anonymisé » ni de date de fin de conservation.
- Pas de désactivation pour les clients et partenaires qui ne passe pas par `users`.

### 5.3 Recommandation

- Conserver la désactivation logique (`enabled`) comme **unique** mécanisme de retrait d'un compte utilisateur.
- Ne pas introduire de colonne `deleted_at` tant qu'aucune règle de restauration n'est définie : elle doublerait `enabled` sans bénéfice clair.
- Interdire toute suppression physique de `users`, `customers` et `employees` ayant un historique (les FK le font déjà, mais il faut le rendre explicite dans la documentation et les tests).
- Pour la conformité RGPD, prévoir une anonymisation (voir R-02), distincte de la désactivation.

## 6. Décisions métier à prendre

1. **Durée de conservation** des données financières, des factures et des logs d'audit (ex. 10 ans pour les factures selon la réglementation applicable à valider avec un juriste).
2. **Politique d'effacement** : désactivation seule, ou anonymisation sur demande ? Quel délai ?
3. **Réservations d'un client désactivé** : annulation automatique, maintien, ou blocage du compte uniquement ?
4. **Administrateur désactivé** : peut-il conserver ses données d'audit visibles ? Peut-il être réactivé par qui ?
5. **Chauffeur avec missions ouvertes** : refus de désactivation ou réaffectation obligatoire ?

## 7. Recommandation architecturale

1. Maintenir la désactivation logique comme seul retrait de compte ; ne pas ajouter de suppression physique.
2. Corriger d'abord les risques P0/P1 : R-01 (cascade sur paiements), R-03 (FK manquante sur factures), R-05 (garde du dernier super-admin à la désactivation).
3. Vérifier et tester R-04 (token d'un compte désactivé refusé).
4. Concevoir une procédure d'anonymisation après les décisions métier (section 6).
5. Chaque migration corrective doit être proposée en `database/proposed/`, validée, puis appliquée sur base isolée seulement après autorisation explicite.

## 8. Points non vérifiés

- Filtre JWT et durée de vie des access tokens (vérifier `enabled` à chaque requête).
- Flux `updateStatus` complet et garde du dernier super-admin à la désactivation.
- Relation `bookings.quote_id` et contraintes de V7 pour `invoice_lines`, `invoice_events`, `invoice_payments`.
- Modèle partenaire et isolation des véhicules par partenaire.
- Composants Angular d'administration des utilisateurs (liste, désactivation).
- Données historiques réelles : aucune copie représentative disponible, aucune requête d'orphelins exécutée.

## 9. Garanties de cette revue

- Lecture seule : aucune requête SQL d'écriture, aucune migration, aucun changement de code.
- Les constats ci-dessus proviennent de la lecture des fichiers cités ; les points de la section 8 ne sont pas confirmés.
- Aucun test n'a été exécuté pour cette analyse.

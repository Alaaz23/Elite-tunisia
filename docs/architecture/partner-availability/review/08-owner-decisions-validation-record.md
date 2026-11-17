# 08 — Enregistrement des décisions du propriétaire et registre unique D-PA

Statut : **documentaire**. Aucun code Java ou Angular, aucune migration, aucune configuration et aucune base de données n'ont été modifiés. Rien n'est implémenté.

Le présent document fait référence pour les identifiants `D-PA-xx`. Il remplace les tables de numérotation des documents `10`, `02` (dossier `review/`) et `12` en cas de divergence.

## 1. Décisions approuvées par le propriétaire

| Décision | Statut | Résumé |
|---|---|---|
| **D-PA-05** | **Validée** (Option A, session précédente) | Garantie liée au véhicule et au partenaire porteur, figé à la création |
| **D-PA-14** | **Validée** (identifiant officiel) | Identifiant du chevauchement des disponibilités garanties. Remplace les anciennes références « D-PA-05 » au chevauchement |
| **D-PA-12** | **Validée** | Transfert d'un véhicule vers un autre partenaire refusé tant qu'un engagement actif existe (voir §3) |
| **Historique des rattachements** | **Validée** (création d'une table dédiée) | Table `vehicle_partner_assignments` (voir §4) |
| **Référence réservation → garantie** | **Validée dans son principe** | Toute nouvelle réservation du système de disponibilités garanties référence obligatoirement la garantie (voir §5) |

Ces décisions sont validées au sens du propriétaire. Elles ne sont **pas** implémentées.

## 2. Registre unique des décisions D-PA

### 2.1 Inventaire des incohérences trouvées

Trois séries de numérotation coexistaient avant cette validation :

- **Série « 10 »** (`10-business-decisions-required.md`), la plus complète.
- **Série « review/02 »** (`review/02-business-decisions-analysis.md`), qui réutilisait les mêmes numéros pour d'autres sujets.
- **Série « propriétaire »** (missions de validation), qui fixe D-PA-01, D-PA-04, D-PA-07, D-PA-12 et D-PA-14.

Divergences relevées :

| Identifiant | Série 10 | Série review/02 | Série propriétaire | Décision |
|---|---|---|---|---|
| D-PA-02 | Changement de partenaire | Type de partenaire | — | Changement de partenaire → **D-PA-12**. Type de partenaire → **D-PA-15** |
| D-PA-04 | Retrait d'un véhicule garanti | Traitement PENDING | PENDING | PENDING → **D-PA-04**. Retrait → **D-PA-07** |
| D-PA-05 | Chevauchement (ancien) | Partenaire porteur | Partenaire porteur | Partenaire porteur → **D-PA-05** (validé). Chevauchement → **D-PA-14** |
| D-PA-06 | PENDING | Visibilité PENDING pour le partenaire | — | Fusionné dans **D-PA-04** |
| D-PA-07 | Maintenance planifiée | Retrait avec réservation | Retrait avec réservation confirmée | Retrait → **D-PA-07**. Maintenance → **D-PA-16** |
| D-PA-08 | Partenaire désactivé | Visibilité publique | — | Visibilité publique → **D-PA-08**. Partenaire désactivé → **D-PA-17** |
| D-PA-09 | Pénalités | Expiration de garantie | — | Pénalités → **D-PA-09**. Expiration → **D-PA-01** (sous-question) |
| D-PA-10 | Moment de confirmation et D5 | Confirmation admin ou automatique | — | **D-PA-10**, D5 (moment de l'acompte) et mode de confirmation regroupés |
| D-PA-11 | Droit de création de partenaires | Conséquence financière | — | Droit de création → **D-PA-11**. Conséquence financière → **D-PA-09** |
| D-PA-12 | Rôle PARTNER | Changement de propriétaire | — | Changement de propriétaire → **D-PA-12**. Rôle PARTNER → **D-PA-18** |
| D-PA-13 | Durée minimale de location | Désactivation de partenaire | — | Durée minimale → **D-PA-13**. Désactivation → **D-PA-17** |

### 2.2 Registre canonique

| ID | Sujet | Statut | Anciens libellés |
|---|---|---|---|
| D-PA-01 | Durée et période de validité d'une garantie, expiration | **Ouverte** | Série 10 D-PA-01 ; review D-PA-09 (expiration) |
| D-PA-02 | Libre (ancienne numérotation « changement de partenaire » retirée) | — | — |
| D-PA-03 | Modification d'une garantie avant réservation | **Ouverte** | Série 10 et review D-PA-03 |
| D-PA-04 | Réservations PENDING : blocage, expiration, visibilité partenaire | **Ouverte** | Série 10 D-PA-06 ; review D-PA-04 et D-PA-06 |
| D-PA-05 | Garantie liée au véhicule et au partenaire porteur (Option A) | **Validée** | Review D-PA-05 |
| D-PA-06 | Libre (fusionné dans D-PA-04) | — | — |
| D-PA-07 | Retrait d'une garantie avec réservation confirmée, et conditions du retrait (préavis, pénalité) | **Ouverte** (règle de principe : pas de retrait qui annule une réservation CONFIRMED) | Série 10 D-PA-04 ; propriétaire D-PA-07 |
| D-PA-08 | Visibilité publique : seuls les véhicules garantis pour les dates | **Ouverte** | Review D-PA-08 |
| D-PA-09 | Pénalités en cas de non-respect d'un engagement | **Ouverte**, avis juridique et comptable requis | Série 10 D-PA-09 ; review D-PA-11 |
| D-PA-10 | Moment de confirmation de la réservation et de l'acompte (D5), mode de confirmation | **Ouverte** | Série 10 D-PA-10 ; review D-PA-10 |
| D-PA-11 | Droit de création de partenaires | **Ouverte** (recommandation : `MANAGER` et `SUPER_ADMIN`) | Série 10 D-PA-11 |
| D-PA-12 | Changement de partenaire d'un véhicule | **Validée** | Série 10 D-PA-02 ; review D-PA-12 |
| D-PA-13 | Durée minimale de location pour la recherche | **Ouverte** | Série 10 D-PA-13 |
| D-PA-14 | Chevauchement des disponibilités garanties | **Validée** (identifiant) ; règle de traitement à préciser | Série 10 D-PA-14 provisoire ; anciens « D-PA-05 » chevauchement |
| D-PA-15 | Type juridique du partenaire (personne physique ou morale) | **Ouverte**, provisoire | Review D-PA-02 |
| D-PA-16 | Maintenance planifiée dans une garantie | **Ouverte**, provisoire | Série 10 D-PA-07 |
| D-PA-17 | Partenaire désactivé avec garanties futures | **Ouverte**, provisoire | Série 10 D-PA-08 ; review D-PA-13 |
| D-PA-18 | Création d'un accès au rôle `PARTNER` en phase 1 | **Ouverte**, provisoire | Série 10 D-PA-12 |

Les identifiants D-PA-15 à D-PA-18 sont **nouveaux** et provisoires. Ils ont été créés pour éliminer les collisions. Le propriétaire doit les valider ou les renuméroter. D5 n'a pas d'identifiant D-PA propre ; il est rattaché à D-PA-10.

### 2.3 Divergences restantes

- Les documents `review/01`, `review/03`, `review/04`, `review/05` et `review/06` peuvent encore contenir d'anciennes numérotations non corrigées dans cette mission. Voir §8.
- L'ancien identifiant « D-PA-06 » de visibilité partenaire est fusionné dans D-PA-04 ; il n'est plus utilisé comme identifiant indépendant.

## 3. D-PA-12 — Changement de partenaire d'un véhicule

### 3.1 Règle approuvée

Un véhicule ne peut pas changer de partenaire tant qu'il possède un **engagement actif** susceptible d'être affecté par le transfert. En version 1, le transfert est **refusé** si l'un des éléments suivants existe :

1. une disponibilité garantie encore active (§3.2) ;
2. une réservation future `CONFIRMED` ;
3. une réservation `IN_PROGRESS` ;
4. tout autre engagement contractuel actif identifié par l'analyse métier (à lister, §3.5).

Un transfert ne modifie **jamais** rétroactivement une garantie ou une réservation historique. Les garanties conservent leur partenaire porteur initial (D-PA-05).

Le transfert est une **opération contrôlée par Elite**, tracée. Une évolution future permettant un transfert contractuel avec consentement des parties est **hors périmètre** de la version 1.

### 3.2 Définition d'un engagement actif

| Élément | Actif si | Détail |
|---|---|---|
| Garantie | statut `ACTIVE` et période non terminée (`upper(period) > now()`) | Une garantie retirée (`WITHDRAWN`) ou expirée n'est pas active |
| Réservation future | `CONFIRMED` et `ends_at > now()` | Bloque le transfert |
| Réservation en cours | `IN_PROGRESS` | Bloque le transfert, quelle que soit la date |
| Réservation `PENDING` | **Non tranché** (D-PA-04) | Recommandation : bloquer tant qu'elle existe, par prudence. Ce n'est pas une décision |
| Réservation annulée | Non active | Ne bloque pas |
| Location terminée (`ends_at <= now()`) | Non active | Reste dans l'historique, ne bloque pas |

### 3.3 Conséquences sur les périodes garanties

- Une garantie dont la période est **entièrement passée** n'est pas active, même si son statut est encore `ACTIVE`. Un traitement d'expiration doit la passer en `EXPIRED` (règle D-PA-01, à valider).
- Une garantie **future** ou **en cours** bloque le transfert. Il n'existe **aucune** libération automatique : la règle de libération dépend de D-PA-01 (durée et expiration) et de D-PA-07 (retrait). Elles ne sont pas validées. Le système ne doit donc pas inventer une libération.

### 3.4 Conséquences sur les réservations annulées et terminées

- Une réservation annulée ne bloque plus le transfert, mais reste référencée par la garantie et l'historique.
- Une location terminée reste rattachée à la garantie et au partenaire porteur historiques.

### 3.5 Points à identifier par l'analyse métier (non validés)

- Liste exhaustive des « autres engagements contractuels » : contrats de partenariat, litiges ouverts, retrait programmé, acompte non soldé. **À confirmer par le propriétaire.**

### 3.6 Procédure de transfert (cible)

Ordre de verrouillage existant : **Vehicle → Booking → autres entités**.

```mermaid
sequenceDiagram
    participant Admin as Admin Elite
    participant API as Backend
    participant DB as PostgreSQL
    Admin->>API: POST transfert (vehicle, nouveau partenaire, motif, date)
    API->>DB: BEGIN; verrou véhicule (SELECT ... FOR UPDATE)
    API->>DB: Vérifier partenaire actuel et nouveau partenaire actifs
    API->>DB: Vérifier garanties ACTIVE, réservations CONFIRMED futures, IN_PROGRESS, autres engagements
    alt un engagement actif existe
        API-->>Admin: 409 refus métier, avec motif
    else aucun engagement actif
        API->>DB: Clore le rattachement courant (valid_during upper = date)
        API->>DB: Insérer le nouveau rattachement (admin, motif, date, référence opération)
        API->>DB: Mettre à jour vehicles.partner_id
        API->>DB: Écrire audit_logs
        API->>DB: COMMIT
        API-->>Admin: 200 transfert effectué
    end
```

Points techniques :

- Tout se fait dans une **seule transaction**, avec le verrou véhicule pris en premier.
- Les vérifications sont refaites **après** le verrou, jamais avant.
- Un transfert concurrent avec une création de garantie ou de réservation est sérialisé par le verrou véhicule.
- Le refus renvoie une réponse métier compréhensible (motif, engagement bloquant), pas une erreur technique.

### 3.7 Données obligatoires du transfert

Partenaire actuel (validé), nouveau partenaire (identifié et actif), date d'effet, administrateur responsable, motif obligatoire, référence d'opération optionnelle.

## 4. Historique des rattachements véhicule-partenaire

### 4.1 Décision

Création d'une table dédiée, **append-only**, pour retrouver l'historique des associations entre véhicules et partenaires.

### 4.2 Modèle cible (documentaire, non migré)

| Colonne | Type | Contrainte |
|---|---|---|
| `id` | UUID | PK |
| `vehicle_id` | UUID | NOT NULL, FK `vehicles(id)`, sans cascade |
| `partner_id` | UUID | NOT NULL, FK `partners(id)`, sans cascade |
| `valid_during` | `tstzrange` | NOT NULL, borne `[)`, borne haute `NULL` pour le rattachement courant |
| `started_at` | TIMESTAMPTZ | NOT NULL (= borne basse) |
| `ended_at` | TIMESTAMPTZ | NULL tant que le rattachement est actif |
| `operation_at` | TIMESTAMPTZ | NOT NULL, date de l'opération de saisie |
| `assigned_by_user_id` | UUID | NOT NULL, FK `users(id)` |
| `reason` | TEXT | NOT NULL, motif |
| `transfer_operation_id` | UUID | NULL, référence de l'opération de transfert |

### 4.3 Contraintes

- `EXCLUDE USING gist (vehicle_id WITH =, valid_during WITH &&)` : **un seul rattachement à la fois** pour un véhicule. Empêche les rattachements concurrents ou chevauchants. Nécessite `btree_gist`, déjà utilisé par V4 (à vérifier).
- Index unique partiel complémentaire `(vehicle_id) WHERE ended_at IS NULL` : garantit un seul rattachement courant, lisible.
- `CHECK (lower(valid_during) = started_at)` et `CHECK (ended_at IS NULL OR upper(valid_during) = ended_at)`.
- **Append-only** : aucun `DELETE` ni `UPDATE` hors clôture (`ended_at`). Imposable par le service, par des droits SQL ou par un trigger (décision d'implémentation).
- FK sans `ON DELETE CASCADE` : un partenaire désactivé **n'est jamais supprimé**, donc son historique reste.

### 4.4 Articulation avec `vehicles.partner_id`

- `vehicles.partner_id` = **partenaire actuellement rattaché** (valeur de lecture rapide).
- La ligne ouverte (`ended_at IS NULL`) de l'historique doit toujours correspondre à `vehicles.partner_id`. Cette cohérence est assurée **par le service dans la même transaction**. Une contrainte SQL inter-tables n'est pas possible en CHECK.
- Un test d'intégrité doit vérifier cette correspondance après chaque transfert.

### 4.5 Distinction des trois notions (toujours séparées)

| Notion | Source | Mutable | Question à laquelle elle répond |
|---|---|---|---|
| Partenaire **actuel** du véhicule | `vehicles.partner_id` | Oui (transfert contrôlé, D-PA-12) | À qui appartient le véhicule aujourd'hui ? |
| Partenaire **porteur** d'une garantie | `guaranteed_availabilities.partner_id` | **Non** après création (D-PA-05) | Qui a engagé cette disponibilité ? |
| **Historique** des rattachements | `vehicle_partner_assignments` | Non (append-only) | Qui était rattaché au véhicule à telle date ? |

## 5. Référence réservation → garantie

### 5.1 Principe approuvé

Toute **nouvelle** réservation créée dans le système de disponibilités garanties **doit** référencer la garantie utilisée. Objectifs : traçabilité, identification du partenaire porteur, retrouver la période garantie, audits et litiges.

Pour les réservations **historiques** créées avant le nouveau système, la référence peut rester `NULL`.

### 5.2 Modèle cible

- Colonne `bookings.availability_id UUID NULL`, FK vers `guaranteed_availabilities(id)` avec `ON DELETE RESTRICT` (ou `NO ACTION`).
- Pas de suppression physique d'une garantie référencée. La suppression est remplacée par un statut (`WITHDRAWN`, `EXPIRED`) ou un archivage.
- **Nullable en SQL** pour les données historiques, **mais** le backend impose la présence pour toute nouvelle réservation. La nullabilité SQL ne doit jamais permettre de créer une réservation nouvelle sans garantie.

### 5.3 Contrôle de non-contournement

Deux options techniques, à choisir à l'implémentation (non validées) :

- **Option 1 (recommandée)** : `CHECK (availability_id IS NOT NULL OR booking_model = 'LEGACY')` avec une colonne `booking_model` (`LEGACY` ou `GUARANTEED`) figée à la création. Les anciennes lignes sont `LEGACY`, les nouvelles `GUARANTEED`, donc `availability_id` est obligatoire pour toute nouvelle ligne.
- **Option 2** : CHECK basé sur une date de bascule. Plus fragile, car dépend d'une constante et d'un horodatage.

Le contrôle backend (`BookingService`, dans la transaction et sous verrou véhicule) reste **obligatoire** dans les deux cas.

### 5.4 Cycle de vie de la référence

- **Création** : la garantie est lue et verrouillée (ordre Vehicle → Booking) ; sa couverture est vérifiée ; le partenaire porteur est lu depuis la garantie.
- **Annulation** : la référence est **conservée**. La réservation annulée ne libère pas la garantie automatiquement. La libération dépend de D-PA-01 et D-PA-07, non validées.
- **Fin de location** : la référence est conservée pour l'historique et les litiges.
- **Retrait de garantie** : une garantie référencée par une réservation `CONFIRMED` ne peut pas être retirée (règle existante, D-PA-07 pour les conditions). Une garantie référencée par une réservation terminée ou annulée ne peut jamais être supprimée physiquement.

### 5.5 Stratégie de migration (non écrite)

1. Ajouter la colonne `availability_id` nullable et `booking_model` avec valeur par défaut `LEGACY` pour l'existant.
2. Ne pas rétro-remplir les anciennes réservations : aucune garantie historique n'existe.
3. Ajouter la FK et le CHECK après la création de `guaranteed_availabilities` (V24).
4. Tester sur PostgreSQL isolé uniquement, après autorisation.

Ces étapes ne sont **pas** écrites ni exécutées.

## 6. Impacts sur les autres documents et le modèle

| Sujet | Impact |
|---|---|
| Modèle de données (doc 04) | Ajout de `vehicle_partner_assignments`, de `bookings.availability_id` et de `bookings.booking_model`, avec règles de suppression |
| Architecture (doc 03) | Service de transfert, contrôle de non-contournement dans `BookingService` |
| Roadmap (doc 11) | Lot 2 : transfert et historique. Lot 5 : référence obligatoire |
| Propriétaire (doc 12) | Décisions validées listées, identifiants corrigés |

## 7. Décisions toujours ouvertes (ne pas considérer comme réglées)

- **D-PA-01** : durée et période de validité de la garantie.
- **D-PA-04** : traitement des réservations PENDING.
- **D-PA-07** : retrait d'une garantie avec réservation confirmée.
- **D-PA-10 / D5** : moment de l'acompte et cycle de paiement.

Les décisions D-PA-03, D-PA-08, D-PA-09, D-PA-11, D-PA-13, D-PA-15 à D-PA-18 restent également ouvertes.

Les recommandations ne sont pas des décisions. Par exemple, « bloquer le transfert en présence d'une réservation PENDING » reste une recommandation.

## 8. Incohérences restantes

- Documents `review/01`, `review/04`, `review/05`, `review/06` : anciennes numérotations à corriger dans le même lot documentaire (corrections effectuées ou signalées dans la mission).
- Le document `review/02` garde ses sections d'analyse numérotées de l'ancienne série ; son tableau de synthèse est corrigé, et le document est marqué comme subordonné au présent registre.
- Le document `10` a été réaligné sur le registre canonique.

## 9. Pratiques de marché : comparaison

**Avertissement.** Cette section n'a pas été vérifiée en ligne dans cette mission. Elle décrit des pratiques générales. Les détails précis doivent être confirmés sur les sources publiques avant d'être cités comme références.

| Plateforme | Pratiques générales observées | Adaptation Elite |
|---|---|---|
| Turo | Calendrier géré par l'hôte, réservations pouvant être automatiques, engagements de l'hôte | Elite n'est pas l'hôte. La garantie est un engagement envers Elite, confirmé par le partenaire |
| Getaround | Calendrier, réservation instantanée, annonces temporairement indisponibles | Indisponibilité temporaire distincte de l'archivage (règle validée du cycle de vie) |
| Booking.com | Gestion des disponibilités, prévention des surréservations | Contrainte d'exclusion et verrou véhicule, déjà en place pour les réservations |

Distinctions :

- **Pratiques documentées des plateformes** : rappel ci-dessus, à confirmer.
- **Adaptations nécessaires à Elite** : partenaire porteur figé, garantie séparée de la réservation, transfert contrôlé.
- **Recommandations architecturales** : verrou véhicule, contrainte EXCLUDE, historique append-only.
- **Décisions du propriétaire** : §1 uniquement.

Elite n'utilise pas nécessairement le même modèle de données que ces plateformes.

## 10. Dépendances

- D-PA-05 (validé) ← conditionne la référence réservation → garantie et l'historique.
- D-PA-12 (validé) ← dépend de D-PA-01, D-PA-04, D-PA-07 pour la définition complète d'un engagement actif.
- D-PA-14 (validé identifiant) ← règle de traitement à définir ; bloque la contrainte EXCLUDE des garanties.
- Historique ← dépend de D-PA-12.
- Référence réservation ← dépend de D-PA-04 et D-PA-07, pour l'annulation et le retrait.

## 11. Risques techniques restants

- **Fausse sécurité de la contrainte SQL** : les EXCLUDE et FK sont des hypothèses non vérifiées sous PostgreSQL. Aucun test PostgreSQL n'a été exécuté dans cette mission.
- **Divergence entre `vehicles.partner_id` et la ligne ouverte de l'historique** : seul le service et les tests la protègent.
- **Transfert concurrent** : la sérialisation dépend du verrou véhicule, à tester.
- **Données historiques** : `vehicles.partner_id` est NULL pour les anciens véhicules. Le rattachement initial n'a pas de source connue.
- **Code existant** : `BookingService` ne bloque pas la maintenance avec une réservation future `CONFIRMED` (constat déjà documenté, non corrigé dans cette mission).
- **PENDING** : sans décision D-PA-04, le transfert et le retrait ne peuvent pas être finalisés sans risque.

## 12. Prochain lot autorisable

Aucun lot de développement n'est autorisé par ce document. Le prochain pas documentaire est la validation écrite par le propriétaire de :

1. D-PA-01 et D-PA-07 (conditions de libération et de retrait) ;
2. D-PA-04 (PENDING) ;
3. la liste des « autres engagements contractuels » (D-PA-12, §3.5) ;
4. les identifiants provisoires D-PA-15 à D-PA-18.

Le lot 1 (fondations partenaires, sans migration) peut être envisagé dès que l'autorisation écrite de développement est donnée, puisqu'il ne dépend d'aucune des décisions ouvertes ci-dessus.

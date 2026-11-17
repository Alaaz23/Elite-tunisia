# 07 — Enregistrement de validation D-PA-05

Statut : **décision validée par le propriétaire du projet**. Ce document est documentaire. Aucun code, aucune migration et aucune base de données n'ont été modifiés pour cette décision.

## 1. Décision du propriétaire

**D-PA-05 — Option A (validée) :** chaque disponibilité garantie est liée simultanément :

- au **véhicule** concerné ;
- au **partenaire porteur**, c'est-à-dire le partenaire qui a confirmé la disponibilité et porte l'engagement auprès d'Elite.

Cette décision n'est plus une question ouverte. Elle remplace l'ancienne formulation « garantie liée au véhicule ou au partenaire » des analyses antérieures.

## 2. Option choisie et justification métier

| Option | Résumé | Statut |
|---|---|---|
| Garantie liée au véhicule seul, partenaire déduit | Ne conserve pas le partenaire d'origine après un changement de rattachement | Écartée |
| Garantie liée au partenaire seul, sans véhicule | Empêche la réservation directe d'un véhicule précis | Écartée |
| **A : véhicule + partenaire porteur** | Identifie l'engagement et son porteur de façon traçable | **Retenue (validée)** |

Justification : Elite ne possède aucun véhicule. Le client réserve un véhicule précis, mais l'engagement de disponibilité est pris par un partenaire envers Elite. Pour l'isolation, la traçabilité et les litiges, il faut pouvoir retrouver à tout moment quel partenaire a porté chaque engagement.

## 3. Règles métier qui en découlent

1. **Identification.** Une garantie référence un véhicule et un partenaire porteur. Les deux sont obligatoires.
2. **Création.** Le partenaire porteur n'est jamais saisi librement. Il est déterminé à partir du partenaire **actuellement rattaché** au véhicule au moment de la création. Il doit être actif, et le rattachement doit être effectif à cet instant.
3. **Figement.** Le partenaire porteur est un snapshot. Il ne change jamais après création.
4. **Changement de partenaire du véhicule.** Il ne modifie jamais rétroactivement le partenaire porteur des garanties existantes. Exemple : le partenaire A confirme une Porsche pour une période. Elle passe ensuite chez B. La garantie conserve A. Toute nouvelle garantie porte B.
5. **Traçabilité.** Pour chaque garantie, il doit être possible de retrouver : le véhicule, le partenaire porteur, la période, la date de confirmation, l'administrateur Elite ayant enregistré la confirmation et l'historique des opérations.

## 4. Distinction obligatoire des trois notions

| # | Notion | Stockage cible | Mutable | Usage |
|---|---|---|---|---|
| 1 | Partenaire **actuellement** associé au véhicule | `vehicles.partner_id` | Oui, par une procédure de rattachement (D-PA-02 et D-PA-12) | Catalogue, création de nouvelles garanties, isolation courante |
| 2 | Partenaire **porteur** d'une garantie | `guaranteed_availabilities.partner_id` (NOT NULL) | **Non** après création | Engagement, isolation des garanties, traçabilité |
| 3 | **Historique** des partenaires associés au véhicule | Table append-only éventuelle `vehicle_partner_assignments` | Non | Reconstituer les propriétaires passés. **Besoin non tranché.** |

Ces trois notions ne sont pas une seule relation. Elles ne doivent pas être fusionnées dans une même colonne ou une même lecture. En particulier, la notion 2 ne se déduit jamais de la notion 1 après création.

## 5. Conséquences sur le modèle de données

Documentaire uniquement. Aucune migration n'est écrite.

- **Table `guaranteed_availabilities`** : colonnes `vehicle_id` (NOT NULL, FK `vehicles(id)`) et `partner_id` (NOT NULL, FK `partners(id)`).
- **Clés étrangères** : simples, sans `ON DELETE CASCADE`. Un partenaire se désactive, il ne se supprime pas.
- **Contrainte composite interdite.** La contrainte `(vehicle_id, partner_id) → vehicles(id, partner_id)`, proposée dans une version précédente de l'analyse, est **rejetée**. Elle forcerait la cohérence rétroactive et empêcherait de conserver les garanties historiques après un changement de partenaire.
- **Cohérence à la création** : contrôle au niveau service, sous verrou véhicule, puis test. Pas de CHECK possible inter-tables en PostgreSQL.
- **Immutabilité de `partner_id`** : à imposer par le service et, si retenu, par un trigger qui refuse toute modification de la colonne après insertion. Décision technique à confirmer au moment de l'implémentation.
- **Contrainte d'exclusion** : `EXCLUDE (vehicle_id WITH =, period WITH &&) WHERE (status = 'ACTIVE')` reste une décision distincte. Elle ne dépend pas de D-PA-05 mais de la règle de chevauchement (voir section 8).

## 6. Conséquences sur les relations partenaire / véhicule

- `vehicles.partner_id` : valeur courante, unique, nullable pendant la transition des véhicules historiques.
- Un véhicule ne peut recevoir une **nouvelle** garantie que si son partenaire courant est actif.
- Un changement de `vehicles.partner_id` :
  - ne modifie aucune garantie existante ;
  - ne modifie pas `partner_id` des garanties existantes ;
  - est refusé s'il existe un engagement actif (D-PA-12, validé ; voir `review/08-owner-decisions-validation-record.md` §3).
- Un partenaire désactivé ne peut plus recevoir de nouvelle garantie (D-PA-13). Ses garanties existantes et leurs réservations ne sont pas modifiées par cette décision. Leur sort reste à décider (D-PA-08).

## 7. Conséquences sur les réservations

- Une réservation doit pouvoir retrouver la garantie utilisée et son partenaire porteur. Cette traçabilité est nécessaire pour les litiges et les audits.
- **Étude en cours** : une référence optionnelle `bookings.availability_id` vers la garantie utilisée. **Son caractère obligatoire n'est pas décidé.** Il dépend du cycle de vie réel des réservations : PENDING, annulation, retrait de garantie (D-PA-04 et D-PA-07).
- Tant que cette référence n'est pas décidée, le partenaire porteur d'une réservation s'obtient par la garantie référencée, si elle existe. Une réservation créée sans référence ne doit pas être présentée comme traçable au partenaire porteur.
- Une réservation confirmée n'est jamais annulée parce que le véhicule change de partenaire (règle validée).

## 8. Conséquences sur l'historique

- Une garantie n'est jamais écrasée : une modification ou un retrait crée un événement.
- Chaque création, modification et retrait est tracé : acteur, action, horodatage, valeurs avant et après.
- Le partenaire porteur est lu depuis la garantie et non depuis le partenaire courant du véhicule.
- L'historique des rattachements véhicule-partenaire n'est pas requis par D-PA-05. Son besoin reste à décider.

## 9. Contraintes techniques

- Aucune migration n'est écrite par ce document. Les versions V23 à V25 restent des propositions.
- Aucune entité Java n'est modifiée.
- La table `partners` (V1) existe mais n'est pas intégrée au backend. Elle n'a pas encore d'entité, de repository ni de service.
- **`partners.commission_rate` ne doit pas être utilisé.** Le modèle de commission D1 est indépendant du partenaire : taux par niveau commercial, configurable par Elite, snapshot par réservation.
- Les permissions `partners:*` et `availability:*` restent à créer.
- Le comportement réel des clés étrangères et des contraintes d'exclusion sous PostgreSQL n'est pas vérifié : aucun test PostgreSQL n'a été exécuté.

## 10. Questions qui restent ouvertes

| ID | Question | Statut |
|---|---|---|
| D-PA-14 (provisoire) | Chevauchement de garanties : refus, fusion automatique ou fusion manuelle. Ancien identifiant « D-PA-05 » dans les documents 02, 07, 09, 10, 12 et dans le tableau du document 10. | **Ouverte.** Renumérotation à confirmer par le propriétaire. |
| D-PA-01 | Durée et période de validité d'une garantie | **Ouverte, non modifiée.** |
| D-PA-04 | Traitement des réservations PENDING | **Ouverte, non modifiée.** |
| D-PA-07 | Retrait d'une garantie avec réservation confirmée | **Ouverte, non modifiée.** |
| D5 | Moment de l'acompte et cycle de paiement (et non « confirmation et paiement intégral ») | **Ouverte, non modifiée.** |
| D-PA-12 | Changement de partenaire d'un véhicule ayant des garanties ou réservations actives | **Validée** (review/08 §3) |
| Historique | Table dédiée des rattachements véhicule-partenaire | **Validée** (review/08 §4) |
| Référence réservation | Référence `bookings.availability_id` | **Validée** pour les nouvelles réservations, NULL toléré pour l'historique (review/08 §5) |

## 11. Impacts sur les futurs lots de développement

| Lot | Impact |
|---|---|
| L1 Fondations partenaires | Aucun impact sur D-PA-05. Entité `partners` sans commission. |
| L2 Rattachement véhicules | `vehicles.partner_id` devient la notion 1. Définir la règle de changement (D-PA-12) et l'historique éventuel (notion 3). |
| L3 Garanties | Table avec `vehicle_id` et `partner_id` NOT NULL, figé. Test obligatoire : changement de partenaire après création. Dépend de D-PA-01 et D-PA-14. |
| L4 Recherche | La couverture vérifie le véhicule, la période et le partenaire porteur actif. |
| L5 Réservations | Référence optionnelle à étudier. Dépend de D-PA-04 et D-PA-07. |
| L6 Isolation | Un partenaire voit ses garanties en tant que partenaire porteur, même après un changement de rattachement. |
| L7 Angular | Le partenaire porteur est affiché en lecture seule. Le client ne le voit pas. |
| L8 Tests | Ajouter un test H2 de changement de partenaire après création. Tests PostgreSQL à exécuter ailleurs, sur base isolée. |

## 12. Incohérences documentaires corrigées par cette validation

1. **Collision d'identifiant.** « D-PA-05 » désignait le chevauchement de garanties dans les documents 02, 07, 09, 10, 12 et 11 ; il désigne désormais le partenaire porteur validé. Le chevauchement devient **D-PA-14 (provisoire)**.
2. **Contrainte composite.** Le document `review/02` et `review/03` recommandaient une FK composite `(vehicle_id, partner_id) → vehicles(id, partner_id)`. Elle est rejetée (section 5).
3. **Identité partenaire et véhicule.** La formulation « le partenaire du véhicule est déduit automatiquement » ne suffisait pas. Elle est remplacée par « partenaire porteur figé à la création » (documents 02, 05).
4. **Dépendance du lot 3.** Le lot L3 ne dépend plus de D-PA-05 (validé), mais de D-PA-14.

Incohérence restante non corrigée : le document 10 utilise encore la numérotation D-PA-06 à D-PA-13 pour des sujets qui diffèrent de ceux du document `review/02` (par exemple D-PA-12, rôle PARTNER dans le document 10 et changement de propriétaire dans la revue). Elle est signalée, non renumérotée, faute de décision du propriétaire.

## 13. Fichiers concernés par cette validation

Documents mis à jour : `02-business-functional-design.md`, `03-target-architecture.md`, `04-data-model-proposal.md`, `05-api-contracts.md`, `07-admin-partner-workflow.md`, `08-security-and-concurrency.md`, `09-test-strategy.md`, `10-business-decisions-required.md`, `11-implementation-roadmap.md`, `12-executive-summary.md`, `review/01-document-consistency-audit.md`, `review/02-business-decisions-analysis.md`, `review/03-data-model-final-review.md`, `review/05-final-implementation-roadmap.md`, `review/06-owner-decision-pack.md`.

Non modifiés car sans référence à D-PA-05 : `01-current-state-audit.md`, `06-client-availability-flow.md`, `review/04-booking-and-payment-impact.md`.

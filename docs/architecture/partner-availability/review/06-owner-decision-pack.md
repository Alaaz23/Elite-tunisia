# 06 — Pack de décisions pour le propriétaire

Ce document ne contient que les décisions nécessaires avant développement. Chaque recommandation reste une proposition ; aucune n'est validée.

## Les 3 premières décisions à trancher

Mise à jour MISSION 4 : décisions validées par le propriétaire, registre canonique dans `review/08-owner-decisions-validation-record.md` §2.2. En cas de divergence avec un ancien numéro, le registre fait foi.

**Décisions validées** : D-PA-05 (partenaire porteur, Option A) ; D-PA-12 (transfert refusé en présence d'engagement actif) ; D-PA-14 (identifiant du chevauchement) ; table d'historique des rattachements ; référence obligatoire réservation → garantie pour les nouvelles réservations.

## Les 3 premières décisions à trancher

1. **D-PA-14 : règle de traitement du chevauchement.** Identifiant validé, règle à préciser. Bloque la contrainte d'exclusion.
2. **D-PA-01 : garantie à période explicite (début et fin obligatoires).** Bloque la table de garanties.
3. **D5 : moment de l'acompte.** Bloque V21 et tout le paiement. D5 n'est pas « confirmation et paiement intégral ».

## Décisions bloquantes (ordre de traitement)

| # | Décision | Recommandation | Pourquoi maintenant | Avis externe |
|---|---|---|---|---|
| 1 | D-PA-14 : règle de chevauchement | Refus à la création, fusion manuelle | Contrainte EXCLUDE | Non |
| 2 | D-PA-01 : période explicite | Oui, pas de durée illimitée | Contrainte EXCLUDE | Non |
| 3 | D5 : moment de l'acompte | Acompte à la validation, modèle de solde explicite | Bloque V21 | Comptable, prestataire de paiement |
| 4 | D-PA-07 : retrait avec réservation confirmée | Interdit si CONFIRMED couvert | Règle de sécurité | Non |
| 5 | D-PA-04 : PENDING | Ne bloque pas ; expiration décidée séparément (D10) | Concurrence | Non |
| 6 | Maintenance avec réservation future (écart C1) | Interdite si CONFIRMED futur, comme validé | Correctif de code (D-PA-16 provisoire) | Non |
| 7 | Rattachement des véhicules existants à un partenaire | Partenaire de transition, à nommer | Bloque NOT NULL | Non |
| 8 | D-PA-09 : pénalités et expiration | Expiration automatique, sans effet sur CONFIRMED | Lot L3 | Juridique, comptable |
| 9 | D-PA-17 : partenaire désactivé (provisoire) | Bloque les nouvelles garanties seulement | Lot L1 | Non |
| 10 | D-PA-12 : transfert de véhicule | **Validé** : refus si engagement actif | Lot L2 (procédure à implémenter) | Non |

## Décisions non bloquantes pour le premier lot

- D-PA-15 (type juridique du partenaire, provisoire) : énumération existante.
- D-PA-03 (modification de garantie) : autorisée avec journal.
- D-PA-08 (visibilité publique) : garanties uniquement.
- D-PA-10 (confirmation) : manuelle.
- D-PA-11 (droit de création de partenaire) : à confirmer.
- D-PA-13 (durée minimale de location) : à confirmer.
- D-PA-18 (rôle PARTNER en phase 1, provisoire) : vue lecture seule proposée.

## Décisions avec avis externe

- D-PA-09 (pénalités et conséquences financières) : juridique et comptable.
- D5 (comptable : TVA sur acompte ; prestataire : capture partielle).
- D6 (fiscalité, numérotation des factures).

## Points à confirmer avant toute action

- Le propriétaire accepte-t-il que la règle MAINTENANCE soit corrigée dans le code (écart C1, D-PA-16 provisoire) ?
- Le propriétaire accepte-t-il de nommer un partenaire de transition pour les véhicules existants ?
- Le propriétaire confirme-t-il que D5 = moment de l'acompte, et non confirmation et paiement intégral ?
- Le propriétaire valide-t-il les identifiants provisoires D-PA-15 à D-PA-18 ?
- Le propriétaire choisit-il la règle de chevauchement (D-PA-14) : refus, fusion manuelle ou automatique ?
- Le propriétaire confirme-t-il la liste exhaustive des autres engagements contractuels bloquant un transfert (D-PA-12 §3.5) et si une réservation PENDING bloque un transfert (D-PA-04) ?

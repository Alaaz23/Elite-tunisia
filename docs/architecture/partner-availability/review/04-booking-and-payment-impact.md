# 04 — Impact sur les réservations et les paiements

Statut : analyse documentaire. Aucune décision validée. Aucun code modifié.

## 1. Règles de blocage actuelles (confirmées)

- `BookingService.BLOCKING` = CONFIRMED, IN_PROGRESS (ligne 19).
- PENDING ne bloque pas. CANCELLED, REJECTED et COMPLETED ne bloquent pas.
- L'EXCLUDE de V4 applique la même règle en base.
- La confirmation (`transition(CONFIRMED)`) rappelle `requireAvailable`, qui contrôle `rentable(vehicle)` et le chevauchement.

## 2. Scénarios de réservation

| # | Scénario | Comportement actuel | Comportement attendu proposé | Statut |
|---|---|---|---|---|
| R-1 | Client A et client B demandent la même voiture | Les deux PENDING coexistent | Identique ; le premier confirmé gagne | Conforme au code |
| R-2 | Confirmation de B alors que A est déjà CONFIRMED | Refus `VEHICLE_NOT_AVAILABLE` | Identique | Conforme |
| R-3 | Garantie retirée alors qu'un PENDING existe | **Non traité.** Le contrôle de garantie n'existe pas encore. | Confirmation refusée avec message métier ; PENDING passe en REJECTED ou reste à traiter (D-PA-04) | **Risque non couvert** |
| R-4 | Garantie retirée alors qu'un CONFIRMED existe | Non traité | Retrait refusé (D-PA-07) | **À implémenter** |
| R-5 | Garantie réduite pour exclure une réservation CONFIRMED | Non traité | Refus, car la réservation doit rester couverte | **À implémenter** |
| R-6 | Réservation annulée | Libère le véhicule (CANCELLED non bloquant) | Identique | Conforme |
| R-7 | Garantie expire pendant une réservation PENDING | Non traité | PENDING non confirmable après expiration ; CONFIRMED maintenu (D-PA-09) | À décider |
| R-8 | Réservation à cheval sur deux garanties contiguës | Non traité | Refus, sauf règle contraire décidée | À décider |

## 3. Risque principal : PENDING qui survit à sa garantie

Une demande PENDING peut exister alors que la garantie a été retirée ou a expiré. Sans contrôle, la confirmation dépendra de l'ordre d'exécution. Recommandation : `requireAvailable` doit contrôler aussi la couverture par une garantie active, pas seulement le chevauchement. Ce contrôle doit être ajouté avec un test, dans le lot Intégration.

## 4. Verrouillage (ordre existant)

Ordre à respecter : Vehicle → Booking → autres entités (`booking-lock-order.md`).

- Création de garantie ou retrait : verrouiller Vehicle, puis lire les Booking bloquants sur la période.
- Confirmation de réservation : verrouiller Vehicle (existant), vérifier garantie et chevauchement, puis passer Booking.
- Le verrou Vehicle sérialise les deux opérations sur un même véhicule. **Cela suffit** pour R-4 et R-5 si le retrait passe aussi par le verrou Vehicle.

## 5. Protection contre les doubles réservations

- Niveau base : EXCLUDE sur CONFIRMED et IN_PROGRESS. **[CONFIRMÉ]**
- Niveau service : `requireAvailable` sous verrou Vehicle. **[CONFIRMÉ]**
- Niveau HTTP : mapping `DataIntegrityViolationException` non vérifié. **[À VÉRIFIER]**

## 6. Scénario D5 : acompte et solde

**Définition.** D5 = moment de l'acompte (`business-decisions-pending.md`). Ce n'est pas « confirmation et paiement intégral ».

**Conflit technique.** Un acompte suivi d'un solde crée deux paiements `CAPTURED` pour le même devis. L'invariant `PaymentService.createForQuote` (ligne 27) et les contrôles des lignes 48 et 51 l'interdisent. L'index V21 `uq_payments_one_captured_per_quote` l'interdit aussi en base.

**Exemple.** Location de 1 000 TND, acompte de 300 TND à la confirmation, solde de 700 TND sur place.

| Option | Effet sur paiement | Effet sur invariant | Risque |
|---|---|---|---|
| Paiement total | Un `CAPTURED` | Conforme | Friction client |
| Acompte différé (solde en ligne plus tard) | Deux paiements | Invariant à réviser | Solde non payé |
| Acompte à la validation (roadmap) | Deux paiements, lien au devis | Invariant à réviser | Double comptabilisation si mal modélisé |
| Paiement sur place | Aucun paiement en ligne pour le solde | Conforme pour le solde | Pas de trace électronique du solde |

**Recommandation.** Acompte à la validation, avec un modèle explicite (montant attendu, montant payé, solde). Ne pas appliquer V21 avant cette décision. **Avis externe** : comptable (TVA sur acompte), prestataire de paiement (capture partielle et remboursement partiel).

## 7. Commission et snapshot (D1)

- Le snapshot doit être figé au moment de la confirmation, dans la réservation (voir `03`, §2.4).
- Les paiements ne doivent pas porter la commission.
- Un changement de taux ne doit pas modifier les réservations passées.
- `partners.commission_rate` est une source de taux non conforme à D1 et ne doit pas être utilisée.

## 8. Dépendances avec le paiement

- `PaymentEntity` : `quote` obligatoire, `booking` nullable, pas de commission (**[CONFIRMÉ]**).
- `payments.quote_id` ON DELETE CASCADE : **risque de perte d'historique**. V22 le corrige en RESTRICT, non appliquée. À traiter avant toute suppression de devis en production.
- `legacy_payments` : contenu et migration non relus. **[À VÉRIFIER]**

## 9. Points à ne pas toucher sans décision

- Workflow de réservation (`PENDING → CONFIRMED`) : ne pas modifier.
- Système de paiement : ne pas modifier.
- Règles de commission : ne pas modifier.

## 10. Tests attendus (sans exécution dans cette mission)

- R-3 : PENDING, garantie retirée, confirmation refusée.
- R-4 : retrait refusé si CONFIRMED couvert.
- R-5 : réduction refusée si CONFIRMED hors nouvelle période.
- R-8 : réservation à cheval refusée.
- Deux confirmations concurrentes sur un même véhicule : une seule réussit (H2 pour la logique, PostgreSQL pour l'EXCLUDE, non exécuté ici).

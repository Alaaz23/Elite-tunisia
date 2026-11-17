# 02 — Analyse des décisions métier D-PA-01 à D-PA-13

> **Subordonné au registre `review/08-owner-decisions-validation-record.md` §2.2.** Les identifiants des sections ci-dessous suivent l'ancienne numérotation de la revue et ne doivent plus être utilisés. Correspondances principales : D-PA-02 (type partenaire) → D-PA-15 ; D-PA-06 (visibilité PENDING) → D-PA-04 ; D-PA-09 (expiration) → D-PA-01 ; D-PA-11 (conséquence financière) → D-PA-09 ; D-PA-12 (changement de propriétaire) → D-PA-12 (validé) ; D-PA-13 (désactivation) → D-PA-17.

Statut : **recommandations uniquement**. Aucune décision n'est validée par ce document. Les décisions restent au propriétaire du projet.

Légende priorité : **P0** bloque la conception ou une migration ; **P1** bloque une fonctionnalité client ou partenaire ; **P2** améliore le produit sans bloquer.

## Synthèse

| ID | Sujet | Priorité | Recommandation | Statut |
|---|---|---|---|---|
| D-PA-01 | Durée de validité d'une garantie | P0 | Garantie par période explicite (date début/fin), pas de durée illimitée | Non validée |
| D-PA-02 | Statut juridique du partenaire (personne physique / morale) | P1 | Champ `partner_type`, sans impact de calcul | Non validée |
| D-PA-03 | Modification d'une garantie sans réservation | P1 | Autorisée, avec journal et délai minimum | Non validée |
| D-PA-04 | Traitement des réservations PENDING | P0 | PENDING ne bloque pas, expiration à délai fixe | Non validée |
| D-PA-05 | Garantie liée au véhicule et au partenaire porteur | P0 | **Validée (Option A)** : véhicule + partenaire porteur figé à la création | **Validée** (voir 07-dpa-05-validation-record.md) |
| D-PA-06 | Visibilité de PENDING pour le partenaire | P2 | Partenaire voit ses seules réservations, sans données client complètes | Non validée |
| D-PA-07 | Retrait d'une garantie avec réservation | P0 | Interdit pour une réservation CONFIRMED | Non validée |
| D-PA-08 | Visibilité publique des véhicules | P1 | Seuls les véhicules avec garantie couvrant les dates | Non validée |
| D-PA-09 | Expiration d'une garantie | P1 | Expiration automatique, sans effet sur les réservations CONFIRMED | Non validée |
| D-PA-10 | Confirmation par admin ou automatique | P1 | Confirmation manuelle par admin dans un premier temps | Non validée |
| D-PA-11 | Conséquence financière d'un engagement non respecté | P1 | Pénalité à définir, dépend de D3 | Non validée |
| D-PA-12 | Changement de propriétaire d'un véhicule | P2 | Interdit pendant une garantie ou une réservation active | Non validée |
| D-PA-13 | Désactivation d'un partenaire | P1 | Bloque les nouvelles garanties, pas les réservations existantes | Non validée |

Ordre de résolution : D-PA-01, D-PA-07, D-PA-04, puis D5 (acompte) ; D-PA-14 (chevauchement, provisoire) à renuméroter. Les autres suivent. D-PA-05 est validé.

## D-PA-01 — Durée de validité d'une garantie (priorité P0)

**Problème.** Une garantie dit « ce véhicule est disponible du X au Y ». Sans durée explicite, elle peut rester active indéfiniment ou expirer de façon imprévisible.

**Options.**
1. Période explicite (début, fin) — chaque garantie a des dates.
2. Durée illimitée, retirée manuellement.
3. Durée fixe par défaut (ex. 30 jours), renouvelable.

**Avantages / inconvénients.**
- Option 1 : simple, auditable, compatible avec `tstzrange` et l'EXCLUDE existant. Inconvénient : demande une saisie par période.
- Option 2 : peu de saisie. Inconvénient : risque de garanties fantômes, et réservations hors contrôle.
- Option 3 : prévisible. Inconvénient : rajoute une logique de renouvellement sans besoin prouvé.

**Exemple concret.** Une Ferrari est garantie du 20 au 25 décembre. Un client cherche du 23 au 27 décembre : refus, car la garantie ne couvre pas le 26 et 27 (règle « entièrement contenue »).

**Recommandation.** Option 1. Période explicite obligatoire, pas de chevauchement pour un même véhicule.

**Impact backend.** Validation `start < end`, contrôle de chevauchement via contrainte ou service.
**Impact base.** Table de garanties avec `tstzrange` et EXCLUDE sur `(vehicle_id, période)`.
**Impact frontend.** Saisie de dates dans l'admin uniquement.
**Dépendances.** D-PA-05 (validé, Option A), D-PA-09.
**Avis externe.** Non nécessaire.

## D-PA-04 — Traitement des réservations PENDING (priorité P0)

**Problème.** Une demande PENDING ne bloque rien dans le code (`BLOCKING` = CONFIRMED, IN_PROGRESS). Deux clients peuvent demander le même véhicule ; le premier confirmé gagne. Il faut décider ce qu'un PENDING protège, et combien de temps il vit.

**Options.**
1. PENDING ne bloque pas ; confirmation re-vérifiée (comportement actuel).
2. PENDING bloque temporairement (réservation provisoire avec expiration).
3. PENDING bloque et doit être décidé sous délai fixe.

**Avantages / inconvénients.**
- Option 1 : déjà implémentée et testée. Inconvénient : un client peut payer ou attendre pour un véhicule finalement perdu.
- Option 2 : protège la demande. Inconvénient : des demandes bloquantes peuvent être abusées.
- Option 3 : équilibré. Inconvénient : nécessite un job d'expiration.

**Exemple concret.** Client A demande une Ferrari le 20 décembre à 10h. Client B demande la même voiture à 10h05. Option 1 : les deux PENDING coexistent ; si Elite confirme B en premier, A est refusé à la confirmation avec `VEHICLE_NOT_AVAILABLE`.

**Recommandation.** Option 1 pour le premier lot (cohérent avec le code), plus une date d'expiration de PENDING décidée séparément (D10). Ne pas introduire de blocage provisoire sans demande explicite.

**Impact backend.** Aucun changement immédiat ; ajouter un contrôle d'expiration si retenu.
**Impact base.** Colonne `expires_at` éventuelle sur `bookings`.
**Impact frontend.** Message clair au client en cas de refus à la confirmation.
**Dépendances.** D-PA-01, D10 (motifs, expiration).
**Avis externe.** Non.

## D-PA-05 — Garantie liée au véhicule et au partenaire porteur (priorité P0) — **VALIDÉE (Option A)**

**Statut.** Décision du propriétaire validée. Voir `07-dpa-05-validation-record.md`. Cette section est conservée pour l'historique de l'analyse.

**Problème.** Une garantie doit désigner un véhicule précis, et le partenaire qui porte l'engagement auprès d'Elite.

**Options.**
1. Garantie liée au véhicule ; partenaire déduit du véhicule.
2. Garantie liée au partenaire, sans véhicule (ex. « 2 SUV »).
3. Garantie liée aux deux, avec partenaire contrôlé.

**Décision retenue (Option A, validée).** Chaque garantie identifie simultanément le véhicule et le partenaire porteur. Le partenaire porteur est figé à la création, à partir du partenaire rattaché au véhicule à cet instant. Il ne change jamais rétroactivement.

**Correction par rapport à la version précédente de cette analyse.** La recommandation initiale proposait une clé étrangère composite `(vehicle_id, partner_id) → vehicles(id, partner_id)` « si possible ». Elle est **rejetée** : elle empêcherait de conserver le partenaire porteur d'origine après un changement de rattachement du véhicule. La cohérence se contrôle à la création (service, sous verrou véhicule) et par les tests.

**Justification.** L'option 2 empêche la réservation directe (le client choisit un véhicule). L'option 1 ne permet pas de conserver le partenaire d'origine si le véhicule change de partenaire (D-PA-12).

**Impact base.** `guaranteed_availabilities(vehicle_id, partner_id NOT NULL, period, status, ...)`, FK simples vers `vehicles(id)` et `partners(id)`. Pas de FK composite. Chevauchement : décision distincte (D-PA-14 provisoire).
**Dépendances.** D-PA-12 (changement de propriétaire), D-PA-14 (chevauchement), lot Fondations partenaires.


## D-PA-07 — Retrait d'une garantie avec réservation (priorité P0)

**Problème.** Un partenaire peut vouloir retirer un véhicule alors qu'une réservation est déjà confirmée. Le besoin validé dit non.

**Options.**
1. Retrait interdit si une réservation CONFIRMED ou IN_PROGRESS est couverte.
2. Retrait autorisé avec remplacement obligatoire par un véhicule équivalent.
3. Retrait autorisé avec pénalité (D-PA-11).

**Recommandation.** Option 1 comme règle de base. L'option 3 peut s'ajouter plus tard, sur décision du propriétaire. L'option 2 introduit un moteur de substitution qui n'existe pas.

**Exemple concret.** Partenaire veut retirer la Ferrari du 20 au 25 décembre, alors qu'un client a une réservation CONFIRMED du 22 au 24. Refus avec message métier.

**Impact backend.** Contrôle dans le service de garantie, avant toute mise à jour (verrou Vehicle puis Booking, cf. `booking-lock-order.md`).
**Impact base.** Lecture des bookings bloquants sur la période ; pas de nouvelle contrainte obligatoire.
**Impact frontend.** Message d'erreur explicite côté admin, puis côté partenaire quand il existera.
**Dépendances.** D-PA-11, D3.

## D-PA-02, D-PA-03, D-PA-06, D-PA-08 à D-PA-13 — Résumé

| ID | Problème | Options | Avantages / inconvénients | Recommandation | Impact backend / base / frontend | Dépendances | Avis externe |
|---|---|---|---|---|---|---|---|
| D-PA-02 | Type de partenaire (personne physique ou morale) | Champ libre / énumération / aucun | Énumération : simple, pas de calcul lié | Énumération `partner_type` (déjà présent en V1) | Backend : validation. Base : colonne existante. Frontend : formulaire admin | D-PA-05 | Non (juridique si mandat) |
| D-PA-03 | Modifier une garantie sans réservation | Libre / avec délai minimum / interdit | Délai : protège les clients, rigide | Autorisé avec journal et délai minimum à fixer | Backend : service + audit. Base : table d'historique. Frontend : admin | D-PA-01, D-PA-07 | Non |
| D-PA-06 | Ce que voit le partenaire d'un PENDING | Tout / ses seules réservations / rien | Limité : respect des données client | Ses seules réservations, données client minimales | Backend : filtre par partenaire. Frontend : liste partenaire (futur) | D-PA-13, isolation | Non (RGPD à vérifier si applicable) |
| D-PA-08 | Visibilité publique | Tous les véhicules / seulement garantis | Garantis seulement : fiable | Seulement les véhicules avec garantie couvrant les dates | Backend : filtre `availableDuring` étendu. Frontend : recherche | D-PA-01, D-PA-05 | Non |
| D-PA-09 | Expiration de garantie | Manuelle / automatique | Automatique : pas d'oubli | Automatique, sans effet sur CONFIRMED | Backend : job ou filtre à la lecture | D-PA-01 | Non |
| D-PA-10 | Confirmation admin ou auto | Manuelle / automatique | Manuelle : contrôle humain | Manuelle dans un premier temps | Backend : rien de nouveau. Frontend : aucun | D10, D-PA-04 | Non |
| D-PA-11 | Conséquence financière d'un engagement non respecté | Aucune / pénalité / remboursement | Pénalité : dissuasive, à calibrer | À définir avec D3 ; ne pas coder avant | Backend : écritures. Base : lignes de pénalité | D3, D5, D1 | **Oui, juridique et comptable** |
| D-PA-12 | Changement de propriétaire | Autorisé / interdit / avec transfert | Interdit pendant garantie ou réservation : sûr | Interdit tant qu'une garantie ou réservation active existe | Backend : contrôle de transfert. Base : historique du propriétaire | D-PA-05 | Non |
| D-PA-13 | Désactivation d'un partenaire | Tout bloquer / nouvelles garanties seulement | Nouvelles seulement : ne casse pas les clients | Bloque les nouvelles garanties, maintient les réservations existantes | Backend : contrôle à la création de garantie. Frontend : admin | D-PA-02, D-PA-07 | Non |

## D5 — Moment de l'acompte (priorité P0, à traiter avant V21)

**Attention.** D5 est défini dans `business-decisions-pending.md` comme « moment de l'acompte ». Plusieurs documents l'appellent à tort « confirmation et paiement intégral » (voir `01-document-consistency-audit.md`, C5).

**Problème.** Le modèle « acompte puis solde » crée deux paiements `CAPTURED` pour un même devis. Cela contredit l'invariant `PaymentService.createForQuote` et l'index unique partiel prévu par V21 (`uq_payments_one_captured_per_quote`).

**Options (documentées dans le registre).**
1. Paiement total.
2. Acompte différé.
3. Acompte à la validation (recommandé par la roadmap).
4. Paiement sur place (option du benchmark, non retenue dans le registre ; à trancher par le propriétaire).

**Exemple concret.** Location de 1 000 TND. Option 3 : 300 TND à la confirmation, 700 TND sur place. Il faut alors soit assouplir l'invariant « un seul CAPTURED », soit modéliser deux paiements distincts par devis.

**Recommandation.** Option 3, avec une modélisation explicite du solde (et non deux `CAPTURED` sur le même devis sans règle). Cette décision bloque V21 et tout le lot de paiement. **Ne pas appliquer V21 avant décision.**

**Impact backend.** `PaymentService` et son invariant.
**Impact base.** V21 et son index unique, à réviser.
**Impact frontend.** Parcours de paiement.
**Avis externe.** Comptable (TVA sur acompte) et prestataire de paiement (capture partielle).

## Décisions à prendre immédiatement (sans avis externe)

D-PA-01, D-PA-04, D-PA-07, D-PA-09, D-PA-10, D-PA-12, D-PA-13, D-PA-14 (ex-chevauchement).

## Décisions nécessitant un avis externe

D-PA-11 (juridique, comptable), D5 (comptable et prestataire de paiement), D6 (fiscalité).

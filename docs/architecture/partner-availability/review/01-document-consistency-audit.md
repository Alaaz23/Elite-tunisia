# 01 — Audit de cohérence documentaire (partner-availability)

Statut : revue documentaire. Aucun code, migration ni configuration modifié. Aucun test exécuté.
Périmètre : documents `01` à `12` de `docs/architecture/partner-availability/`, confrontés à `booking-lock-order.md`, `business-decisions-pending.md`, `implementation-authorization-matrix.md`, `business-decision-analysis.md`, `vehicle-lifecycle-protection-report.md`, `partner-vehicle-availability-analysis.md`, et au code réel.

Légende : **[CONFIRMÉ]** vérifié dans le code ou les migrations ; **[À VÉRIFIER]** non vérifié dans cet environnement.

## 1. Contradictions détectées

| # | Document / section | Règle décrite | Contradiction | Risque | Correction recommandée |
|---|---|---|---|---|---|
| C1 | 07 §5, 10, 01 §5, 12 | MAINTENANCE interdite si réservation future CONFIRMED (décision validée) | **[CONFIRMÉ]** `VehicleService.requireStatusChangeAllowed` bloque MAINTENANCE seulement si une réservation IN_PROGRESS existe. UNAVAILABLE est bloqué si IN_PROGRESS ou CONFIRMED se terminant après maintenant. Le Javadoc contredit aussi la règle validée. | Un véhicule peut passer en maintenance alors qu'une réservation future est confirmée. Le client est exposé à une annulation de fait. | Corriger le code (lot dédié, sur accord explicite). Docs 01 §5 et 12 : retirer l'affirmation « protection complète ». |
| C2 | 03 §2 vs 03 §3 | `booking` dépend de `availability`, sans cycle | **[CONFIRMÉ]** 03 §3 fait interroger `BookingRepository` (module booking) par `GuaranteedAvailabilityService`. Cela crée un cycle `availability` ↔ `booking`. | Violation du monolithe modulaire, dépendances circulaires. | Séparer : `availability` expose une lecture des garanties ; `booking` consomme. Ou placer la vérification de couverture dans `booking`. |
| C3 | 04 §5 vs 11 | V23 dépend de D-PA-01 | 11 sépare le lot 1 (partners, dépend de D-PA-02) et le lot 2 (`vehicles.partner_id`, dépend d'autres décisions). 04 fusionne les deux dans V23 et rattache D-PA-01 (durée de garantie), sans lien avec la structure partenaire. | Migration bloquée à tort, ou lancée avant la décision réellement nécessaire. | Scinder en deux migrations, chacune rattachée à sa décision réelle. |
| C4 | 05 (listing public `GET /api/vehicles`) | Visibilité publique | 05 rattache ce point à D-PA-06 (PENDING). La visibilité publique relève de D-PA-08 (ou d'une règle de visibilité), pas du traitement PENDING. | Décision mal ciblée, lot de recherche publique bloqué inutilement. | Rattacher à D-PA-08. |
| C5 | 06, 10 (D-PA-10), 12 | « D5 = confirmation et paiement intégral » | **[CONFIRMÉ]** `business-decisions-pending.md` définit **D5 = « Moment de l'acompte »** (paiement total ou acompte, et moment). La confirmation n'est pas dans D5. | Décision mal nommée, risque de trancher la mauvaise question. | Renommer dans 06, 10, 12 : D5 = moment de l'acompte. Traiter la confirmation séparément. |
| C6 | 02 §5, 05, 07 §3 | Réservation « couverte » par une garantie | 07 dit « couverte », 05 dit « bloquante », 02 dit « toute réservation touchant la garantie ». Le recouvrement partiel et le rétrécissement d'une période ne sont pas tranchés. | Acceptation de réservations partiellement hors garantie. | Règle unique : une réservation doit être entièrement contenue dans une garantie active (voir `02`, D-PA-01). |
| C7 | 04 §2.1 | `vehicles.partner_id` NOT NULL « pour les nouveaux véhicules » | SQL ne sait pas exprimer « NOT NULL pour les nouveaux seulement » sans contrainte transitoire. Le texte ne dit pas comment traiter les véhicules existants. | Migration impossible en l'état, ou véhicules historiques sans partenaire. | Migration en trois temps : colonne nullable, rattachement à un partenaire de transition validé par le propriétaire, puis NOT NULL. Décision requise. |
| C8 | 10, 01 | Réservation PENDING | Aucun document ne traite le cas : garantie retirée pendant qu'une demande PENDING existe. | Confirmation qui échoue ou réussit de façon incohérente. | Voir `04-booking-and-payment-impact.md`, scénario R-3. |
| C9 | 08 §7 | `DataIntegrityViolationException` gérée | Marqué « à vérifier » dans le document lui-même. **[À VÉRIFIER]** | Doublon non géré en concurrence (risque de 500). | Vérifier le handler avant de s'appuyer sur ce point. |
| C10 | 10 (D-PA-03, D-PA-07) | Recommandations « Non » | Ne référencent pas l'effet réel de D-PA-07 sur le cycle de vie du véhicule (retrait de garantie). | Décision prise sans mesurer ses effets. | Lier D-PA-03 et D-PA-07 explicitement. |
| C11 | 03 / 04 | Snapshot de commission (D1) | D1 exige un snapshot par réservation. Aucun document ne dit où il est stocké. `payments` n'a ni commission ni snapshot (**[CONFIRMÉ]** V6, `PaymentEntity`). `partners.commission_rate` existe encore (V1) et contredit D1. | Commission recalculée au lieu d'être figée. | Ajouter un snapshot dans le modèle cible (voir `03`). Ne pas réutiliser `partners.commission_rate`. |
| C12 | 01 §5, 12 | Protections du cycle de vie « complètes » | Ignorent l'écart C1. | Fausse assurance. | Corriger le texte. |
| C13 | 06 | Décisions « nécessaires avant développement » | Certaines listées comme bloquantes ne le sont pas (ex. D-PA-13 pour un lot d'isolation). | Attente inutile. | Voir `06-owner-decision-pack.md`. |

## 2. Vérifications de cohérence avec le code

| Point | Résultat | Statut |
|---|---|---|
| `BookingService.BLOCKING` = CONFIRMED, IN_PROGRESS | Confirmé ligne 19. PENDING ne bloque pas. | [CONFIRMÉ] |
| `requireAvailable` vérifie `rentable(vehicle)` et chevauchement | Confirmé lignes 196-197 | [CONFIRMÉ] |
| Re-contrôle au passage CONFIRMED | `transition(CONFIRMED)` appelle `requireAvailable` (~ligne 138). Pas de contrôle de garantie (n'existe pas encore). | [CONFIRMÉ] |
| EXCLUDE gist sur `(vehicle_id, tstzrange(starts_at, ends_at,'[)'))` WHERE status IN CONFIRMED, IN_PROGRESS | Confirmé V4 lignes 25-28. Cohérent avec BLOCKING. | [CONFIRMÉ] |
| FK `bookings.vehicle_id` sans ON DELETE | Confirmé V1 ligne 106 (NO ACTION). `VehicleService.delete` refuse avec `VEHICLE_HAS_BOOKINGS`. | [CONFIRMÉ] (comportement PostgreSQL non testé) |
| Permission `vehicles:manage` | Confirmé : `AdminVehicleController` lignes 42+ et `ImageUploadController`. | [CONFIRMÉ] |
| Permission `bookings:manage` pour la confirmation | Confirmé : `AdminBookingController` ligne 22 (`PATCH /{id}/confirm`). | [CONFIRMÉ] |
| Route `/api/partner/**` | N'existe pas (`elite-business-model-proposal.md`, ligne 297). | [CONFIRMÉ] |
| Migrations | `database/migrations/` V1 à V20. `database/proposed/` V21, V22 non appliquées. 04 propose V23 à V25. | [CONFIRMÉ] |
| Divergence `backend/src/main/resources/db/migration/` | Contient seulement V11 et des fichiers `_DISABLED_`. Cause non identifiée. | [À VÉRIFIER] |

## 3. Réponses aux questions de cohérence

- **Une garantie est-elle liée à un véhicule et une période ?** Oui dans 03 et 04. Conforme à D-PA-05 validé (Option A) : la garantie est aussi liée au partenaire porteur, figé à la création (voir `07-dpa-05-validation-record.md`).
- **Modifiable ? Retirable ?** Non tranché. 02 autorise la modification sans règle de délai ; 07 la limite. Voir D-PA-01 et D-PA-07.
- **Deux garanties qui se chevauchent ?** Non traité explicitement. Recommandation : interdire le chevauchement pour un même véhicule (contrainte EXCLUDE sur `tstzrange`, cohérente avec V4).
- **Deux périodes successives ?** Non traité. Une réservation à cheval sur deux garanties contiguës doit être refusée, sauf règle contraire décidée.
- **Empêcher une réservation hors garantie ?** Décrit comme intention. Aucun contrôle codé. À ajouter dans `BookingService.requireAvailable` ou une vérification dédiée, avec test.
- **Historique ?** Décrit comme « à conserver ». Pas de table ni de règle de suppression définies.
- **Quelle réservation bloque ?** CONFIRMED et IN_PROGRESS, conformément au code. PENDING ne bloque pas. Cohérent.
- **Réservation annulée ?** Non bloquante (CANCELLED hors BLOCKING). Cohérent.
- **Double réservation ?** Protégée en base par EXCLUDE pour CONFIRMED et IN_PROGRESS. Une demande PENDING peut toutefois être confirmée après une autre confirmation : le re-contrôle de `transition` le bloque, car il passe par `requireAvailable`. **[CONFIRMÉ]**
- **Protéger une réservation confirmée contre une modification de garantie ?** Non traité. Règle recommandée : une garantie couvrant une réservation CONFIRMED ne peut pas être retirée ni réduite (voir D-PA-07).

## 4. Points conformes aux décisions validées

- Maintenance : écart documenté (C1). Non conforme au code.
- Suppression définitive interdite si historique : conforme (`VEHICLE_HAS_BOOKINGS` + FK NO ACTION).
- UNAVAILABLE temporaire et distinct de l'archivage : cohérent dans 07 et 10.
- Commission par niveau commercial : pas de commission globale par partenaire réintroduite dans 06 et 12. **Mais** `partners.commission_rate` subsiste dans V1 (C11).
- Isolation partenaire : décrite comme cible, non implémentée (pas de `partner_id` sur `vehicles`, pas de route `/api/partner/**`).

## 5. Corrections documentaires à appliquer (non effectuées dans cette mission)

Les corrections C2, C4, C5 et C12 doivent être reportées dans les documents `04`, `05`, `06`, `10` et `12`. Cette mission reste en lecture seule : les corrections sont listées, pas appliquées.

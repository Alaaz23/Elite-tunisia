# Analyse — Gestion des disponibilités des véhicules partenaires

**Statut :** analyse documentaire uniquement. Aucun fichier source, migration, configuration ou test modifié.
**Base analysée :** HEAD `78da39b`, avec les modifications locales préexistantes (non suivies et suivies) préservées.

---

## 1. Résumé de la décision métier validée

- Elite ne possède aucun véhicule ; les véhicules appartiennent aux partenaires.
- Le client choisit ses dates. Elite interroge ses partenaires, qui confirment les véhicules qu'ils peuvent **garantir** sur la période.
- Un administrateur Elite **enregistre** ces disponibilités dans la plateforme.
- Le client ne voit que les véhicules enregistrés comme disponibles pour ses dates, et réserve directement.
- Aucune validation partenaire n'est demandée à la réservation.
- **Règle essentielle :** une disponibilité enregistrée comme garantie engage le partenaire. Une réservation confirmée ne peut pas être annulée parce que le partenaire veut retirer le véhicule.

Conséquence : la disponibilité est un **engagement saisi en amont par Elite**, et non un calcul automatique à partir des réservations.

---

## 2. État actuel de l'implémentation

### 2.1 Modèle véhicule et statut

- [VehicleStatus.java](c:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/vehicle/VehicleStatus.java) : l'enum contient `AVAILABLE`, `RESERVED`, `RENTED`, `MAINTENANCE` (`RESERVED` n'est jamais posé par le backend, décision en attente).
- `VehicleStatus` est un **statut global** : il ne porte aucune période. Il ne peut donc pas représenter « disponible du 20 au 25 décembre ».
- Aucune entité `partner` n'existe : le package [partner/package-info.java](c:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/partner/package-info.java) est vide. `VehicleEntity` ne porte **aucune référence au partenaire propriétaire** (recherche `partner|Partner` négative dans `backend/src/main/java`).

### 2.2 Calcul actuel de la disponibilité

Il repose uniquement sur les **réservations** et le statut global :

- [BookingService.java](c:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/booking/BookingService.java) (lignes 50–56) : `availability` refuse si `rentable(vehicle)` est faux, puis vérifie `bookings.overlaps(..., BLOCKING, null)`.
- `rentable` (ligne 193) exige `VehicleStatus.AVAILABLE`, une marque active et une catégorie active.
- `BLOCKING = CONFIRMED, IN_PROGRESS` : **une réservation `PENDING` ne bloque pas** le véhicule.
- [VehicleSpecification.java](c:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/vehicle/VehicleSpecification.java) (`availableDuring`) : exclut les véhicules ayant une réservation bloquante qui chevauche `[start, end[`. Le chevauchement partiel est correctement détecté (`start < end` et `end > start`).
- [PublicVehicleController.java](c:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/vehicle/PublicVehicleController.java) (`/available`) appelle `VehicleService.availableSearch`, qui utilise cette spécification.
- La réservation (`create`, ligne ~78) verrouille le véhicule (`vehicle(id, true)`, `findLocked`), puis rejoue `requireAvailable`.
- Contrainte PostgreSQL V4 : `bookings_no_blocking_overlap` (EXCLUDE gist) empêche le chevauchement de réservations bloquantes au niveau base.

### 2.3 Ce qui n'existe pas

- Aucune table ni entité de **disponibilité enregistrée par Elite**.
- Aucune notion de **partenaire garant**, de confirmation partenaire, de date de confirmation, de période de validité ni d'expiration de disponibilité.
- Aucune traçabilité des modifications de disponibilité.
- Aucun parcours administrateur d'enregistrement de disponibilité (à confirmer côté Angular : non examiné en détail dans cette analyse).

---

## 3. Écarts entre l'existant et le besoin

| # | Besoin validé | Existant | Écart |
|---|---|---|---|
| E1 | Le client ne voit que les véhicules **garantis** pour ses dates | Le client voit tout véhicule `AVAILABLE` sans réservation bloquante | **Modèle inversé** : aujourd'hui « disponible sauf si réservé », demain « disponible seulement si garanti » |
| E2 | Disponibilité par véhicule et par période, saisie par Elite | Absente | Nouveau modèle nécessaire |
| E3 | Partenaire associé au véhicule | Absent | Aucune relation véhicule → partenaire |
| E4 | Traçabilité (qui a saisi, quand, confirmation partenaire) | Absente | Audit à concevoir |
| E5 | Engagement : une réservation confirmée ne doit pas être annulée pour retrait du véhicule | Aucun contrôle de retrait lié aux réservations (à vérifier dans le service de modification du statut véhicule) | **Non vérifié** ; probablement absent |
| E6 | Expiration d'une disponibilité | Absente | Non modélisée |
| E7 | Chevauchement partiel : la période client doit être **couverte** | La spécification gère le chevauchement de réservations, pas la couverture par une garantie | Nouvelle règle : `garantie.start <= start` et `garantie.end >= end` |

**Constat important :** une disponibilité enregistrée ne peut **pas** se déduire du statut `VehicleStatus` ni des réservations. Un nouveau modèle est justifié (voir §5).

---

## 4. Proposition d'architecture cible

Principe : conserver la chaîne existante (`VehicleSpecification` → `BookingService` → `EXCLUDE`) et y ajouter une **condition de garantie**, sans réécrire le moteur de réservation.

1. **Couverture** : un véhicule est proposé pour `[start, end]` si :
   - `VehicleStatus = AVAILABLE` (marque et catégorie actives, inchangé) ;
   - il existe une disponibilité **active** couvrant `[start, end]` ;
   - aucune réservation `CONFIRMED` / `IN_PROGRESS` ne chevauche (inchangé).
2. **Réservation** : `create` re-vérifie les trois conditions sous verrou véhicule (ordre Vehicle → Booking inchangé).
3. **Concurrence** : la contrainte EXCLUDE reste la dernière barrière.
4. **Retrait / modification** d'une garantie : ne peut pas réduire une période déjà couverte par une réservation bloquante.

---

## 5. Modèle de données recommandé

Le besoin ne peut pas être satisfait par l'existant : une **nouvelle table est justifiée**, mais elle doit être validée avant toute migration.

Proposition `vehicle_availability` (nom indicatif) :

| Colonne | Rôle |
|---|---|
| `id` | UUID |
| `vehicle_id` | FK véhicule |
| `partner_id` | Partenaire garant (à créer : entité `partner`) |
| `start_at`, `end_at` | Période garantie, `[start, end[` |
| `status` | `PROPOSED`, `CONFIRMED_BY_PARTNER`, `WITHDRAWN`, `EXPIRED` (à décider, voir §11) |
| `recorded_by` | Administrateur Elite |
| `partner_confirmed_at` | Date de confirmation partenaire |
| `created_at`, `updated_at` | Traçabilité |

Contraintes recommandées :

- `CHECK (end_at > start_at)`.
- EXCLUDE gist sur `(vehicle_id, tstzrange(start_at, end_at))` **pour les disponibilités actives** : évite les garanties contradictoires sur un même véhicule (à confirmer selon la règle de fusion, §11).
- Index sur `(vehicle_id, start_at, end_at)`.

Alternative minimale : réutiliser la table `bookings` avec un type « blocage partenaire ». **Déconseillée** : elle mélange engagement et réservation client, et compliquerait l'historique financier.

Table `partners` : nécessaire pour E3 ; nom de l'entité et périmètre à valider.

---

## 6. Parcours administrateur Elite

1. Recherche des partenaires et de leurs véhicules.
2. Saisie d'une disponibilité : véhicule, période, partenaire garant.
3. Enregistrement avec statut `CONFIRMED_BY_PARTNER` (si la confirmation est reçue hors plateforme) ou `PROPOSED`.
4. Consultation de l'historique des garanties d'un véhicule.
5. Modification d'une garantie : seulement si aucune réservation bloquante ne sera affectée (sinon refus avec motif).
6. Retrait : soumis à la décision sur les conditions partenaire (§11).

Contrôle d'accès : permission admin dédiée (à créer), distincte de la gestion du catalogue.

---

## 7. Parcours client

1. Sélection des dates.
2. Recherche : seuls les véhicules `AVAILABLE` **et garantis** sur `[start, end]` et sans réservation bloquante sont retournés.
3. Choix direct du véhicule (sans demande au partenaire).
4. Prévisualisation : `preview` revérifie la disponibilité (déjà présent).
5. Réservation : `create` revérifie sous verrou. En cas de conflit concurrent : erreur `VEHICLE_NOT_AVAILABLE` (déjà présente).
6. Le client ne voit jamais l'identité du partenaire garant ni les conditions internes.

---

## 8. Parcours partenaire

Le partenaire **ne se connecte pas** à la disponibilité dans le modèle validé : il communique à Elite. Le parcours partenaire se limite donc à :

1. Confirmer ou refuser une demande de disponibilité transmise par Elite (hors plateforme, à défaut de portail partenaire).
2. Être informé des retraits ou modifications (canal à décider, D14).
3. Aucun accès direct aux réservations clients (isolation des partenaires à conserver).

Le portail partenaire n'existe pas et n'est pas requis par la décision validée.

---

## 9. Scénarios de réservation et de concurrence

| # | Scénario | Comportement attendu | Comportement actuel | Risque |
|---|---|---|---|---|
| 1 | Partenaire confirme une Ferrari du 20 au 25 déc. | Elite enregistre une garantie couvrant `[20, 25[` | Aucune garantie enregistrable | Bloquant pour le modèle |
| 2 | Elite enregistre la disponibilité | Garantie `CONFIRMED_BY_PARTNER` créée, traçée | Impossible | Bloquant |
| 3 | Client recherche la Ferrari pour cette période | Visible | Visible (toute période, sans garantie) | Fausse disponibilité si la garantie n'est pas exigée |
| 4 | Véhicule apparaît dans les résultats | Oui, seulement si garanti | Oui, sans condition de garantie | E1 |
| 5 | Client réserve directement | Réservation `PENDING` ou confirmée selon le flux | Possible | — |
| 6 | Deux clients réservent simultanément | Un seul succès, l'autre `VEHICLE_NOT_AVAILABLE` | **Protégé** : verrou véhicule + EXCLUDE V4 | Erreur SQLSTATE 23P01 non mappée vers un message métier (à vérifier) |
| 7 | Modification d'une garantie sans réservation | Autorisée, traçée | Impossible (pas de modèle) | — |
| 8 | Retrait d'une garantie avec réservation confirmée | **Refusé** : engagement | Non vérifié ; aucune règle connue | Risque élevé si le retrait est possible |
| 9 | Garantie expirée | Non proposée | Pas d'expiration | Garanties fantômes si expiration non gérée |
| 10 | Recherche partiellement chevauchante | Refusée : `[start, end]` doit être couvert | Chevauchement de réservation détecté, pas de couverture | Mauvaise règle si on réutilise le chevauchement |

**Concurrence sur la garantie elle-même :** deux administrateurs peuvent saisir des garanties contradictoires. Protection recommandée : EXCLUDE sur les garanties actives ou verrou sur le véhicule lors de la saisie.

**Point à vérifier :** un booking `PENDING` ne bloque pas. Deux `PENDING` peuvent donc coexister sur les mêmes dates ; la confirmation rejoue `requireAvailable` (ligne ~138). À confirmer dans le code de transition.

---

## 10. Risques métier et techniques

| Risque | Gravité | Commentaire |
|---|---|---|
| Client réserve un véhicule non garanti | Élevée | Cause : modèle inversé (E1) |
| Retrait d'un véhicule déjà réservé (E5) | Élevée | Engagement non protégé ; conséquence financière (§11) |
| Garanties contradictoires saisies par Elite | Moyenne | Contrainte à prévoir |
| Garantie expirée encore visible | Moyenne | Job ou filtre temporel |
| Mauvais calcul de couverture | Élevée | Tests de bornes obligatoires |
| Erreur 23P01 non traduite | Faible à moyenne | Message technique au client |
| Fuite de l'identité du partenaire | Élevée | Isolation à garantir dans les DTO publics |
| Migration sur données historiques | Moyenne | Pas de données de garantie existantes : migration additive possible, à valider |
| Dépendance à la contrainte D5 (paiement) | Moyenne | Confirmation et paiement intégral non figés |

---

## 11. Décisions encore nécessaires

Non tranchées ici. Recommandations proposées.

1. **Durée de garantie** : durée maximale d'une garantie non confirmée (ex. 48 h avant la date de début) puis expiration. *Recommandation :* garantie avec date d'expiration explicite saisie par Elite.
2. **Modification avant réservation** : autorisée, mais tracée et soumise à validation Elite. *Recommandation :* autorisée jusqu'à la première réservation sur la période.
3. **Délai et conséquences d'une modification tardive** : *recommandation :* délai à définir par Elite ; pas de pénalité automatique sans grille validée (lié à D3).
4. **Conditions d'annulation par le partenaire** : *recommandation :* interdiction après réservation confirmée ; exceptions (force majeure, accident) à définir avec un juriste.
5. **Conséquences financières d'un engagement non respecté** : *recommandation :* pénalité ou remboursement intégral au client, à chiffrer. Décision propriétaire et avis juridique (D3, D7).
6. **Traitement d'une réservation PENDING** : *recommandation :* une réservation `PENDING` doit verrouiller la période pendant un délai borné, sinon elle expire (lié à D10).
7. **Moment de confirmation et paiement intégral** : D5 reste ouvert. Ne pas finaliser le flux de confirmation avant D5 (V21 dépend de cette décision).
8. **Règle de fusion** des garanties qui se chevauchent : fusion automatique ou refus ? *Recommandation :* refus explicite pour éviter les erreurs de saisie.
9. **Partenaire sans portail** : confirmation hors plateforme acceptée ? *Recommandation :* oui pour la phase 1, avec traçabilité par l'administrateur.

---

## 12. Plan d'implémentation par lots

Chaque lot nécessite une **autorisation explicite** avant toute modification de code ou de base.

| Lot | Contenu | Dépendances | Migration |
|---|---|---|---|
| A0 | Décisions §11 (1, 2, 8 en priorité) | Propriétaire | Non |
| A1 | Entité `partner` (minimale) et relation véhicule → partenaire | A0 | Oui (autorisation) |
| A2 | Table `vehicle_availability` + entité, repository, contrainte EXCLUDE | A1 | Oui (autorisation) |
| A3 | Service d'enregistrement admin (saisie, modification, retrait), permission dédiée, audit | A2 | Non |
| A4 | Condition de couverture dans `VehicleSpecification.availableDuring` et `BookingService.availability` | A2 | Non |
| A5 | Revérification de couverture dans `create`, `preview`, confirmation | A4 | Non |
| A6 | Règle de retrait : refus si réservation bloquante | A3, A5 et décision §11.4 | Non |
| A7 | Expiration des garanties (filtre temporel ou job) | A2 et décision §11.1 | Non |
| A8 | Frontend : admin (saisie) et client (résultats filtrés) | A3, A4 | Non |
| A9 | Mapping 23P01 vers message métier | Indépendant | Non |

---

## 13. Tests nécessaires par lot

| Lot | Tests |
|---|---|
| A1 | Unit : relation partenaire obligatoire. Isolation : un partenaire ne voit pas les réservations d'un autre. |
| A2 | Unit : `end > start`. Intégration PostgreSQL isolée (non exécutée ici) : EXCLUDE sur garanties actives. |
| A3 | MockMvc : permission admin requise, 403 pour client et partenaire, audit créé. |
| A4 | Unit : couverture exacte, bornes `[start, end[`, chevauchement partiel refusé, garantie expirée exclue. Test H2 compatible. |
| A5 | Concurrence : deux réservations simultanées, une seule réussit (PostgreSQL isolé, comme `BookingLockOrderPostgresIT`). |
| A6 | Unit : retrait refusé si réservation confirmée ; retrait autorisé sans réservation. |
| A7 | Unit : expiration calculée à partir de l'horloge injectée. |
| A8 | Angular : contrat API, filtrage, états vides. **Non exécutable** (Node 18.20.8 insuffisant). |
| A9 | MockMvc : code SQLSTATE 23P01 converti en `VEHICLE_NOT_AVAILABLE`. |

Validation : JDK 21, `.\mvnw.cmd -B -ntp -DskipITs test`, lecture de `$LASTEXITCODE` dans le même appel. Aucun test PostgreSQL sans autorisation.

---

## 14. Recommandation sur la prochaine étape

1. **Ne pas implémenter** tant que les décisions §11.1, 11.2, 11.4 et 11.8 ne sont pas tranchées.
2. Obtenir votre approbation explicite pour le lot A0 (décisions) puis, séparément, pour la migration A1–A2.
3. Traiter en priorité E1 (client voit des véhicules non garantis) : c'est le risque commercial le plus élevé.
4. Vérifier E5 (retrait de véhicule avec réservation confirmée) dans le service de modification du statut véhicule avant toute décision, car c'est probablement une régression possible déjà existante.

**Points non vérifiés dans cette analyse :** le flux de modification du statut véhicule (E5), la transition `PENDING` → `CONFIRMED` (§9), le frontend admin et client, et le mapping 23P01.

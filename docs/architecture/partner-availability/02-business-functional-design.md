# 02 — Spécification fonctionnelle : partenaires et disponibilités garanties

## 1. Principe validé

- Elite ne possède aucun véhicule. Les véhicules appartiennent à des partenaires.
- Un administrateur Elite contacte un partenaire, qui confirme les véhicules disponibles sur une période.
- L'administrateur **enregistre** cette confirmation : elle devient une **disponibilité garantie par Elite** pour la période enregistrée.
- Le client ne voit que les véhicules garantis pour **toute** sa période, puis réserve directement, sans nouvelle validation partenaire.
- Une réservation confirmée ne peut pas être annulée parce que le partenaire veut retirer le véhicule.

## 2. Objets métier

| Objet | Rôle |
|---|---|
| Partenaire | Propriétaire de véhicules. Statut actif ou inactif. Coordonnées et informations légales. |
| Véhicule | Rattaché à exactement un partenaire. Statut opérationnel (`AVAILABLE`, `MAINTENANCE`, `UNAVAILABLE`, `RESERVED`, `RENTED`). |
| Disponibilité garantie | Engagement enregistré : véhicule, partenaire, période `[début, fin[`, date de confirmation, administrateur, statut, traçabilité. |
| Réservation | Inchangée dans son workflow. Doit être contenue dans une disponibilité garantie. |

## 3. Fonctions

### 3.1 Partenaires (administration Elite)
- Créer, modifier, activer, désactiver un partenaire.
- Renseigner informations légales, commerciales, coordonnées et contacts.
- Consulter l'historique des opérations sur le partenaire.
- Un partenaire désactivé n'apparaît plus dans le catalogue public et ne peut recevoir de nouvelle garantie.

### 3.2 Rattachement véhicule-partenaire
- Chaque nouveau véhicule est rattaché à un partenaire (obligatoire pour les nouveaux véhicules).
- Les véhicules existants sans partenaire restent **non publiables** tant qu'Elite ne les a pas rattachés. Aucune valeur fictive n'est créée.
- Changement de propriétaire : décision ouverte (voir 10-business-decisions-required.md), recommandation : interdit sans procédure dédiée et traçée.

### 3.3 Disponibilités garanties (administration Elite)
- Créer une disponibilité : choisir véhicule, période, date de confirmation.
- **Décision validée D-PA-05 (Option A)** : chaque disponibilité garantie identifie simultanément le véhicule et le **partenaire porteur** de l'engagement (voir `review/07-dpa-05-validation-record.md`).
- Le partenaire porteur n'est jamais saisi librement par le client ni par l'administrateur : il est déterminé à la création à partir du partenaire **actuellement rattaché** au véhicule, vérifié (actif, rattachement effectif), puis **figé** (snapshot) dans la garantie.
- Un changement ultérieur de partenaire du véhicule ne modifie jamais le partenaire porteur des garanties existantes.
- Consulter les disponibilités par véhicule, par partenaire porteur, par période.
- Modifier une disponibilité **sans réservation** qui la couvre.
- Retirer une disponibilité **sans réservation** qui la couvre.
- Une disponibilité qui chevauche une autre disponibilité du même véhicule : voir §6.

### 3.4 Recherche client
1. Le client saisit date de début et date de fin.
2. Filtres optionnels : catégorie, marque.
3. Le résultat ne contient que les véhicules qui satisfont **toutes** les conditions :
   - véhicule publié et au statut commercial actif ;
   - partenaire actif ;
   - disponibilité garantie couvrant intégralement `[début, fin[` ;
   - aucune réservation bloquante qui chevauche ;
   - règles de réservation existantes respectées (durée minimale, délai, etc.).
4. Le client consulte le détail, voit le prix calculé et réserve directement.

### 3.5 Réservation
- La réservation est refusée si la période n'est pas couverte par une disponibilité garantie.
- La vérification est refaite à la création **et** à la confirmation (voir 08-security-and-concurrency.md).
- Le workflow existant (PENDING, CONFIRMED, etc.) n'est pas modifié par cette spécification.

## 4. Cas de recherche

| Cas | Comportement attendu |
|---|---|
| Garantie couvrant exactement la période | Visible. |
| Garantie plus longue que la période | Visible. |
| Garantie partielle (ne couvre qu'une partie) | **Non visible.** |
| Deux garanties successives (ex. 1–5 et 6–10) pour une demande du 3 au 8 | **Non visible.** Aucune fusion automatique. |
| Deux garanties qui se chevauchent | Visible si l'union couvre la période **et** si la règle de fusion (D-PA-14, identifiant validé ; règle à préciser) est validée. Par défaut : traité comme une seule garantie seulement si fusionnée explicitement. |
| Réservation existante qui chevauche | Non visible. |
| Réservation annulée | Ne bloque pas. Visible si la garantie couvre la période. |
| Véhicule en maintenance | Non visible. |
| Véhicule `UNAVAILABLE` | Non visible. |
| Véhicule archivé | Non visible. |
| Partenaire désactivé | Non visible. |

## 5. Règles de modification et de retrait

- Modification ou retrait d'une garantie **sans réservation confirmée** : autorisé pour Elite, tracé.
- Modification ou retrait d'une garantie **couverte par une réservation confirmée ou en cours** : **interdit**. La garantie reste engagée.
- Le partenaire ne modifie jamais une garantie lui-même.
- Conséquences financières d'un retrait : **décision ouverte** (D-PA-07 et D-PA-09).

## 6. Périodes qui se chevauchent

Recommandation par défaut (sans validation) : **interdire le chevauchement** de deux garanties actives sur un même véhicule et les obliger à être fusionnées ou corrigées par l'administrateur. Cela évite les ambiguïtés de statut et simplifie la contrainte d'exclusion en base.

## 7. Décisions requises

Voir [10-business-decisions-required.md](./10-business-decisions-required.md). Points bloquants pour le développement : durée de validité, modification avant réservation, retrait, fusion des périodes.

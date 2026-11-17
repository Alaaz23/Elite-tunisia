# 10 — Propositions de décisions métier (Mission 6)

> **Statut de ce document : propositions et recommandations uniquement.**
> Aucune décision listée ici n'est validée. Seules les décisions marquées **VALIDÉE** dans `review/08-owner-decisions-validation-record.md` §1 et §3–§5 le sont. Toute recommandation doit être approuvée explicitement par le propriétaire avant d'entrer dans le périmètre d'implémentation.
>
> Identifiants : registre `review/08` §2.2. Aucun identifiant n'est renuméroté. Le contrôle des identifiants est documenté dans `review/09`.

## 0. Décisions déjà validées (référence, non rediscutées)

| ID | Sujet | Source | Portée |
|---|---|---|---|
| D-PA-05 | Partenaire porteur figé dans chaque garantie (Option A) | review/07, review/08 §1 | Modèle de garantie |
| D-PA-12 | Transfert refusé si engagement actif (principe) | review/08 §3 | Transfert de véhicule |
| D-PA-14 (identifiant) | Chevauchement des garanties : identifiant officiel | review/08 §2.2 | Nommage uniquement |
| Historique des rattachements | Table dédiée `vehicle_partner_assignments` | review/08 §4 | Modèle |
| Référence booking → garantie | Obligatoire pour toute nouvelle réservation garantie ; NULL pour l'historique | review/08 §5 | Modèle et contrôle backend |

Toute décision ci-dessous qui dépend de ces points s'appuie sur ces règles validées, sans les modifier.

## 1. Vue d'ensemble des décisions ouvertes

| Ordre | ID | Intitulé (registre 08) | Bloque | Statut |
|---|---|---|---|---|
| 1 | D-PA-01 | Durée et validité d'une garantie, expiration | Garanties, D-PA-14, D-PA-12 (libération) | Ouverte |
| 2 | D-PA-14 (règle) | Traitement du chevauchement | Création de garanties | Ouverte (identifiant validé) |
| 3 | D-PA-04 | Traitement des réservations PENDING | Réservations, transfert | Ouverte |
| 4 | D-PA-07 | Retrait d'une garantie avec réservation CONFIRMED | Garanties, transfert | Ouverte |
| 5 | D-PA-12 §3.5 | Liste exhaustive des engagements bloquants | Transfert | Ouverte |
| 6 | D-PA-10 / D5 | Moment de confirmation et acompte | Paiement, réservation | Ouverte |
| 7 | D-PA-17 | Partenaire désactivé avec garanties futures | Partenaires, garanties | Provisoire |
| 8 | D-PA-16 | Maintenance d'un véhicule couvert par une garantie | Véhicules, réservations | Provisoire |
| 9 | D-PA-15 | Type de partenaire | Partenaires | Provisoire |
| 10 | D-PA-18 | Rôle PARTNER en phase 1 | Accès, sécurité | Provisoire |

Autres décisions ouvertes du registre, non traitées ici en détail : D-PA-03 (modification d'une garantie), D-PA-08 (visibilité publique), D-PA-09 (pénalités, avis juridique et comptable), D-PA-11 (droit de création de partenaires), D-PA-13 (durée minimale de location). Une proposition sur l'archivage d'un véhicule est ajoutée en §11 car elle n'a pas d'identifiant au registre.

---

## 2. D-PA-01 — Durée et validité d'une garantie

**Problème métier.** Une garantie doit avoir une période claire pour que le client voie un véhicule disponible seulement lorsque le partenaire s'est engagé. Sans borne, le partenaire ne sait pas quand son engagement cesse, et Elite ne peut pas prouver ce qui était garanti à une date donnée.

**Contexte.** Le modèle proposé (doc 04) prévoit `start_date`, `end_date`, `status` ∈ {ACTIVE, WITHDRAWN, EXPIRED}. La règle de validité n'est pas tranchée. Le code actuel ne contient aucune garantie : la question porte sur la conception.

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Période bornée, expiration automatique | Chaque garantie a `valid_from` et `valid_until` ; à la date de fin, le statut passe à EXPIRED | Prévisible, auditable, aucune action humaine oubliée | Nécessite un traitement d'expiration (job ou contrôle à la lecture) |
| B. Période bornée, expiration manuelle | L'admin ferme la garantie | Contrôle humain | Oubli fréquent, client voit un véhicule non garanti |
| C. Garantie ouverte (sans fin) | Valide jusqu'à retrait | Simple à saisir | Engagement illimité, impossible à auditer, contraire au principe de garantie datée |

**Recommandation (non validée) : option A.** Une période bornée avec expiration automatique. La date de fin est obligatoire. Une garantie EXPIRED ne redevient jamais ACTIVE ; un renouvellement crée une nouvelle garantie.

**Points à arbitrer par le propriétaire :**
- Durée maximale d'une garantie (valeur numérique non proposée ici : c'est un choix commercial).
- Fuseau horaire de référence (proposition : Africa/Tunis, dates stockées en TIMESTAMPTZ).
- Règle de couverture : une réservation doit-elle être entièrement incluse dans la garantie ? **Recommandation : oui**, la couverture doit être totale du début à la fin de la location. Une réservation qui dépasse la fin est refusée à la création.

**Garanties couvrant plusieurs jours.** Acceptées, sans fractionnement automatique.

**Cas d'un partenaire qui ne renouvelle pas.** La garantie expire à sa date de fin. Les réservations CONFIRMED déjà créées restent valides (non rétroactif) mais une alerte opérationnelle est recommandée lorsque la date de fin d'une garantie précède la date de fin d'une réservation qu'elle couvre. Cette alerte n'est pas une libération.

**Exemple Elite Tunisia.** Garantie Porsche Cayenne, partenaire A, du 10 au 20 juin, validée le 1er juin. Le 21 juin, la garantie est EXPIRED. Une réservation du 15 au 25 juin créée avant l'expiration ne peut pas exister sans couverture totale : elle aurait été refusée. Une réservation du 18 au 22 juin est refusée à sa création si la garantie se termine le 20 juin.

**Dépendances.** D-PA-14 (chevauchement se calcule sur les périodes), D-PA-07 (retrait), D-PA-12 (libération non automatique).

**Formulation à valider.** « Une garantie a une date de début et une date de fin obligatoires. Elle passe automatiquement à EXPIRED à sa date de fin. Une réservation garantie doit être entièrement couverte par une garantie ACTIVE au moment de sa création. » Durée maximale : **à fixer par le propriétaire**.

---

## 3. D-PA-14 — Traitement du chevauchement des garanties

**Problème métier.** Deux garanties pour le même véhicule et des périodes qui se chevauchent créent un double engagement, ou une ambiguïté sur le partenaire porteur (D-PA-05).

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Refus à la création | Une garantie ACTIVE ne peut pas chevaucher une autre garantie ACTIVE du même véhicule | Déterministe, cohérent avec la règle « un véhicule, un partenaire courant » (D-PA-12) | Saisie plus stricte, nécessite de retirer ou corriger avant de créer |
| B. Arbitrage manuel | Autorisé, puis l'admin choisit | Souple | Double engagement possible pendant l'arbitrage ; risque de réservation sur un engagement non tranché |
| C. Priorité automatique | La plus récente gagne | Pas de blocage | Le partenaire porteur change sans décision ; contraire à la traçabilité |
| D. Garanties multiples simultanées | Plusieurs garanties actives sur la même période | Pas de blocage | Deux partenaires porteurs pour un même véhicule : incompatible avec D-PA-05 et D-PA-12 |

**Recommandation (non validée) : option A.** Refus déterministe. La contrainte est portée par la base (EXCLUDE sur `(vehicle_id WITH =, validity WITH &&)` filtré sur `status = 'ACTIVE'`) et doublée par un contrôle du service dans la transaction. Le PostgreSQL EXCLUDE partiel est attendu mais **non vérifié** : aucun test PostgreSQL n'a été exécuté.

**Option D écartée** par cohérence : deux garanties ACTIVE simultanées pour le même véhicule supposent deux partenaires porteurs, ce qui n'est pas possible avec le modèle validé.

**Exemple.** Partenaire A garantit la Porsche du 1er au 15 juillet. Partenaire B tente une garantie du 10 au 20 juillet : refus. Si B doit reprendre le véhicule, il faut un transfert (D-PA-12) après libération légitime de la garantie de A.

**Dépendances.** D-PA-01 (les périodes doivent être définies), D-PA-12 (un seul rattachement courant).

**Formulation à valider.** « Une garantie ACTIVE ne peut pas chevaucher une autre garantie ACTIVE pour le même véhicule. La création est refusée avec un code explicite. »

---

## 4. D-PA-04 — Réservations PENDING

**Problème métier.** Aujourd'hui, une réservation PENDING ne bloque pas les dates : `BookingService.BLOCKING` ne contient que CONFIRMED et IN_PROGRESS, et la contrainte EXCLUDE V4 ne porte que sur ces deux statuts. Deux clients peuvent donc demander le même véhicule sur les mêmes dates. Le premier à être confirmé l'emporte, le second obtient une erreur à la confirmation.

**Ce que le code fait réellement.** PENDING ne bloque pas. C'est la règle actuelle. **Elle ne doit pas être modifiée sans décision explicite** (consigne de mission).

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Statu quo | PENDING ne bloque pas | Aucun changement | Plusieurs demandes simultanées ; échec tardif à la confirmation ; mauvaise expérience client |
| B. Blocage temporaire (hold) | PENDING bloque les dates pendant une durée limitée, puis passe en EXPIRED/CANCELLED | Protège contre les doublons, expérience prévisible | Nécessite une durée (non proposée ici), un traitement de timeout, et une évolution de la contrainte EXCLUDE ou un verrou applicatif |
| C. Blocage permanent | PENDING bloque jusqu'à décision | Simple | Véhicules immobilisés par des demandes abandonnées |

**Recommandation (non validée) : option B.** La contrainte doit être étendue (ou complétée par un verrou transactionnel) pour que le hold soit exclusif. La durée du hold et la règle de sortie (expiration automatique ou annulation) sont des choix du propriétaire.

**Impact sur les garanties.** Un PENDING ne consomme pas une garantie. La garantie reste ACTIVE. Le PENDING doit rester couvert par la garantie au moment de sa création (règle D-PA-01).

**Impact sur un transfert (D-PA-12).** À arbitrer : un PENDING doit-il bloquer le transfert ? **Recommandation : oui pendant la durée du hold**, car une demande en cours doit pouvoir aboutir sur le partenaire actuel. À confirmer.

**Protection contre les demandes simultanées.** Déjà présente dans le code pour CONFIRMED et IN_PROGRESS (verrou sur le véhicule et EXCLUDE). Pour le hold, il faudra un verrou identique et un test de concurrence (voir `review/15`).

**Exemple.** Client X demande la Clio du 5 au 7 août à 10 h : PENDING avec hold de N heures (valeur à fixer). Client Y demande les mêmes dates à 10 h 05 : refus « véhicule en cours de réservation ». Si X n'est pas confirmé avant l'échéance, le hold tombe et Y peut réserver.

**Formulation à valider.** « Une réservation PENDING bloque les dates du véhicule pendant une durée de **[à fixer]** puis expire automatiquement. » Alternative à valider : statu quo.

---

## 5. D-PA-07 — Retrait d'une garantie

**Problème métier.** Le partenaire ou Elite peut vouloir retirer un engagement. Un retrait après une réservation confirmée laisse un client sans véhicule.

**Matrice par situation (recommandation).**

| Situation au moment du retrait | Recommandation | Justification |
|---|---|---|
| Aucune réservation sur la période | Retrait autorisé par l'admin, motif obligatoire, statut WITHDRAWN | Aucun engagement client |
| Réservation(s) PENDING sur la période | Retrait autorisé ; les PENDING doivent être re-vérifiés (retirés ou refusés) avant | Le client n'a pas de contrat ferme |
| Réservation(s) CONFIRMED future | **Refus par défaut.** Retrait exceptionnel seulement par décision propriétaire avec procédure de compensation | Un client a un engagement ferme |
| Réservation IN_PROGRESS | **Refus.** Aucun retrait pendant une location | Le véhicule est loué |
| Retrait demandé par le partenaire | Passe par l'admin Elite, jamais directement | Traçabilité et contrôle |
| Retrait décidé par Elite | Motif obligatoire, audit | Responsabilité |

**Notification et traçabilité.** Chaque retrait écrit une ligne d'audit (acteur, date, motif, statut avant/après). Le client n'est notifié que si une réservation est touchée.

**Responsabilité en cas d'engagement déjà pris.** Non tranchée. Options : partenaire responsable, Elite responsable, répartition contractuelle. **Décision propriétaire et avis juridique requis** (lien avec D-PA-09).

**Dépendances.** D-PA-01, D-PA-04, D-PA-09, D-PA-12.

**Formulation à valider.** « Une garantie ne peut être retirée que si aucune réservation CONFIRMED ni IN_PROGRESS ne la référence. Le retrait d'une garantie avec réservation CONFIRMED est une exception soumise à décision propriétaire. »

---

## 6. D-PA-12 — Liste exhaustive des engagements bloquants (§3.5)

**Problème métier.** La règle validée bloque le transfert s'il existe un « engagement contractuel encore actif ». La liste exhaustive n'est pas fixée : sans elle, le contrôle est incomplet.

**Distinction proposée.**

| Catégorie | Exemples | Bloquant pour le transfert ? |
|---|---|---|
| Garantie active (période courante ou future, statut ACTIVE) | Garantie du 10 au 20 juin, validée | **Oui** (validé) |
| Réservation future CONFIRMED | Location du 15 au 25 juin | **Oui** (validé) |
| Réservation IN_PROGRESS | Location en cours | **Oui** (validé) |
| Réservation PENDING | Demande en hold | **À décider** (D-PA-04) ; recommandation : oui pendant le hold |
| Contrat de location en cours | Location IN_PROGRESS | Couvert par IN_PROGRESS |
| Acompte ou paiement associé à une réservation future | Acompte versé pour location à venir | **Recommandation : oui** si la réservation est future et non annulée |
| Transfert opérationnel en attente | Voir note ci-dessous | **À décider** |
| Litige ouvert | Aucun module litige dans le code | **À décider** ; pas de règle sans module |
| Engagement contractuel non clôturé | Contrat partenaire en cours | **À décider** (contrat partenaire non modélisé) |
| Réservation COMPLETED, CANCELLED, REJECTED | Historique | **Non** : données historiques |
| Garantie WITHDRAWN ou EXPIRED | Historique | **Non** |
| Historique des rattachements | Anciens partenaires | **Non** |

**Note de nommage.** Le code contient déjà un « transfer » (`TransferEntity`, `TransferService`), qui désigne le **transport d'un client par un chauffeur** et non le changement de partenaire d'un véhicule. Le terme « transfert de véhicule » doit être explicite dans toute conception et tout code pour éviter la confusion.

**Recommandation (non validée).** Liste minimale bloquante : garantie ACTIVE couvrant la date du transfert ou future ; réservation CONFIRMED future ; réservation IN_PROGRESS ; réservation PENDING non expirée (si D-PA-04 retenu). Les autres catégories à arbitrer par le propriétaire.

**Non-rétroactivité.** Un transfert ne modifie jamais les garanties, réservations ou partenaires historiques. Il ajoute une ligne dans `vehicle_partner_assignments` et met à jour le partenaire courant du véhicule.

**Exemple.** Porsche chez A, garantie de A du 10 au 20 juin, réservation CONFIRMED du 25 au 28 juin. Transfert vers B demandé le 1er juin : refusé (garantie active et réservation future). Transfert demandé le 22 juin, sans réservation future ni garantie couvrant après le 22 : autorisé si le reste de la liste est vide.

**Dépendances.** D-PA-01 et D-PA-07 (pour savoir quand une garantie cesse d'être active), D-PA-04, D-PA-09.

**Formulation à valider.** « Le transfert est refusé s'il existe une garantie ACTIVE couvrant ou suivant la date du transfert, une réservation CONFIRMED future, une réservation IN_PROGRESS, [une réservation PENDING non expirée], [autres engagements à lister]. »

---

## 7. D-PA-10 et D5 — Confirmation et acompte

**Problème métier.** Le moment de confirmation (paiement, acompte, validation admin) et le montant de l'acompte déterminent à partir de quand un client est engagé et quand le partenaire doit être payé ou rémunéré.

**Ce que le code fait.** Une réservation passe de PENDING à CONFIRMED par transition (`BookingEntity.transition`). Le paiement est modélisé séparément (`payments`, V6) et lié au devis (`quote_id`) et optionnellement à la réservation (`booking_id`). La politique d'acompte n'est pas codée comme règle commerciale.

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Paiement intégral à la réservation | Confirmation après paiement | Pas d'impayé | Freine la conversion |
| B. Acompte à la confirmation, solde à la prise en charge | Pourcentage à fixer | Équilibre | Gestion du solde, du remboursement |
| C. Confirmation manuelle sans paiement | Admin confirme | Souple | Risque d'impayé, PENDING qui dure |

**Recommandation (non validée) : option B, avec pourcentage et échéances fixés par le propriétaire.** Aucun pourcentage n'est proposé ici.

**Impact sur le partenaire.** Les règles de commission (D1, indépendant du partenaire) et le partenaire porteur ne doivent pas dépendre de l'acompte. `partners.commission_rate` (V1) ne doit pas être utilisé.

**Dépendances.** D-PA-04 (PENDING et paiement), D-PA-09 (pénalités, remboursement), D-PA-12 (acompte comme engagement).

**Formulation à valider.** Choix entre A, B ou C, puis pourcentage et échéances.

---

## 8. D-PA-17 — Partenaire désactivé avec garanties futures

**Problème métier.** Un partenaire est désactivé (par exemple suspendu). Que deviennent ses garanties futures ?

**Ce que le code fait.** La colonne `partners.active` existe (V1). Aucune entité Java `Partner` n'existe encore : la désactivation n'a pas d'effet métier codé.

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Bloquer les nouvelles garanties seulement | Garanties existantes restent ACTIVE jusqu'à leur fin | Ne casse pas les clients, cohérent avec D-PA-05 | Engagement d'un partenaire désactivé maintenu |
| B. Suspendre les garanties futures | Passage en statut suspendu | Protège Elite | Nouveau statut, réservations à reconsidérer |
| C. Retirer les garanties futures | WITHDRAWN automatique | Simple | Contraire à la règle de retrait (D-PA-07) : retrait automatique interdit sans décision |

**Recommandation (non validée) : option A**, avec alerte si des réservations CONFIRMED dépendent d'une garantie d'un partenaire désactivé. Le retrait automatique (C) est écarté car il contournerait D-PA-07.

**Dépendances.** D-PA-07, D-PA-12 (un partenaire désactivé peut-il céder un véhicule ?), D-PA-18.

**Formulation à valider.** « Un partenaire désactivé ne peut plus recevoir de nouvelle garantie. Ses garanties existantes restent régies par leurs dates. »

---

## 9. D-PA-16 — Maintenance d'un véhicule couvert par une garantie

**Problème métier.** Un véhicule peut passer en maintenance alors qu'il est garanti ou réservé.

**Ce que le code fait.** `BookingService.rentable` bloque la recherche et la création de réservation pour `VehicleStatus.MAINTENANCE` (seul AVAILABLE est louable). En revanche, une réservation CONFIRMED future n'est pas annulée ni bloquée lorsque le véhicule passe en MAINTENANCE. C'est un écart connu.

**Options.**

| Option | Description | Avantages | Risques |
|---|---|---|---|
| A. Refuser la mise en maintenance si réservation CONFIRMED future | Blocage à la mise en maintenance | Aucun client impacté sans prévenance | Peut bloquer une maintenance urgente |
| B. Autoriser et imposer une action | Admin doit réaffecter ou annuler | Souple | Annulation à gérer ; données client |
| C. Autoriser sans action | État actuel | Aucun changement | Client sans véhicule, incohérence |

**Recommandation (non validée) : option A** pour les maintenances planifiées, option B pour les urgences, avec motif et audit. Dans tous les cas, la garantie reste régie par ses dates ; la maintenance ne la retire pas.

**Dépendances.** D-PA-07 (une maintenance n'est pas un retrait), D-PA-01.

**Formulation à valider.** « Une mise en maintenance est refusée si une réservation CONFIRMED future existe, sauf procédure d'urgence explicite. »

---

## 10. D-PA-15 et D-PA-18 — Identifiants provisoires

**D-PA-15 (type de partenaire).** La table V1 `partners` contient `partner_type VARCHAR(40) NOT NULL`. Le sujet est donc réel. L'intitulé « type juridique » du registre est trop précis : le schéma parle de « type de partenaire ». **Recommandation : conserver l'identifiant D-PA-15, préciser l'intitulé en « type de partenaire et statut juridique »**, et ne pas renuméroter. Le champ n'a pas de liste de valeurs documentée : à définir.

**D-PA-18 (rôle PARTNER phase 1).** Aucun rôle PARTNER dans les migrations V1–V20 (vérifié par recherche des rôles). Le sujet est réel : un partenaire aura-t-il un compte de connexion ? **Recommandation : phase 1 sans compte partenaire ; toutes les opérations par l'administration Elite.** Ce choix réduit la surface de sécurité. **Non validé.**

**Doublons.** Aucun doublon de sujet entre D-PA-15 à D-PA-18 et les IDs validés ou ouverts ; D-PA-17 et D-PA-16 couvrent des sujets distincts (désactivation, maintenance).

**Recommandation générale.** Conserver les identifiants provisoires. Le propriétaire doit les confirmer ou les renuméroter explicitement ; cette mission ne renumérote rien.

---

## 11. Archivage et suppression d'un véhicule (sans identifiant au registre)

**Constat.** `VehicleStatus` ne contient pas d'état ARCHIVED. Aucun mécanisme de suppression ou d'archivage de véhicule n'est défini dans les migrations. Les réservations référencent le véhicule.

**Proposition.** Un véhicule ne se supprime jamais physiquement s'il a une garantie, un rattachement, une réservation ou un transfert. Il passe à un état archivé (à ajouter), et ses garanties et réservations restent consultables. Cette proposition doit être **soumise au propriétaire** et portée par un identifiant provisoire (par exemple D-PA-19), à créer seulement après validation.

---

## 12. Synthèse des validations attendues

| # | Décision | Ce que le propriétaire doit trancher |
|---|---|---|
| 1 | D-PA-01 | Option A/B/C ; durée maximale ; couverture totale obligatoire |
| 2 | D-PA-14 | Option A (refus) ou autre |
| 3 | D-PA-04 | Statu quo ou hold ; durée du hold |
| 4 | D-PA-07 | Matrice de retrait ; exception CONFIRMED |
| 5 | D-PA-12 §3.5 | Liste exhaustive ; PENDING bloquant ou non |
| 6 | D-PA-10 / D5 | Option A/B/C ; pourcentage et échéances |
| 7 | D-PA-17 | Option A/B/C |
| 8 | D-PA-16 | Option A/B/C |
| 9 | D-PA-15 | Confirmer l'identifiant et les valeurs de type |
| 10 | D-PA-18 | Compte partenaire en phase 1 : oui ou non |
| 11 | Archivage véhicule | Confirmer le besoin et l'identifiant |

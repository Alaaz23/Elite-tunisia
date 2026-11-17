# Phase 1.5 — Pack de décisions propriétaire et préparation de la reprise

Statut : document de préparation. Aucun code, migration, connexion PostgreSQL, test, installation, modification de CI ou de configuration n'a été effectué pour produire ce document.

Sources : [phase-1-finalization-report.md](./phase-1-finalization-report.md), [first-implementation-batch.md](./first-implementation-batch.md), [implementation-roadmap.md](./implementation-roadmap.md), [business-decisions-pending.md](./business-decisions-pending.md), [business-decision-analysis.md](./business-decision-analysis.md), [elite-business-model-proposal.md](./elite-business-model-proposal.md), et le code réel cité ci-dessous.

Légende des preuves : **[CODE]** lu dans le code source pendant cette phase ; **[DOC]** lu dans la documentation ; **[HYPOTHÈSE]** non vérifiable dans l'environnement.

---

## 1. Synthèse des blocages

| # | Blocage | Tâche(s) bloquée(s) | Nature | Qui débloque |
|---|---|---|---|---|
| B1 | Référence `D-02` orpheline dans T-A05 (P-02) | T-A05 | Décision documentaire | Propriétaire |
| B2 | Aucune règle de retard : une location `IN_PROGRESS` dépassée reste bloquante | T-A05 | Décision métier | Propriétaire |
| B3 | Sens de `RESERVED` non défini | T-A05 | Décision métier | Propriétaire |
| B4 | Comportement d'un devis expiré et de la réservation associée non défini | T-A07 (matrice #22) | Décision métier + autorisation | Propriétaire, puis autorisation |
| B5 | Job E2E absent de la CI | T-A06 | Autorisation (matrice #16) | Propriétaire |
| B6 | Configuration Hikari, health checks, restauration non tranchés | T-L02 | Autorisation (matrice #17) + autorisation PostgreSQL distincte | Propriétaire |
| B7 | Node 18.20.8 installé, `engines` exige 22.22.3+ ou 24.15.0+ | Angular `ng test`/`ng build`, Playwright, CSP navigateur | Autorisation d'installation | Propriétaire |
| B8 | Topologie du proxy de production inconnue | T-L01a (`forward-headers-strategy`) | Information à fournir | Propriétaire / exploitation |
| B9 | Contrat de l'API admin factures (pagination) à valider | T-A08 | Décision de contrat d'API | Propriétaire |

**Constat [CODE]** : `VehicleStatus.RESERVED` existe mais n'est jamais posé par le backend. Il n'est sélectionnable que manuellement par l'administrateur ([admin-vehicles.component.ts](../../frontend/src/app/features/admin/admin-vehicles.component.ts), options lignes 21, 42, 129).

---

## 2. Décisions métier proposées

### A. P-02 / D-02 — Référence documentaire erronée de T-A05

**Problème concret** : T-A05 (disponibilité temporelle) référence `D-02`, mais aucune décision `D-02` n'existe dans [business-decisions-pending.md](./business-decisions-pending.md). La référence est signalée comme orpheline dans [implementation-roadmap.md](./implementation-roadmap.md) (ligne ~332). Le sujet réel est un problème de disponibilité, pas une décision financière.

**Comportement actuel** : [BookingService.java](../../backend/src/main/java/tn/elitetunisia/booking/BookingService.java) `rentable` exige `VehicleStatus.AVAILABLE`, un véhicule actif et une catégorie active. La disponibilité temporelle n'est pas modélisée (voir B2 et B3).

**Conséquences client** : aucune directe. Le client peut être bloqué par un véhicule marqué indisponible pour toutes les dates, même si la période demandée est libre (cas NEW-01 documenté).

**Conséquences Elite Tunisia** : tant que la référence est ambiguë, T-A05 ne peut pas être considéré comme validé au regard du processus de décision.

**Options** (telles que notées dans la documentation) :
- a) Retirer la référence `D-02` de T-A05 et traiter T-A05 comme sujet technique interne, lié aux décisions B2 et B3.
- b) Désigner une décision existante comme porteuse du sujet.
- c) Créer une nouvelle décision dédiée à la disponibilité temporelle.

**Avantages / risques**
- a) Simple et sans nouvelle décision. Risque : il faut confirmer que les règles de disponibilité ne touchent aucun sujet financier.
- b) Rattache le sujet à une décision existante, mais aucune ne traite réellement la disponibilité : risque de mauvais rattachement.
- c) Clarifie le périmètre. Risque : ajoute une décision alors que B2 et B3 suffisent.

**Recommandation technique** : option **a**. Retirer la référence et lier T-A05 directement à B2 et B3, qui sont les vraies décisions. Cela ne tranche pas de sujet métier et supprime une référence sans contenu.

**Conséquences techniques** : aucune modification de code. Mise à jour documentaire de T-A05 et de la ligne d'observation de la roadmap, après accord du propriétaire.

---

### B. Retard de restitution — location `IN_PROGRESS` après `ends_at`

**Problème concret** : un client prend le véhicule (pickup) puis le rend après la date prévue. Aujourd'hui rien ne se passe automatiquement.

**Comportement actuel [CODE]** :
- Pickup ([BookingOperationService.java](../../backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java), ligne ~48) : véhicule `RENTED`, réservation `IN_PROGRESS`.
- Retour (`recordReturn`, lignes ~65–100) : véhicule `MAINTENANCE` si dommages, sinon `AVAILABLE` ; réservation `COMPLETED`.
- Aucun job ne fait passer une réservation `IN_PROGRESS` en retard vers un autre état. Aucun champ « en retard » n'existe.
- `BookingRepository.overlaps` ne couvre que la plage `[startDateTime, endDateTime[`. Après `ends_at`, une location en retard n'apparaît plus dans les chevauchements.
- En revanche, `rentable` refuse tout véhicule `RENTED`, quelle que soit la date demandée. Le véhicule reste donc bloqué pour toutes les dates tant qu'il n'est pas rendu : c'est une protection involontaire, trop large (NEW-01).
- La contrainte PostgreSQL `bookings_no_blocking_overlap` (V4) s'appuie sur la plage `[starts, ends[`. Elle ne protège donc pas la période de retard.

**Conséquences client** : une location en retard peut laisser le véhicule chez le client alors que la plateforme affiche un autre client comme disponible à partir du lendemain (si T-A05 retire le blocage actuel sans règle de retard). Les règles de pénalité ou de frais de retard sont des décisions commerciales non prises (voir D3) : elles ne sont pas proposées ici.

**Conséquences Elite Tunisia** : risque de conflit de location, de litige et de relance manuelle non tracée.

**Options**
1. **Blocage strict jusqu'au retour effectif** : le véhicule reste `RENTED` pour toutes les dates tant qu'il n'est pas rendu. *Avantage* : sûr, aucune évolution. *Inconvénient* : bloque aussi les dates futures non concernées (statu quo NEW-01).
2. **Blocage temporel du retard** : après `ends_at`, une location non rendue bloque les demandes qui chevauchent la période `[now, retour prévu]`, et seulement cette période. Les dates futures au-delà restent ouvertes. *Avantage* : cohérent avec T-A05. *Inconvénient* : ne peut pas être garanti par la seule contrainte V4 ; il faut une règle de service et un verrou.
3. **Délai de tolérance** : une durée de grâce (à définir par le propriétaire) avant tout blocage temporel. *Avantage* : évite les blocages pour quelques minutes. *Inconvénient* : crée un risque sur la durée de grâce, valeur non fixée.
4. **Requalification ou prolongation** : le retard est traité comme une nouvelle période à valider par un administrateur, avec création d'une prolongation. *Avantage* : traçable. *Inconvénient* : nécessite un nouveau flux (hors Lot 1).

**Recommandation technique** : option **2**, avec option **1** comme repli tant que la règle n'est pas validée. Ne pas ajouter de nouveau statut d'enum : calculer « en retard » à partir de `IN_PROGRESS` et de `ends_at`. Le blocage de la période de retard doit être vérifié dans la même transaction que la création de réservation, avec verrou sur le véhicule (`lockForCommercialOperation` existe déjà). La durée de tolérance (option 3) reste un paramètre séparé.

**Conséquences techniques** : T-A05 ne doit pas retirer le blocage `RENTED` avant que cette règle soit validée. Tests H2 possibles pour la logique ; tests de contrainte à rejouer sur PostgreSQL isolé (autorisation distincte).

**Questions à trancher par le propriétaire** : valeur de tolérance éventuelle ; blocage temporel (option 2) ou strict (option 1) ; qui décide d'une prolongation.

---

### C. Sens de `RESERVED`

**Problème concret** : `RESERVED` est un statut de véhicule sans définition métier.

**Comportement actuel [CODE]** :
- `VehicleStatus` : `AVAILABLE`, `RESERVED`, `RENTED`, `MAINTENANCE`, `UNAVAILABLE`.
- Le backend ne pose jamais `RESERVED` (aucune transition automatique).
- Contrainte V3 et `catalogue-api.service.ts` (ligne ~57) l'acceptent.
- Dans `rentable`, seul `AVAILABLE` passe. Un véhicule `RESERVED` est donc **déjà refusé à la réservation, pour toutes les dates**, comme `MAINTENANCE` ou `UNAVAILABLE`.
- `UNAVAILABLE` est affiché comme « Masqué » dans l'UI admin.

**Conséquences client** : un véhicule posé en `RESERVED` disparaît de la location sans explication ni date de retour.

**Conséquences Elite Tunisia** : un administrateur peut bloquer un véhicule par erreur ou sans traçabilité, pour toutes les périodes.

**Options**
1. **Retirer l'usage de `RESERVED`** : ne plus le proposer dans l'UI admin ; le conserver en lecture seule pour les données historiques. *Avantage* : pas de nouvelle règle. *Inconvénient* : suppression d'une option existante, à confirmer.
2. **Conserver `RESERVED` comme blocage administratif manuel**, avec motif obligatoire et traçabilité d'audit. *Avantage* : utile en exploitation. *Inconvénient* : reste global (pas de période) et doit être distingué de `MAINTENANCE`.
3. **Remplacer `RESERVED` par un blocage temporel** (table de périodes, T-D01). *Avantage* : résout le problème de fond. *Inconvénient* : dépend de T-D01, donc hors Lot 1.

**Recommandation technique** : option **1** à court terme, et option **3** comme cible. Ne pas donner à `RESERVED` un sens automatique avant T-D01. Si l'option 2 est retenue, `RESERVED` doit avoir un motif et un audit obligatoires.

**Relations** : `AVAILABLE` (louable selon `rentable`) ; `RENTED` (posé au pickup, retiré au retour) ; `MAINTENANCE` (posé au retour si dommages) ; `UNAVAILABLE` (masqué) ; `RESERVED` (manuel, sans effet automatique).

**Conséquences techniques** : option 1 = changement d'UI uniquement (après autorisation). Option 2 = règle d'audit et motif obligatoire, à valider. Aucun changement de contrainte V3 sans migration validée.

---

### D. Devis expiré

**Comportement actuel [CODE]** :
- Transitions de devis ([QuoteEntity.java](../../backend/src/main/java/tn/elitetunisia/quote/QuoteEntity.java), ~50–60) : `DRAFT→SENT` ; `SENT→ACCEPTED/REJECTED/EXPIRED/CANCELLED`. `EXPIRED` est terminal : **réactivation impossible**.
- `send` refuse un devis dont `validUntil` est dépassé.
- `customerAction` appelle `expireIfDue` puis lève `QUOTE_EXPIRED`. L'exception est `noRollbackFor`, donc l'expiration est persistée.
- `accept` refuse tout devis expiré.
- `get` (`@Transactional` non readOnly) écrit l'expiration à la lecture.
- `QuoteExpirationWorker` expire les devis `SENT` échus, par lot de 100, toutes les 60 s par défaut (`app.quotes.expiration-scan-ms`).
- `create` : un nouveau devis est autorisé si la réservation est `PENDING` et si la version précédente n'est pas `DRAFT`. Un devis `EXPIRED` ne bloque donc pas la création d'une nouvelle version. L'ancien `EXPIRED` n'est pas annulé.
- Une réservation associée reste `PENDING`. Aucun job n'expire les réservations `PENDING` (gap P1-02, lié à T-E02).

**Réponses aux questions posées** :
- *Peut-il être réactivé ?* Non, dans le code actuel. Décision à confirmer.
- *Faut-il générer un nouveau devis ?* Possible aujourd'hui via `create`. Décision à confirmer : automatique ou sur action administrateur.
- *Peut-il encore être accepté ?* Non. Le code refuse toute acceptation après expiration.
- *Que devient la réservation associée ?* Elle reste `PENDING` indéfiniment. C'est le point ouvert.

**Conséquences client** : une demande peut rester « en attente » sans fin, sans information. Un nouveau devis peut être proposé ou non selon la décision.

**Conséquences Elite Tunisia** : des réservations `PENDING` s'accumulent et bloquent potentiellement la même période si elles sont traitées comme des demandes (à vérifier avec T-E02 et l'écran admin).

**Options**
- Réservation associée :
  1. Rester `PENDING` jusqu'à action administrateur. *Avantage* : aucune règle nouvelle. *Inconvénient* : accumulation.
  2. Annulation automatique de la demande après expiration du devis, avec notification. *Avantage* : propre. *Inconvénient* : règle commerciale à valider, notification à concevoir.
- Nouveau devis :
  1. Autorisé sur action administrateur (comportement actuel de `create`, à confirmer). *Avantage* : contrôle humain.
  2. Automatique à l'expiration. *Inconvénient* : hors périmètre tant que le modèle de prix n'est pas validé (D1, D2, D6).
- Lecture et écriture : la lecture (`get`) écrit l'expiration. Passer `get` en lecture seule (T-A07) empêche l'expiration si personne n'appelle le worker : option à valider au regard de la matrice #22.

**Recommandation technique** : réactivation **interdite** (statu quo). Nouveau devis **sur action administrateur** (statu quo). Réservation `PENDING` : **option 1 pour le Lot 1** (rester `PENDING`, afficher l'état « devis expiré » dans l'UI admin), et **option 2** à décider avec T-E02. Ne pas toucher à l'écriture à la lecture avant autorisation #22.

**Conséquences techniques** : T-A07 peut être repris après décision D, sur autorisation #22. Aucun changement financier.

---

## 3. Options et recommandations : synthèse

| Sujet | Recommandation | Décision propriétaire requise | Impact code |
|---|---|---|---|
| A. P-02 / D-02 | Retirer la référence, lier T-A05 à B2 et B3 | Oui (accord de principe) | Documentaire |
| B. Retard | Blocage temporel de la période de retard ; repli sur blocage strict | Oui | T-A05 (service, verrou) |
| C. `RESERVED` | Retirer l'usage à court terme ; cible T-D01 | Oui | UI admin (après autorisation) |
| D. Devis expiré | Pas de réactivation ; nouveau devis sur action admin ; réservation reste `PENDING` | Oui | T-A07 (après autorisation #22) |

---

## 4. Fiches d'autorisation technique

Aucune de ces actions n'a été exécutée. Chaque fiche doit être approuvée séparément.

### 4.1 T-A06 — Job E2E et épinglage Node dans la CI

- **Fichiers** : [ci.yml](../../.github/workflows/ci.yml) (4 jobs actuels : `backend-h2`, `backend-postgres`, `frontend`, `dependency-review` ; aucun job E2E). [frontend/package.json](../../frontend/package.json) (`engines`), [frontend/playwright.config.ts](../../frontend/playwright.config.ts) (`testDir: './e2e'`, pas de `webServer`).
- **Commandes prévues** (une fois autorisées) : ajout d'un job `e2e` avec Node 24.15.0 ; `npm ci` ; `npx playwright install --with-deps chromium` ; `npx playwright test` ; `actions/upload-artifact` pour les rapports. L'action `upload-artifact` est une nouvelle dépendance de CI : elle doit être justifiée et épinglée par SHA.
- **Risques** : E2E instables (historique de flakiness), temps de build allongé, blocage des merges si le job est rendu obligatoire, besoin d'un serveur applicatif (`webServer`) absent de la configuration Playwright.
- **Tests** : exécution du job sur une branche ; trois exécutions vertes consécutives ; rapports présents en artefacts.
- **Retour arrière** : restaurer `ci.yml` depuis son état actuel (copie de sauvegarde avant modification). Ne pas rendre le job obligatoire tant qu'il n'est pas stable.

### 4.2 T-L02 — Configuration Hikari, health checks, sauvegarde et restauration

- **Fichiers** : [application.yml](../../backend/src/main/resources/application.yml) (`management.endpoints.web.exposure.include: health,info`, lignes ~58–62 ; Hikari et `show-details` à vérifier car non présents dans les lignes lues), profils de production (à localiser), [docs/backup-restore.md](../../docs/backup-restore.md), [scripts/db-backup.ps1](../../scripts/db-backup.ps1), [scripts/db-restore.ps1](../../scripts/db-restore.ps1).
- **Commandes prévues** (après autorisations distinctes) : modification des propriétés Hikari et de Actuator ; `scripts/db-backup.ps1` sur la base source (lecture seule côté source, mais **accès PostgreSQL réel : autorisation PostgreSQL séparée**) ; `scripts/db-restore.ps1` dans une **nouvelle** base isolée et jetable, jamais dans une base existante.
- **Risques** : fuite d'informations par les health checks détaillés ; sauvegarde des fichiers non incluse (voir [security-review-images-storage-migrations.md](../security-review-images-storage-migrations.md)) ; restauration par erreur sur la base locale réelle ; dimensionnement Hikari non justifié sans charge mesurée.
- **Tests** : health sans détail en production (MockMvc) ; restauration complète sur base isolée avec rapport conservé ; `pg_restore --list` sur la sauvegarde.
- **Retour arrière** : restaurer les fichiers de configuration depuis leur sauvegarde ; supprimer uniquement la base isolée créée pour le test, après confirmation explicite.

### 4.3 Installation d'une version Node compatible

- **Environnement** : poste de développement Windows. Node 18.20.8 installé (`C:\Program Files\nodejs\node.exe`). Aucun gestionnaire de versions détecté (nvm, fnm, volta).
- **Version cible** : Node 24.15.0 (version épinglée en CI) ou 22.22.3 (`engines`). Recommandation : 24.15.0 pour rester aligné sur la CI.
- **Méthode recommandée** : installation côte à côte (archive portable ou gestionnaire de versions), sans remplacer Node 18, pour permettre un retour arrière immédiat.
- **Commandes prévues** : `node -v` ; `npm -v` ; `npm ci` dans `frontend/` ; `npx ng test --watch=false` ; `npx ng build` ; `npx playwright install chromium` (si E2E).
- **Risques** : modification de l'environnement système (PATH, variables) ; téléchargement de binaires ; incompatibilité de dépendances npm avec la nouvelle version.
- **Tests** : `ng test` et `ng build` verts sous Node cible ; `npm audit` consigné (sans correction automatique non revue).
- **Retour arrière** : désinstallation de la version ajoutée ; Node 18 conservé.

### 4.4 Validation CSP dans un navigateur

- **Fichiers / environnements** : [SecurityConfig.java](../../backend/src/main/java/tn/elitetunisia/common/config/SecurityConfig.java) (CSP `Report-Only` déjà présente côté API) ; [index.html](../../frontend/src/index.html) (Google Fonts, lignes ~10–12) ; serveur de développement Angular (`ng serve`) sous Node cible.
- **Commandes prévues** : démarrer l'application en local (`ng serve` et backend sur un profil de test) ; navigation sur le parcours public et connexion ; collecte des violations CSP dans la console du navigateur (outils navigateur ou Playwright).
- **Risques** : la CSP `Report-Only` sans `report-uri` ne remonte aucun signal ; Google Fonts non autorisé dans `style-src` / `font-src` ; le SPA servi par un autre composant en production est inconnu (B8).
- **Tests** : zéro violation sur le parcours de référence en mode `Report-Only` ; aucune activation du mode bloquant dans cette fiche.
- **Retour arrière** : retirer le bloc `headers` modifié dans `SecurityConfig.java` (6 lignes ajoutées, aucune suppression) ; aucune modification de `index.html` sans autorisation séparée.

---

## 5. Matrice des dépendances

| Tâche | Dépend de | Autorisation requise | Décision requise |
|---|---|---|---|
| T-A05 (disponibilité temporelle) | A (référence), B (retard), C (`RESERVED`) | Code backend (autorisation explicite) | Oui |
| T-A05 tests PostgreSQL (contrainte V4, concurrence) | T-A05 | Accès PostgreSQL isolé | Non |
| T-A07 (devis, lecture seule, matrice #22) | D (devis expiré) | Matrice #22 | Oui |
| T-E02 (expiration des demandes `PENDING`) | D (réservation associée) | Code backend | Oui (option 2) |
| T-A06 (job E2E CI) | Node cible (4.3) | Matrice #16 | Non |
| Angular `ng test` / `ng build` / Playwright | Node cible (4.3) | Installation | Non |
| T-L02 (Hikari, health) | — | Matrice #17 | Non |
| T-L02 (restauration) | Sauvegarde | Autorisation PostgreSQL distincte, base isolée | Non |
| T-L01a (CSP navigateur) | Node cible, B8 | Validation navigateur (4.4) | Non |
| T-L01a (`forward-headers-strategy`) | B8 (topologie proxy) | Matrice #18 | Information requise |
| T-A08 (pagination factures) | Validation du contrat API | Code backend et frontend | Oui (contrat) |

**Aucune dépendance financière** : D1, D2, D3, D5, D6, D11, D12 ne bloquent aucune tâche de Phase 1. Ne pas les introduire dans cette phase.

---

## 6. Ordre recommandé de reprise

1. **Décision A** (référence P-02/D-02) : mise à jour documentaire de T-A05 après accord.
2. **Décisions B et C** (retard, `RESERVED`) : prérequis de T-A05. Aucun retrait du blocage `RENTED` avant décision B.
3. **Implémentation T-A05** côté backend, tests H2, puis tests PostgreSQL isolés sur autorisation distincte.
4. **Décision D** (devis expiré) puis **T-A07** sur autorisation #22.
5. **Installation Node cible** (4.3) : débloque `ng test`, `ng build` et Playwright. Cette étape ne dépend d'aucune décision et peut être faite en premier si l'autorisation est accordée.
6. **T-A06** (CI E2E, matrice #16) une fois Node validé localement.
7. **T-L02** (configuration, matrice #17) et test de restauration sur base isolée (autorisation PostgreSQL distincte).
8. **T-L01a** : validation CSP navigateur (4.4), puis `forward-headers-strategy` après réception de B8.
9. **T-A08** (pagination) après validation du contrat API.

Justification : les étapes 1 à 4 traitent les blocages métier qui conditionnent le parcours de location. Les étapes 5 à 8 sont techniques et peuvent avancer en parallèle si les autorisations sont données. Les décisions financières sont exclues.

---

## 7. Critères de sortie de la Phase 1

La Phase 1 est close lorsque **chacun** des points suivants est prouvé par une exécution documentée (log ou rapport conservé) :

- [ ] Décisions A, B, C et D tranchées par le propriétaire et consignées dans [business-decisions-pending.md](./business-decisions-pending.md).
- [ ] T-A05 : implémentation conforme aux décisions ; tests H2 verts ; tests PostgreSQL isolés verts (les 10 tests ignorés actuels sont exécutés sur base isolée).
- [ ] T-A07 : tests verts ; décision #22 appliquée.
- [ ] Angular : `ng test` et `ng build` verts sous Node 24.15.0 (ou 22.22.3).
- [ ] Playwright : parcours E2E verts, trois exécutions consécutives, en CI et en local.
- [ ] T-A06 : job E2E présent dans la CI, rapports conservés en artefacts.
- [ ] T-L02 : configuration validée ; restauration complète sur base isolée avec rapport.
- [ ] T-L01a : CSP validée en `Report-Only` sans violation sur le parcours de référence ; `forward-headers-strategy` testée avec un `X-Forwarded-For` non fiable.
- [ ] Statuts mis à jour dans [first-implementation-batch.md](./first-implementation-batch.md), [implementation-roadmap.md](./implementation-roadmap.md) et [implementation-authorization-matrix.md](./implementation-authorization-matrix.md), uniquement avec preuves.

Tant qu'une case n'est pas cochée avec preuve, la tâche reste dans son statut actuel.

---

## 8. Périmètre proposé pour la phase suivante

**Objectif** : rendre le parcours réel de location cohérent de bout en bout, sans finance.

Inclus :
- Disponibilité temporelle (T-A05) avec règles de retard et statut `RESERVED` tranchés.
- Cycle devis et demande (T-A07) avec traitement des devis expirés et des demandes `PENDING` (lien T-E02, option à décider).
- Retour de véhicule et état `MAINTENANCE` (vérification des flux de retour).
- Tests de bout en bout du parcours : demande, devis, acceptation, pickup, retour.

Exclu :
- Tout calcul de commission, reversement, remboursement, acompte, TVA, facturation depuis le devis (D1, D2, D3, D5, D6, D12).
- Choix d'un prestataire de paiement (D11).
- Chauffeurs (D13), contrats partenaires (D7), assurance (D8).
- Redis ou toute infrastructure nouvelle.

---

## 9. Décisions que le propriétaire doit réellement prendre

1. **Référence A (P-02 / D-02)** : accepter le retrait de la référence `D-02` de T-A05 (option a) ?
2. **Retard (B)** : blocage temporel limité à la période de retard (recommandé), ou blocage strict jusqu'au retour ? Existe-t-il une durée de tolérance avant blocage ? Quelle est la règle pour une prolongation ?
3. **`RESERVED` (C)** : retirer son usage à court terme (recommandé), ou le conserver comme blocage administratif avec motif obligatoire ?
4. **Devis expiré (D)** : confirmer : réactivation interdite ; nouveau devis sur action administrateur ; acceptation refusée après expiration ; réservation `PENDING` conservée jusqu'à décision de T-E02 (ou annulation automatique si vous préférez) ?
5. **Autorisations techniques** : donner ou refuser séparément les autorisations #16 (CI), #17 (configuration), #22 (expiration des devis), l'autorisation PostgreSQL distincte pour la restauration sur base isolée, et l'installation de Node 24.15.0.
6. **Topologie du proxy de production (B8)** : quel proxy est devant l'application, et fait-il confiance aux en-têtes `X-Forwarded-*` ? Cette information est nécessaire avant de configurer `forward-headers-strategy`.
7. **Contrat de l'API admin factures (B9)** : accepter une réponse paginée avec taille maximale (changement de contrat, frontend à modifier dans la même livraison) ?

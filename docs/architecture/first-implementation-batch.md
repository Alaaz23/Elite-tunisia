# Premier lot de développement — Elite Tunisia

Statut : **proposition non autorisée**. Aucune tâche de ce document ne doit démarrer avant votre autorisation explicite, tâche par tâche ou lot par lot. Aucune implémentation, migration, connexion PostgreSQL, test ou modification de configuration n'a été effectuée pour rédiger ce document.

Documents de référence : [implementation-roadmap.md](./implementation-roadmap.md) (backlog §11, statuts §11 bis), [business-decisions-pending.md](./business-decisions-pending.md), [elite-business-model-proposal.md](./elite-business-model-proposal.md), [implementation-authorization-matrix.md](./implementation-authorization-matrix.md), [technical-work-reconciliation.md](./technical-work-reconciliation.md).

---

## 1. Critères de sélection

Un lot est retenu s'il satisfait les conditions suivantes :

1. **Fondation** : la tâche est nécessaire à plusieurs modules, ou corrige un risque de sécurité ou de cohérence déjà identifié.
2. **Indépendance des décisions métier** : la tâche ne dépend d'aucune décision D1 à D15 non prise. Une tâche qui dépend d'une décision est dans le lot 2, pas dans le lot 1.
3. **Pas de réécriture prévisible** : la tâche ne sera pas à refaire après une décision métier.
4. **Risque maîtrisé** : pas de changement de schéma non autorisé, pas de test sur base réelle.

## 2. Structure proposée

| Lot | Contenu | Statut |
|---|---|---|
| **Lot 1A** — Stabilisation et plateforme sans décision métier | T-A05 (sous condition), T-A06, T-A07, T-A08, T-L01a, T-L02 | Proposé, non autorisé |
| **Lot 1B** — Socle partenaire et disponibilité | T-B01 partiel, T-C01, T-C02, T-C03 (sans caution), T-D01 | Conditionnel : après validations du propriétaire |
| **Validations sans développement** | T-A02, T-A03 : reproduction des preuves | Autorisation PostgreSQL distincte |
| **Hors lot 1** | T-A04 (V21), T-E01 à T-E03, T-F*, T-G*, T-H*, T-I01, T-D02, T-J01, T-L04 | Bloqués par décisions ou par ordre de dépendances (§5) |

---

## 3. Lot 1A — Stabilisation et plateforme (sans décision métier)

### 3.1 T-A05 — Disponibilité par dates, sans dépendre de `RENTED`

**Condition préalable** : le propriétaire choisit une option pour P-02 (référence « D-02 »). Les options sont (a) retirer la référence, (b) désigner une décision existante, (c) créer une décision dans le registre. **Cette tâche ne doit pas démarrer sans ce choix**, même si son implémentation technique est indépendante de D-02.

| Élément | Contenu |
|---|---|
| Objectif | Une réservation future est acceptée pendant qu'un véhicule est en location, si les dates ne se chevauchent pas. |
| Priorité | P0 (roadmap). Positionnée dans le lot 1A, car elle corrige NEW-01 sans décision métier. |
| Dépendances | P-02 (choix du propriétaire). Aucune dépendance technique ouverte. |
| Fichiers concernés | `backend/src/main/java/tn/elitetunisia/booking/BookingService.java` (`rentable`, lignes 193–199 ; `overlaps`, lignes 53–54). Tests de `BookingService` à localiser. |
| Changements prévus | Dans `rentable` : conserver le rejet de `MAINTENANCE` et `UNAVAILABLE`. Ne plus rejeter `RENTED` pour une réservation future : le chevauchement est contrôlé par `overlaps` (qui inclut `IN_PROGRESS` dans `BLOCKING`). Clarifier le sens de `RESERVED` avant de décider de son traitement. |
| Risque principal | **Retard de retour** : si un véhicule n'est pas rendu à l'heure, sa réservation `IN_PROGRESS` a dépassé `ends_at` et `overlaps` ne le voit plus. Une réservation future pourrait être acceptée alors que le véhicule est chez le client. Ce risque est créé par la correction elle-même. **Une règle de retard doit être décidée avant de livrer T-A05** (voir [elite-business-model-proposal.md](./elite-business-model-proposal.md) §4.4). |
| Risque secondaire | `VehicleSpecification` filtre sur `AVAILABLE` : un véhicule `RENTED` disparaît du catalogue même si la réservation future est possible. À traiter dans la même tâche ou explicitement hors périmètre. |
| Tests | Unitaires : table de cas `rentable` (AVAILABLE, RENTED, MAINTENANCE, UNAVAILABLE, RESERVED) × chevauchement oui/non. Intégration PostgreSQL : contrainte `EXCLUDE` sur `IN_PROGRESS` et `CONFIRMED` (cas A, B, C du §4.3 du modèle). **Le test PostgreSQL requiert une autorisation distincte.** |
| Critère d'acceptation | Réservation future acceptée pendant une location en cours ; chevauchement refusé ; véhicule en `MAINTENANCE` toujours refusé. |
| Autorisation nécessaire | (1) Modification de code : autorisation explicite. (2) Test PostgreSQL : autorisation distincte. (3) Règle de retard : décision propriétaire. |

### 3.2 T-L01a — En-têtes de sécurité, CSP et `forward-headers-strategy`

| Élément | Contenu |
|---|---|
| Objectif | Ajouter les en-têtes de sécurité (CSP, `X-Content-Type-Options`, `Referrer-Policy`, etc.) et configurer la confiance aux en-têtes proxy. |
| Priorité | P1. Aucune décision métier. |
| Dépendances | Aucune. |
| Fichiers concernés | Configuration de sécurité backend (à localiser par recherche de `SecurityFilterChain` ou `headers`), `backend/src/main/resources/application*.yml`, frontend (`index.html` ou configuration Angular si CSP posée côté client). |
| Changements prévus | Backend : en-têtes de sécurité ; `server.forward-headers-strategy` réglé pour n'accepter `X-Forwarded-*` que derrière un proxy de confiance. Frontend : CSP. |
| Risques | **CSP** : Angular peut nécessiter des styles en ligne et l'évaluation de scripts selon la version et la configuration de build ; une CSP trop stricte bloque l'application. **Intégrations externes** : polices, cartes, analytics, paiement. **Forward headers** : un réglage trop permissif permet de falsifier l'IP cliente, ce qui contourne le throttle et fausse les journaux. |
| Tests | MockMvc : présence et valeur des en-têtes. Test de confiance proxy : un `X-Forwarded-For` venant d'une source non fiable est ignoré. Playwright : navigation complète sous CSP en mode `Report-Only` d'abord, puis en mode bloquant après zéro violation. |
| Critère d'acceptation | Tests d'en-têtes verts ; aucune violation CSP sur le parcours E2E ; `X-Forwarded-For` non fiable ignoré. |
| Autorisation nécessaire | Modification de configuration : autorisation explicite. Pas de test PostgreSQL. |

**Recommandation** : déployer la CSP d'abord en `Report-Only`, collecter les violations sur un environnement de test, puis passer en mode bloquant. Ne pas activer la CSP bloquante sans cette étape.

**Statut après Phase 1 (partiel)** : la partie API est implémentée dans `SecurityConfig.java` (CSP `Report-Only` `default-src 'none'; frame-ancestors 'none'; base-uri 'none'`, `Referrer-Policy: no-referrer`, `Permissions-Policy`) et couverte par `SecurityHeadersTest` (2 tests MockMvc, exécutés sous JDK 21 avec H2, OK). Restent ouverts : `server.forward-headers-strategy` (topologie du proxy de production inconnue), la CSP du SPA frontend (service du SPA en production inconnu ; Google Fonts à autoriser), et le test de confiance proxy. T-L01a n'est donc **pas terminée**.

### 3.3 T-A06 — Épingler Node et job E2E en CI

| Élément | Contenu |
|---|---|
| Objectif | CI exécute les tests E2E et conserve leurs rapports. |
| Priorité | P1. |
| Dépendances | Aucune. |
| Fichiers concernés | `.github/workflows/ci.yml` (quatre jobs réels, aucun job E2E), `frontend/package.json` (champ `engines`), `frontend/playwright.config.ts` (`testDir: './e2e'`). |
| Changements prévus | Job E2E dans la CI ; conservation des rapports (artefacts). Version Node alignée sur `engines`. |
| Risques | E2E instables (checkpoints précédents). Une CI qui ajoute un job instable bloque les merges. Node local (18.20.8) incompatible avec `engines` : le poste de développement doit suivre la version CI. |
| Tests | Exécution de la CI sur une branche, job E2E vert trois fois consécutivement, rapports présents comme artefacts. |
| Critère d'acceptation | CI exécute les E2E et conserve les rapports ; version Node identique en CI et `engines`. |
| Autorisation nécessaire | Modification de la CI : autorisation explicite, distincte. |

### 3.4 T-A07 — Lecture de devis sans écriture

| Élément | Contenu |
|---|---|
| Objectif | `QuoteService.get` ne doit pas écrire. L'expiration des devis passe par un traitement dédié. |
| Priorité | P2. |
| Dépendances | Aucune décision métier : l'expiration existe déjà (`valid_until`, V5). |
| Fichiers concernés | `backend/src/main/java/tn/elitetunisia/quote/QuoteService.java`. État : `list` déjà en `readOnly`, `get` encore en transaction d'écriture (roadmap §11 bis). |
| Changements prévus | `get` en `@Transactional(readOnly = true)`. Expiration déplacée vers un traitement planifié. |
| Risques | Un devis expiré ne serait plus marqué à l'instant de la lecture : un client pourrait voir un statut `SENT` alors que la date est passée, le temps que le traitement tourne. À documenter. |
| Tests | Unitaires : `get` ne modifie aucune entité. Test du traitement d'expiration sur date fixée. |
| Critère d'acceptation | `get` sans écriture ; expiration vérifiée par test. |
| Autorisation nécessaire | Modification de code : autorisation explicite. |

### 3.5 T-A08 — Pagination de la liste admin des factures

| Élément | Contenu |
|---|---|
| Objectif | `adminList` des factures paginée, taille maximale serveur. |
| Priorité | P2. |
| Dépendances | Aucune. |
| Fichiers concernés | `backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java` (ligne 86 : `invoices.findAll()`), contrôleur admin des factures, écran admin Angular associé. |
| Changements prévus | `Pageable` avec taille maximale ; réponse paginée. Adaptation de l'écran admin. |
| Risques | **Changement de contrat d'API** : le frontend doit être modifié dans la même livraison. Pagination sans tri stable : résultats incohérents entre pages. |
| Tests | Unitaires : taille demandée au-delà du maximum est bornée. Intégration : tri stable. E2E : affichage et pagination de la liste. |
| Critère d'acceptation | Réponse paginée ; taille maximale appliquée ; écran admin fonctionnel. |
| Autorisation nécessaire | Modification de code backend et frontend : autorisation explicite. |

### 3.6 T-L02 — Pool Hikari, health checks, sauvegarde et restauration testée

| Élément | Contenu |
|---|---|
| Objectif | Pool de connexions dimensionné, health checks, procédure de sauvegarde et restauration vérifiée. |
| Priorité | P1. |
| Dépendances | Aucune décision métier. |
| Fichiers concernés | `backend/src/main/resources/application*.yml`, configuration Actuator, documentation d'exploitation. |
| Changements prévus | Paramètres Hikari ; exposition des health checks minimaux ; procédure de sauvegarde et de restauration. |
| Risques | **Restauration** : elle doit se faire sur une base **isolée et jetable**, jamais sur la base locale réelle ni sur des données existantes. **Health checks** : exposer des détails de base de données en production est une fuite d'information. |
| Tests | Restauration complète d'une sauvegarde sur une base isolée, rapport conservé. Health check sans détail en production. |
| Critère d'acceptation | Restauration réussie sur base isolée, rapport conservé ; health checks sans détail sensible. |
| Autorisation nécessaire | Configuration : autorisation explicite. **Restauration : autorisation PostgreSQL distincte, base isolée uniquement.** |

---

## 4. Lot 1B — Socle partenaire et disponibilité (conditionnel)

Ce lot démarre seulement après validation du propriétaire sur les points suivants : **P-02**, **statut de D13** (§4.1 de ce document), **périmètre minimal de T-B01** (modèle partenaire interne, sans commission), **sens de `RESERVED`**, et **règle de retard** (T-A05).

**Attention à la numérotation.** La roadmap affecte V22 à T-C01. Or `database/proposed/V22__payments_quote_fk_restrict.sql` est déjà réservée à la proposition de clé étrangère. Les numéros de T-C01 et des tâches suivantes doivent être recalculés avant écriture (voir la correction apportée à la roadmap).

### 4.1 T-B01 (partiel) — Validations du propriétaire pour le socle

| Élément | Contenu |
|---|---|
| Objectif | Obtenir les validations nécessaires au socle partenaire, sans trancher les décisions financières. |
| Dépendances | Réponses du propriétaire. |
| Décisions concernées | P-02, D13 (statut), `RESERVED`, règle de retard, modèle partenaire interne. |
| Changements | Aucun code. Mise à jour du registre des décisions une fois les réponses reçues. |
| Autorisation | Décision propriétaire, pas d'autorisation technique. |

### 4.2 T-C01 — Entité `Partner`, partenaire interne, rôles `PARTNER_*`

| Élément | Contenu |
|---|---|
| Objectif | Créer l'entité partenaire, un partenaire interne pour les véhicules existants, et les rôles `PARTNER_*`. |
| Priorité | P0 (roadmap). |
| Dépendances | T-B01 (partiel). **Ne dépend pas de D1** : la commission n'est pas ajoutée dans cette tâche. |
| Fichiers concernés | Nouveau package `partner` ; migration à numéroter (V22 réservée, voir ci-dessus) ; `SecurityConfig` ; `partners` de V1 (existant, à réutiliser). |
| Changements | Table `partners` étendue (si nécessaire) ; rôles et permissions ; rattachement des véhicules existants au partenaire interne (migration). |
| Risques | **Migration sur données existantes** : chaque véhicule existant doit recevoir le partenaire interne, sinon des véhicules deviennent invisibles. Ne pas rattacher la commission ici (D1). |
| Tests | Migration testée sur copie représentative (sans donnée réelle sur base locale). Test d'intégration PostgreSQL : rattachement complet, aucune ligne orpheline. |
| Critère d'acceptation | Véhicules existants rattachés au partenaire interne ; migration testée sur copie ; aucun véhicule sans partenaire. |
| Autorisation | Migration : autorisation distincte. Test PostgreSQL : autorisation distincte. |

### 4.3 T-C02 — Préfixe `/api/partner/**` et isolation

| Élément | Contenu |
|---|---|
| Objectif | Toutes les routes partenaire sous `/api/partner/**`, avec isolation stricte. |
| Priorité | P0. |
| Dépendances | T-C01. |
| Fichiers concernés | `SecurityConfig`, contrôleurs partenaire (nouveaux), services associés. |
| Changements | Filtrage serveur par `partner_id` de l'utilisateur, sur chaque endpoint. |
| Risques | Un seul endpoint oublié ouvre une fuite de données entre partenaires. Le contrôle Angular n'est pas une protection. |
| Tests | Matrice d'isolation : pour chaque endpoint partenaire, un utilisateur du partenaire A demandant une ressource du partenaire B reçoit un refus. Couverture : 100 % des endpoints. |
| Critère d'acceptation | 100 % des endpoints `/api/partner/**` testés en accès croisé ; tous refusés. |
| Autorisation | Code : autorisation explicite. |

### 4.4 T-C03 — Rattachement des véhicules au partenaire

| Élément | Contenu |
|---|---|
| Objectif | Ajouter `partner_id` aux véhicules, la plaque, le statut de publication. |
| Priorité | P0. |
| Dépendances | T-C01. |
| Fichiers concernés | `VehicleEntity`, `VehicleService`, `VehicleSpecification`, migration à numéroter, formulaires Angular. |
| Changements | `partner_id` obligatoire, publication séparée de l'état opérationnel (§4.1 du modèle). |
| **Exclusion** | **Le champ « caution » est exclu** de cette tâche : D4 (cautions) est Reportable. Ajouter la caution ici reviendrait à implémenter une décision non prise. |
| Risques | Véhicules publiés sans validation si le statut de publication n'est pas filtré dans le catalogue. |
| Tests | Un partenaire ne voit que ses véhicules ; un véhicule non publié n'apparaît pas au catalogue client. |
| Critère d'acceptation | Un partenaire ne voit que ses véhicules ; catalogue client filtré par publication. |
| Autorisation | Migration et code : autorisations distinctes. |

### 4.5 T-D01 — Table de blocages (maintenance, indisponibilités)

| Élément | Contenu |
|---|---|
| Objectif | Modéliser les périodes où un véhicule ne peut pas être réservé. |
| Priorité | P0. |
| Dépendances | T-C03. |
| Fichiers concernés | Nouvelle table (migration à numéroter), service de disponibilité, `BookingService`. |
| Changements | Table avec `vehicle_id`, `tstzrange`, type de blocage ; contrainte `EXCLUDE` ; contrôle dans le calcul de disponibilité. |
| Risques | Sémantique de `RESERVED` et de `MAINTENANCE` à clarifier. Les blocages et les réservations doivent être vérifiés dans une même logique. |
| Tests | Cas D du §4.3 du modèle : une demande du 19 au 21 juin est refusée si le blocage du 20 au 22 juin existe. Test PostgreSQL : chevauchement de blocages refusé par la base. |
| Critère d'acceptation | Blocage empêche la réservation ; `EXCLUDE` refuse les chevauchements de blocages. |
| Autorisation | Migration et test PostgreSQL : autorisations distinctes. |

---

## 5. Tâches hors premier lot et raisons

| Tâche | Blocage | Décision ou dépendance |
|---|---|---|
| T-A04 (V21 unique CAPTURED) | **Décision D5 avant V21** ; application de V21 interdite sans autorisation ; précontrôle des doublons à autoriser | D5 |
| T-E01 (validation admin, motifs de refus) | Motifs non décidés | D10 |
| T-E02 (expiration des PENDING) | Délai non décidé | D10, T-E01 |
| T-E03 (annulation chiffrée) | Grille non décidée | D3 |
| T-F01 (paiement lié à la réservation, remboursements) | Acompte, remboursement, prestataire | D5, D3, D11, T-A04 |
| T-F02 (fournisseur réel) | Prestataire non choisi | D11 |
| T-G01 (commission, snapshot) | Modèle de taux | D1 |
| T-G02 (grand livre) | Dépend de T-G01 et des règles de reversement | D1, D2 |
| T-G03 (reversements, relevés) | Fréquence documentée, méthode, délai de contestation | D2, D11 |
| T-H01 (facture depuis réservation payée) | Régime de TVA, cardinalité | D6, D12 |
| T-I01 (durée et exclusion des chauffeurs) | Statut de D13 à confirmer | D13 |
| T-D02 (règles de prix) | **Aucune décision D1–D15 ne couvre la tarification** : un registre est à créer | Propriétaire |
| T-J01 (notifications) | Canal e-mail documenté, reste à décider | D14 (reportable) |
| T-L04 (routes placeholder) | Livrer ou masquer | P-01 |

**Modifications financières exclues du premier lot** : aucune. Les modifications financières dépendent de décisions non prises.

---

## 6. Chemin critique (rappel)

```
P-02, D13 (statut), RESERVED, règle de retard
  └─ T-A05 (lot 1A, sous condition)

T-B01 partiel
  └─ T-C01 ─ T-C02
       └─ T-C03
            └─ T-D01
                 └─ T-E01 (D10)
                      └─ T-F01 (D5, D3, D11, T-A04)
                           ├─ T-G01 (D1) ─ T-G02 ─ T-G03 (D2)
                           └─ T-H01 (D6, D12)
```

**Lecture.** Le chemin critique vers une version exploitable passe par les décisions D5 et D11 pour les paiements, puis D1 et D2 pour le reversement. Les décisions D10 et D3 bloquent les réservations annulées et refusées. Les tâches du lot 1A ne sont pas sur ce chemin critique, mais elles réduisent les risques de stabilité et de sécurité avant lui.

---

## 7. Autorisations requises par type d'opération

| Opération | Autorisation |
|---|---|
| Modification de code | Explicite, par lot |
| Migration de schéma (V22+ à numéroter) | Distincte |
| Connexion ou test PostgreSQL | Distincte, base isolée et jetable uniquement |
| Modification de la CI | Distincte |
| Modification de configuration | Explicite |
| Restauration de sauvegarde | Distincte, base isolée uniquement |
| Décision métier | Propriétaire, par écrit |

Référence complète : [implementation-authorization-matrix.md](./implementation-authorization-matrix.md).

---

## 8. Questions à résoudre avant d'autoriser le lot 1A

1. **P-02** : option (a), (b) ou (c) pour la référence « D-02 » de T-A05 ?
2. **Règle de retard** : tolérance et conséquences d'un retour en retard, avant de livrer T-A05.
3. **Sens de `RESERVED`** : état distinct ou inutilisé ?
4. **T-L01a** : mode `Report-Only` de la CSP accepté avant passage en bloquant ?
5. **T-A06** : job E2E à ajouter maintenant, ou après stabilisation des tests E2E ?

## 9. Verdict

**READY FOR LIMITED IMPLEMENTATION** est écarté : le lot 1A n'est pas autorisé, et T-A05 dépend de P-02 et d'une règle de retard non décidée. Le verdict de cette mission est **READY FOR OWNER DECISIONS** : les tâches du lot 1A peuvent être autorisées après réponse aux questions du §8, tandis que le lot 1B et tout développement financier attendent les décisions correspondantes.

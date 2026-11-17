# Décisions métier en attente — Elite Tunisia

Statut du document : **Phase 0.1, documentaire**. Ce registre centralise les décisions métier ouvertes. Il ne prend **aucune** décision à la place du propriétaire du produit.

Référence d'identifiants : les identifiants `D1` à `D15` sont ceux du §15 de [implementation-roadmap.md](./implementation-roadmap.md), référence canonique citée par les tâches `T-x`. Les autres séries portent un préfixe pour éviter toute confusion :
- `V22-Dx` : registre de la migration V22 ([v22-decision-register.md](./v22-decision-register.md)) ;
- `BM-Dx` : décisions du benchmark, section 10.4 de [international-benchmark-and-target-architecture.md](./international-benchmark-and-target-architecture.md).

## 1. Légende

- **Statut** : `NON PRISE` pour toute décision ci-dessous. Aucune option n'est adoptée.
- **Recommandation** : proposition documentée (roadmap §15 ou benchmark), pas une décision.
- **Options documentées** : seulement celles présentes dans les sources citées. Aucune option n'a été ajoutée.
- **Nature** : `Bloquante` (avant développement de la tâche indiquée), `Avant production`, ou `Reportable` (roadmap §15).

## 2. Tableau de synthèse

| ID | Sujet | Nature (roadmap §15) | Tâches concernées | Statut | Décision avant développement ? |
|---|---|---|---|---|---|
| D1 | Commission | Bloquante (étape D) | Module G | NON PRISE | Oui, pour le snapshot de commission |
| D2 | Reversements | Bloquante (étape D) | T-G03 | NON PRISE | Oui, avant T-G03 |
| D3 | Annulation, remboursement, no-show | Bloquante | T-E03, T-F01 | NON PRISE | Oui |
| D5 | Moment de l'acompte | Bloquante | T-F01 | NON PRISE | Oui |
| D6 | TVA et facturation | Bloquante | T-H01 | NON PRISE | Oui |
| D7 | Contrats partenaires | Avant production | Module B | NON PRISE | Non pour le développement, oui avant production |
| D8 | Assurance et sinistres | Avant production | Modules C, E | NON PRISE | Non pour le développement, oui avant production |
| D10 | Motifs de refus | Bloquante | T-E01 | NON PRISE | Oui |
| D11 | Fournisseur de paiement | Bloquante | T-F02 | NON PRISE | Oui |
| D12 | Facturation depuis le devis | Bloquante | T-H01 | NON PRISE | Oui, avec D6 |
| D4 | Cautions | Reportable | Modules C, F | NON PRISE | Non |
| D9 | Statut des chauffeurs | Reportable | Module I | NON PRISE | Non, à confirmer |
| D13 | Exclusion des chauffeurs sur transferts | Reportable | T-I01 | NON PRISE | **À confirmer** (voir §4) |
| D14 | Canaux de notification | Reportable | T-J01 | NON PRISE | Non |
| D15 | Migrations inertes | Reportable | T-L03 | NON PRISE | Non |
| P-01 | Routes placeholder (livrer ou masquer) | Décision produit, hors roadmap §15 | T-L04 | NON PRISE | Oui, avant T-L04 |
| P-02 | Identifiant « D-02 » (T-A05) | Référence orpheline | T-A05 | NON PRISE | Oui, pour l'affectation de T-A05 |

## 3. Décisions détaillées

Pour chaque décision : question, risque, options documentées, conséquences techniques, modules, décisions liées, décision attendue.

### D1 — Taux de commission

- **Question** : taux de commission global, par partenaire, ou par catégorie ?
- **Risque** : sans taux figé par réservation, impossible de recalculer un montant historique.
- **Options documentées** (source : [marketplace-gap-analysis.md](./marketplace-gap-analysis.md) §7.5) : taux global ; taux par partenaire ; taux par catégorie. Le snapshot du taux sur chaque réservation est documenté comme exigence de traçabilité.
- **Recommandation (roadmap §15)** : taux par partenaire avec défaut global.
- **Conséquences techniques** : colonne de taux sur la réservation (snapshot) ; `partners.commission_rate` existe déjà (V1, `NUMERIC(5,2)`) mais n'est pas lié à `vehicles` ni `bookings`. Ne rien coder en dur.
- **Modules** : G.
- **Décisions liées** : D2 (reversement dépend de la commission), D6 (TVA appliquée à la commission).
- **Décision attendue** : modèle de taux (global, partenaire, catégorie), et règle de snapshot.

### D2 — Fréquence et méthode de reversement

- **Question** : à quelle fréquence et par quel moyen reverser aux partenaires ?
- **Risque** : sans règle, pas de module de reversement fiable ; litiges sur les montants.
- **Options documentées** (source : marketplace-gap-analysis §7.5) : reversement mensuel par virement (recommandé) ; relevés ; délai de contestation ; virement manuel ou automatique.
- **Recommandation (roadmap §15)** : mensuel, virement.
- **Conséquences techniques** : module de payouts absent (« Pas de payouts ») ; dépend de D11 (prestataire) pour l'exécution.
- **Modules** : G.
- **Décisions liées** : D1, D11, D6.
- **Décision attendue** : fréquence, méthode, délai de contestation. Obligatoire **avant T-G03** (roadmap, étape D), et non « avant production » comme l'indiquait l'ancien plan.

### D3 — Annulation, remboursement, no-show

- **Question** : quelle grille d'annulation et de remboursement ?
- **Risque** : état actuel, `customerCancel` sans pénalité ni remboursement ; colonnes `refunded_*` jamais alimentées (constat documenté dans [payment-history-integrity-review.md](./payment-history-integrity-review.md)).
- **Options documentées** (sources : marketplace-gap-analysis §7.8 ; benchmark BM-D9) : grille par délai avant départ (recommandée) ; remboursement partiel ; traitement du no-show ; calcul et écritures inverses.
- **Recommandation (roadmap §15)** : grille par délai.
- **Conséquences techniques** : écritures d'annulation et de remboursement ; calcul de pénalité ; remboursement partiel côté prestataire (D11).
- **Modules** : E, F.
- **Décisions liées** : D5, D11, D8.
- **Décision attendue** : grille (seuils et pourcentages) et règle no-show. Bloquante pour T-E03 et T-F01.

### D5 — Moment de l'acompte

- **Question** : paiement total ou acompte ? Si acompte, à quel moment ?
- **Risque** : parcours de paiement indéfini ; montants et statuts incohérents sans règle.
- **Options documentées** (source : roadmap §15, marketplace-gap-analysis) : paiement total ; acompte différé ; acompte à la validation (recommandé).
- **Option supplémentaire relevée dans le benchmark** (BM-D3, [international-benchmark-and-target-architecture.md](./international-benchmark-and-target-architecture.md) ligne 779) : **paiement sur place**. Ce n'est **pas** une option du registre roadmap §15 ; son ajout ou son rejet relève du propriétaire. Le benchmark indique aussi que Hertz.tn encaisse les frais optionnels sur place (ligne 140).
- **Constat technique** : un modèle acompte + solde produit deux paiements `CAPTURED` pour un même devis. Cela contredit l'invariant « un seul `CAPTURED` par devis » (`PaymentService.createForQuote`, ligne 27 ; contrôles lignes 48 et 51 ; `PaymentRepository.existsByQuote_IdAndStatusIn`, ligne 11 ; index unique partiel de V21 `uq_payments_one_captured_per_quote`). Cette décision doit donc être tranchée **avant** l'application de V21.
- **Recommandation (roadmap §15)** : acompte à la validation.
- **Conséquences techniques** : nouveau statut ou montant partiel ; lien avec le paiement lié à la réservation (T-F01).
- **Modules** : F.
- **Décisions liées** : D3, D11, D4 (si caution).
- **Décision attendue** : type de paiement et moment. Bloquante pour T-F01.

### D6 — TVA, factures et fiscalité

- **Question** : quel régime de TVA, quelle numérotation, quelles mentions de facture ?
- **Risque** : TVA actuellement saisie en montant fixe (`default-tax-amount`, 0 par défaut), sans taux ; numérotation par séquence PostgreSQL non rétroactive, donc numéros manquants possibles en cas de rollback.
- **Options documentées** (sources : marketplace-gap-analysis ; benchmark BM-D2) : TVA en taux ou en montant fixe ; arrondi ; numérotation continue ; facture au nom d'Elite ou du partenaire, avec mandat de facturation.
- **Recommandation (roadmap §15)** : avis d'un expert-comptable tunisien. Aucune règle fiscale n'est proposée dans ce document.
- **Conséquences techniques** : table des taux, mentions obligatoires, séquence de numérotation, éventuel mandat.
- **Modules** : H.
- **Décisions liées** : D12, D1, D2.
- **Décision attendue** : avis de l'expert-comptable, puis décision. Bloquante pour T-H01.

### D7 — Contrats et responsabilités partenaires

- **Question** : quel contrat lie Elite aux partenaires, et qui porte quelle responsabilité ?
- **Risque** : onboarding sans contrat ; responsabilité non répartie en cas de sinistre.
- **Options documentées** : contrat-cadre partenaire (recommandé, roadmap §15).
- **Conséquences techniques** : acceptation de contrat (version, date) à enregistrer si retenue.
- **Modules** : B.
- **Décisions liées** : D8, D9.
- **Décision attendue** : avis juridique. Obligatoire avant production, non bloquant pour le développement.

### D8 — Assurances et sinistres

- **Question** : quelle politique d'assurance, quel traitement des sinistres ?
- **Risque** : litige sans règle écrite.
- **Options documentées** : politique écrite (recommandée, roadmap §15) ; champs et documents d'assurance ; workflow de sinistre (benchmark, sans option alternative documentée).
- **Conséquences techniques** : champs et documents liés aux réservations ; statut de sinistre si retenu.
- **Modules** : C, E.
- **Décisions liées** : D7, D3.
- **Décision attendue** : juridique et assureur. Obligatoire avant production.

### D10 — Conditions de validation et motifs de refus

- **Question** : quels motifs de refus et quelles conditions de validation ?
- **Risque** : étape administrative sans règles ; décisions arbitraires.
- **Options documentées** : liste de motifs (recommandée, roadmap §15) ; étape admin de confirmation manuelle ou instantanée (benchmark BM-D8).
- **Conséquences techniques** : transition `PENDING` → `CONFIRMED` réservée à l'administrateur (`AdminBookingController`, lignes 22–23, permission `bookings:manage`) ; aucun champ de motif structuré n'existe aujourd'hui (`BookingStatus` : PENDING → CONFIRMED, REJECTED, CANCELLED) ; motifs à stocker en base si retenus.
- **Règle définitive** : aucune expiration automatique des `PENDING`. Une demande `PENDING` ne bloque pas la disponibilité ; plusieurs `PENDING` peuvent coexister. La confirmation effectue le contrôle définitif de disponibilité.
- **Modules** : E.
- **Décisions liées** : D3, D8.
- **Décision attendue** : liste des motifs et mode de confirmation. Bloquante pour T-E01.

### D11 — Prestataire de paiement

- **Question** : quel prestataire de paiement ?
- **Risque** : paiement réel impossible sans prestataire ; prestataire simulé à interdire en production.
- **Options documentées** (source : roadmap §15, benchmark BM-D3) : aucune option de prestataire n'est nommée dans les sources. Les exigences documentées sont : encaissement centralisé, séparation des fonds, webhooks, remboursement partiel, devises. Compatibilité marketplace **à prouver**.
- **Recommandation** : aucune documentée au-delà de « compatibilité à prouver ».
- **Conséquences techniques** : intégration webhook ; idempotence ; remboursement partiel ; interdiction du prestataire simulé en profil `prod`.
- **Modules** : F.
- **Décisions liées** : D2, D3, D5.
- **Décision attendue** : choix du prestataire après vérification des exigences. Bloquante pour T-F02.

### D12 — Facturation depuis le devis

- **Question** : une ou plusieurs factures par réservation ? Règle de facturation depuis un devis ?
- **Risque** : doublons de facture ; ISS-04 non traitée.
- **Options documentées** : une facture par réservation ; plusieurs factures par réservation avec unicité en base (source : roadmap §15, marketplace-gap-analysis).
- **Recommandation (roadmap §15)** : « à trancher avec D6 ».
- **Conséquences techniques** : contrainte d'unicité en base si une seule facture ; lien devis → réservation → facture.
- **Modules** : H.
- **Décisions liées** : D6.
- **Décision attendue** : cardinalité, puis règle d'unicité. Bloquante pour T-H01, avec D6.

### D4 — Cautions (Reportable)

- **Question** : caution par empreinte de carte ou dépôt ?
- **Options documentées** (roadmap §15) : empreinte carte ou dépôt, hors chiffre d'affaires.
- **Décision attendue** : reportable, à reprendre après D5 et D11.

### D9 — Statut des chauffeurs (Reportable)

- **Question** : statut juridique et social des chauffeurs.
- **Recommandation (roadmap §15)** : avis juridique et social.
- **Décision attendue** : reportable. Le statut Reportable doit être confirmé car le plan maître l'indiquait « À trancher ».

### D13 — Exclusion des chauffeurs sur transferts (Reportable, à confirmer)

- **Question** : exclure un chauffeur déjà affecté d'un transfert qui chevauche ?
- **Recommandation (roadmap §15)** : durée par défaut puis contrainte EXCLUDE.
- **Constat** : `TransferService` (lignes 116, 121–122) et `TransferRepository` (lignes 23–26) contrôlent le chevauchement uniquement au niveau applicatif. T-I01 implémente l'exclusion. Le statut « Reportable » est donc **incohérent avec T-I01** si T-I01 est planifiée avant production.
- **Décision attendue** : confirmer le statut (Reportable ou non). Non tranché ici. Si T-I01 reste planifiée avant production, D13 ne peut pas rester « Reportable » sans revoir T-I01.
- **Note** : le benchmark le nomme BM-D12 (« exclusion de chauffeur (V25) »).

### D14 — Canaux de notification (Reportable)

- **Options documentées** (roadmap §15) : e-mail d'abord. Le benchmark (BM-D6) mentionne aussi SMS et WhatsApp, sans choix.
- **Décision attendue** : reportable.

### D15 — Nettoyage des migrations inertes (Reportable)

- **Recommandation (roadmap §15)** : après sauvegarde, sur autorisation. Lié au doublon V11 (T-L03), **sans suppression ni déplacement** dans cette phase.
- **Décision attendue** : reportable, par le propriétaire du dépôt.

## 4. Points non métier ouverts

### P-01 — Routes placeholder (T-L04)

- **Constat** : `frontend/src/app/app.routes.ts` définit `const page` (ligne 8) et 12 routes l'utilisent : `/chauffeur`, `/airport-transfer`, `/services`, `/about`, `/faq`, `/contact` (lignes 42 à 47), `/auth/forgot-password` (ligne 58), `/customer/notifications` (ligne 74), `/admin` (ligne 84), `/admin/payments` (ligne 96), `/admin/partners` (ligne 98), `/admin/reports` (ligne 100). La route `/services` annonce des services « configurables depuis le back-office », alors que `/admin/services` (ligne 97) est livré.
- **Question** : livrer le contenu ou masquer la route ? L'option « masquer » est un **choix produit**, non technique.
- **Décision attendue** : propriétaire du produit, avant T-L04.

### P-02 — Identifiant « D-02 » (T-A05)

- **Constat** : « D-02 » est cité par T-A05 dans la roadmap (§11 et §11 bis), le plan (lot 3 et §10.1) et l'audit (AUD-22). Aucune ligne du §15 de la roadmap ne porte cet identifiant.
- **Interprétation non prouvée** : référence orpheline, probablement issue d'une numérotation antérieure. Aucune source retrouvée ne permet de l'affecter.
- **Options documentées** :
  - (a) retirer la référence « D-02 » de T-A05 et traiter T-A05 sans décision métier ;
  - (b) désigner une décision existante (par exemple D2) : **hypothèse non confirmée**, à valider par le propriétaire ;
  - (c) créer une nouvelle décision dans le registre §15 : hors de la présente mission.
- **Décision attendue** : le propriétaire choisit (a), (b) ou (c). Ce choix ne doit pas être fait par cette mission. T-A05 (P0) est également absente des lots 0 et 1 du plan.

## 5. Écarts entre documents (à ne pas fusionner)

| Sujet | Roadmap §15 | Plan maître (avant correction) | Benchmark §10.4 |
|---|---|---|---|
| D2 | Bloquante (étape D) | « avant production » | BM-D2 = TVA, arrondi, numérotation |
| D4 | Cautions, Reportable | Acomptes, À trancher | BM-D5 = paliers, caution |
| D9 | Statut chauffeurs, Reportable | À trancher | BM-D9 = barème d'annulation |
| D12 | Facturation depuis devis | — | BM-D12 = exclusion de chauffeur |
| D13 | Exclusion chauffeur, Reportable | À trancher | BM-D13 = hébergement |

Le plan maître a été aligné sur la roadmap (voir [master-project-completion-plan.md](./master-project-completion-plan.md) §10.1). Les décisions elles-mêmes restent ouvertes.

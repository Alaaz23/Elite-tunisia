# Modèle économique proposé — Elite Tunisia (marketplace de location de véhicules premium)

Statut : **document de cadrage, non décisionnel**. Aucune règle de ce document n'est une décision métier approuvée. Les options réelles restent celles de [business-decisions-pending.md](./business-decisions-pending.md) (D1 à D15). Les montants et taux donnés en exemple sont **hypothétiques** et ne doivent pas être repris comme valeurs commerciales.

Périmètre de la mission : aucun code, aucune migration, aucun SQL, aucune connexion PostgreSQL, aucun test, aucune modification de configuration. Les constats ci-dessous s'appuient sur la lecture des migrations `database/migrations/V1` à `V20`, de `database/proposed/`, de quelques classes Java citées, et des documents existants.

Légende utilisée partout :

| Marque | Signification |
|---|---|
| `[CODE]` | Comportement présent dans le code ou les migrations lus. |
| `[DOC]` | Option ou exigence documentée dans les sources citées. |
| `[PROPOSITION]` | Cadrage proposé par ce document, à valider par le propriétaire. |
| `[NON IMPLÉMENTÉ]` | Absent du dépôt. |
| `[À VÉRIFIER]` | Non confirmé dans cette passe. |

---

## 1. Scénario de bout en bout

Le scénario suit le parcours demandé. Chaque étape indique ce qui existe réellement et ce qui dépend d'une décision.

| # | Étape | État réel | Références | Décision ou tâche requise |
|---|---|---|---|---|
| 1 | Un partenaire propose un véhicule | `[NON IMPLÉMENTÉ]` Aucune migration V1–V20 ne relie `vehicles` à `partners` (pas de `partner_id`). La table `partners` existe (V1) avec `commission_rate`, sans lien. Aucun rôle `PARTNER_*`. | V1, T-C01, T-C03 | D7 (contrat, avant production), D1 (commission par partenaire) |
| 2 | Elite Tunisia valide le véhicule | `[NON IMPLÉMENTÉ]` Aucun état « en validation » ni « publié ». `VehicleStatus` contient `AVAILABLE`, `RESERVED`, `RENTED`, `MAINTENANCE`, `UNAVAILABLE` : ce sont des états opérationnels, pas des états de publication. | `VehicleStatus.java`, V3 (contrainte `chk_vehicles_status`), T-C03 | Proposition : séparer le statut de publication de l'état opérationnel. Le propriétaire doit confirmer le processus de validation. |
| 3 | Le client choisit le véhicule | `[CODE]` Catalogue filtrable. `VehicleSpecification` combine le statut `AVAILABLE` et les réservations `CONFIRMED` / `IN_PROGRESS` `[À VÉRIFIER]` lors de l'implémentation. | `VehicleSpecification.java`, T-A05 | T-A05 : un véhicule `RENTED` ne doit pas masquer une réservation future. |
| 4 | Le client demande un devis | `[CODE]` `QuoteEntity` impose `booking_id NOT NULL` (V5). Une réservation `PENDING` est donc créée avant le devis (`BookingService`, création à la ligne 81). La disponibilité est vérifiée à la création du devis. | V5, `QuoteEntity.java`, `BookingService.java` | Conséquence : une demande `PENDING` ne bloque pas la disponibilité et n'expire pas automatiquement (décision définitive). |
| 5 | Elite Tunisia valide ou refuse la demande | `[CODE]` Transitions `PENDING → CONFIRMED` et `PENDING → REJECTED`, réservées à l'administrateur (`AdminBookingController`, lignes 22–23). `[NON IMPLÉMENTÉ]` Aucun champ de motif structuré pour le refus. | `BookingStatus.java`, `AdminBookingController.java`, D10, T-E01 | D10 : liste des motifs, mode de confirmation, délai d'expiration. |
| 6 | Le client paie | `[CODE]` `PaymentService.createForQuote` ; fournisseur `SimulatedPaymentProvider` (renvoie `CAPTURED` ou `FAILED`, sans webhook). L'unicité d'un `CAPTURED` par devis est contrôlée par code (lignes 48 et 51), **non atomique**. `[PROPOSITION V21]` index unique partiel non appliqué. | `PaymentService.java`, `PaymentRepository.java`, `database/proposed/V21…` | D11 (prestataire), D5 (type et moment du paiement) |
| 7 | La réservation est confirmée | `[CODE]` L'état `CONFIRMED` est atteint par l'administrateur. `[À VÉRIFIER]` Aucun lien automatique entre paiement encaissé et confirmation n'a été établi dans cette passe. | `AdminBookingController.java`, T-F01 | D5 (moment de l'acompte), D11 |
| 8 | Le véhicule est mis à disposition | `[CODE]` Au retrait, `BookingOperationService` passe le véhicule en `RENTED` (ligne 48). La réservation `IN_PROGRESS` est couverte par la contrainte `EXCLUDE` (V4). | `BookingOperationService.java`, V4 | Retard de retour : voir §4.4. |
| 9 | La location est terminée | `[CODE]` Au retour, le véhicule passe en `MAINTENANCE` ou `AVAILABLE` selon les dommages (ligne 91). Le statut `COMPLETED` existe. | `BookingOperationService.java`, `BookingStatus.java` | Procédure de dommages et de sinistre : D8. |
| 10 | Elite Tunisia calcule sa commission | `[NON IMPLÉMENTÉ]` Aucun calcul. `partners.commission_rate` existe mais n'est lié ni à `vehicles` ni à `bookings`. | V1, T-G01 | D1 (modèle de taux, snapshot) |
| 11 | Le partenaire reçoit son reversement | `[NON IMPLÉMENTÉ]` Pas de module de reversement (« Pas de payouts »). | T-G03 | D2 (fréquence, méthode), D11 (exécution) |
| 12 | Facture et historique financier | `[CODE]` `InvoiceService.createReturnChargeInvoice` existe (appelé par `BookingOperationService` ligne 95 pour les suppléments de retour). `InvoiceService.createFromQuote` existe sans appelant trouvé (ligne 30). Depuis V7, `invoices.booking_id` n'est plus unique. L'historique des paiements existe, mais les colonnes `refunded_*` ne sont jamais alimentées (constat de [payment-history-integrity-review.md](./payment-history-integrity-review.md)). | V7, `InvoiceService.java`, T-H01 | D6 (TVA, numérotation), D12 (cardinalité facture / réservation) |

### 1.1 Règles présentes dans le code et règles proposées

**Présentes dans le code ou les migrations (`[CODE]`)**
- Une réservation a un état parmi `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `IN_PROGRESS`, `COMPLETED`.
- Une réservation `CANCELLED` ne peut pas être annulée une seconde fois.
- Deux réservations `CONFIRMED` ou `IN_PROGRESS` ne peuvent pas se chevaucher sur un même véhicule (contrainte `EXCLUDE`, V4, intervalle semi-ouvert `[)`).
- Un devis porte une réservation obligatoire, des dates et un total avec contrôle `total = sous-total − remise + taxe` (V5).
- Un paiement est rattaché à un devis (`quote_id NOT NULL`, V6). Sa suppression en cascade avec le devis est possible tant que V22 n'est pas appliquée.
- Le montant d'un paiement doit égaler le total du devis (`PaymentService`, ligne 34).
- La TVA est un montant fixe (`default-tax-amount`, défaut 0), sans taux (`application.yml`, ligne 35).

**Proposées, non décidées (`[PROPOSITION]` ou `[DOC]`)**
- Taux de commission par partenaire avec défaut global (D1, recommandation de la roadmap §15).
- Snapshot du taux de commission sur chaque réservation (exigence de traçabilité documentée).
- Reversement mensuel par virement (D2, recommandation).
- Acompte à la validation (D5, recommandation).
- Liste de motifs de refus (D10, recommandation).
- Une facture par réservation, avec unicité en base (D12, option documentée).

---

## 2. Scénarios financiers (hypothétiques)

**Base de calcul utilisée dans tous les exemples** : location de 3 jours à 200.000 TND par jour, soit un total client de **600.000 TND**. Aucune TVA n'est appliquée, car son régime n'est pas décidé (D6). Les taux de commission des exemples (10 %, 12 %, 15 %, 18 %, 20 %) sont **arbitraires** et ne constituent aucune recommandation.

### 2.1 D1 — Commission

Options documentées : taux global ; taux par partenaire ; taux par catégorie. Snapshot du taux à la réservation (exigence de traçabilité).

| Modèle | Hypothèse | Commission | Montant dû au partenaire (avant remboursement) |
|---|---|---|---|
| Global | 15 % pour tous | 90.000 | 510.000 |
| Par partenaire | Partenaire A à 12 %, B à 20 % | A : 72.000 ; B : 120.000 | A : 528.000 ; B : 480.000 |
| Par catégorie | SUV à 18 %, berline à 12 % | SUV : 108.000 | SUV : 492.000 |

**Snapshot.** Une réservation du 20 juin est fixée à 15 %. Le taux passe à 18 % le 1er juillet. Si le calcul est refait sans snapshot en août, la commission apparaît à 108.000 au lieu de 90.000, soit un écart de 18.000 TND sur la même location. C'est le risque que le snapshot évite. Le snapshot est une exigence technique indépendante du choix du modèle : il doit exister dans tous les cas.

**Conséquences techniques.** Pas de colonne de taux sur `bookings` aujourd'hui. Le modèle par partenaire impose de lier `vehicles` à `partners` (T-C03). Le modèle par catégorie impose une table de catégories absente des migrations lues.

### 2.2 D2 — Reversements

**Seule la fréquence mensuelle est documentée** (recommandée, virement). Les autres éléments documentés sont : relevés, délai de contestation, virement manuel ou automatique. Aucune fréquence hebdomadaire ou par location n'est documentée : je ne les compare donc pas comme des options.

**Exemple (hypothétique) pour le partenaire A, juin.** Trois locations de 600.000, 450.000 et 900.000 TND, soit 1.950.000 TND brut. Commission à 15 % : 292.500 TND. Montant dû : **1.657.500 TND**, avant remboursements et ajustements. Le virement a lieu début juillet.

**Rapprochement à construire.** La formule ci-dessous est une proposition de cadrage : elle suppose que le remboursement est imputé au partenaire, ce qui n'est pas décidé.

| Élément | Source | Exemple (location de 600.000 TND, remboursement partiel de 100.000 TND) |
|---|---|---|
| Paiement client encaissé | `payments` `CAPTURED` | 600.000 |
| Commission Elite | calcul D1, snapshot | 90.000 |
| Remboursement client | D3, écriture de remboursement | 100.000 |
| Montant dû au partenaire | `[PROPOSITION]` paiement − commission − remboursement imputé | 410.000 |
| Reversement effectué | D2, module T-G03 | 410.000 si le virement est fait |
| Historique | grand livre à ajout seul (T-G02) | une écriture par événement |

**Variantes non documentées.** Imputer le remboursement à la commission, au partenaire, ou les partager : aucune option documentée. C'est une question à poser au propriétaire (§7).

**Conséquences techniques.** Pas de module de reversement. Pas de grand livre. `payments.refunded_amount` (V6, `NOT NULL` depuis V17) reste à 0 tant que D3 n'est pas implémentée. Le reversement dépend de D11 pour l'exécution (virement via prestataire ou manuel).

### 2.3 D3 — Annulation, remboursement, no-show

Options documentées : grille par délai avant départ (recommandée) ; remboursement partiel ; traitement du no-show ; calcul et écritures inverses. **Aucune valeur de seuil n'est documentée.**

**Exemple de grille, purement illustratif, non proposé.** Plus de 7 jours avant le départ : remboursement de 100 %. Entre 2 et 7 jours : 50 %. Moins de 48 heures : 0 %. Sur une location payée 600.000 TND : 600.000, 300.000 ou 0 TND.

| Cas | État réel | Manque | Décision |
|---|---|---|---|
| Annulation client avant confirmation | `[CODE]` `PENDING → CANCELLED` autorisé. Un paiement encaissé à ce stade n'a pas de règle de remboursement. | Règle de remboursement, ordre paiement / validation | D3, D5 |
| Annulation client après confirmation | `[CODE]` `CONFIRMED → CANCELLED` possible, sans pénalité ni remboursement (constat de D3). | Grille, écriture de remboursement | D3 |
| Refus par Elite Tunisia | `[CODE]` `PENDING → REJECTED`. Si un paiement est encaissé, le remboursement intégral n'est pas documenté. | Règle de remboursement en cas de refus | D3, D10 |
| Annulation par le partenaire | `[NON IMPLÉMENTÉ]` Aucun rôle partenaire. Aucune option documentée. | Règle entière | D7, D3 |
| Annulation après paiement | `[CODE]` Paiement `CAPTURED` possible ; `refunded_*` jamais alimentés. | Remboursement total et partiel, écritures | D3, D11 |
| No-show | `[NON IMPLÉMENTÉ]` Aucun état `NO_SHOW` dans `BookingStatus`. L'option « traitement du no-show » existe sans valeur. | État, règle, paiement retenu | D3 |
| Remboursement partiel | `[DOC]` Option documentée. `[NON IMPLÉMENTÉ]` Écritures. | Montant, imputation | D3, D2 |

**Ce qui n'est pas inventé.** Aucune pénalité, aucun seuil et aucune règle de remboursement par défaut n'est proposé. La grille reste à fixer par le propriétaire.

### 2.4 D5 — Comparaison des modes de paiement

Quatre options sont examinées. Trois sont documentées dans la roadmap §15 et le plan (paiement total, acompte différé, acompte à la validation). La quatrième, le paiement sur place, n'apparaît que dans le benchmark (BM-D3) et **n'est pas une option du registre**.

| Critère | Paiement total `[DOC]` | Acompte différé `[DOC]` | Acompte à la validation `[DOC, recommandé]` | Paiement sur place `[BENCHMARK seul]` |
|---|---|---|---|---|
| Parcours client | Paie l'intégralité du devis en une fois. | Paie un montant partiel, plus tard que la validation. | Paie un acompte quand la demande est validée, puis le solde. | Ne paie pas en ligne, ou seulement certains frais. |
| Parcours administratif | Valide, puis confirme. | Valide, attend l'acompte, puis confirme. | Valide, reçoit l'acompte, confirme. Suit le solde. | Valide et confirme sans encaissement en ligne. |
| Moment de confirmation | À fixer : après encaissement ou après validation. | À fixer, probablement après l'acompte. | Après l'acompte. | Après validation seule. |
| Risque d'annulation | Refus après paiement : remboursement intégral à régler. | Acompte payé puis solde non payé. | Solde non payé, dette client à gérer. | Risque de no-show élevé. Sans règle, la location est perdue. |
| Remboursements | Remboursement intégral ou partiel (D3). | Sur l'acompte et sur le solde séparément. | Sur chaque tranche. | Hors plateforme ; trace à définir. |
| Modèle de données | 1 paiement `CAPTURED` par devis. | Au moins 2 paiements par devis. | Au moins 2 paiements par devis. | Aucun statut « payé sur place » : `PaymentStatus` contient seulement `PENDING`, `AUTHORIZED`, `CAPTURED`, `FAILED`, `REFUNDED`, `CANCELLED`. |
| Paiements `CAPTURED` par devis | 1 | 2 ou plus | 2 ou plus | 0 sur la plateforme |
| Compatibilité avec l'invariant V21 | **Compatible** en l'état. | **Incompatible** sans révision de l'invariant. | **Incompatible** sans révision de l'invariant. | Pas de conflit de comptage, mais réconciliation à définir. |

**Constat sur l'invariant V21.** La règle « un seul `CAPTURED` par devis » est contrôlée par le code (`PaymentService`, lignes 48 et 51) et sera imposée en base par l'index unique partiel `uq_payments_one_captured_per_quote` (V21, proposée et non appliquée). Le contrôle applicatif n'est pas atomique : deux requêtes concurrentes peuvent passer le contrôle. Un modèle acompte + solde produit deux `CAPTURED` pour un même devis : **la recommandation « acompte à la validation » est donc incompatible avec V21 telle qu'écrite**. La révision de l'invariant (par exemple par tranche) est une conception à faire **avant** l'application de V21. Ce document ne tranche pas cette révision.

**Pourcentage et moment de l'acompte.** Non documentés (« pourcentage et moment » figurent dans la liste des points à décider, sans valeur). Ils ne sont pas proposés ici.

**Sens de « acompte différé ».** Les sources ne définissent pas précisément cette option. Le propriétaire doit la préciser avant comparaison définitive.

**Paiement sur place.** Le benchmark (BM-D3, [international-benchmark-and-target-architecture.md](./international-benchmark-and-target-architecture.md), ligne 779) le mentionne. Il indique aussi que Hertz.tn encaisse les frais optionnels sur place (ligne 140). Cette référence sert de comparaison, pas de règle pour Elite Tunisia. Son adoption relève du propriétaire.

### 2.5 Autres points de flux financiers

- **Suppression de devis.** `payments.quote_id` est en `ON DELETE CASCADE` (V6). Supprimer un devis supprime ses paiements. La proposition V22 (`database/proposed/V22__payments_quote_fk_restrict.sql`) passe cette clé en `RESTRICT`. Elle n'est pas appliquée.
- **Facturation de suppléments.** `createReturnChargeInvoice` crée une facture pour les suppléments au retour. Ce flux n'est pas une facture de location.

---

## 3. Paiement et facturation

### 3.1 D11 — Grille de sélection du prestataire de paiement

**Aucun prestataire n'est nommé dans les sources.** Cette grille ne désigne aucun fournisseur et n'affirme aucune capacité. Chaque réponse doit être prouvée par le prestataire lui-même (documentation, sandbox, contrat).

| Critère | Pourquoi pour Elite Tunisia | Question à poser au prestataire | Preuve attendue |
|---|---|---|---|
| Acceptation des paiements en Tunisie | Clients locaux, devise TND | Quels moyens de paiement sont acceptés pour un marchand établi en Tunisie ? | Liste écrite, test sandbox |
| Cartes bancaires acceptées | Parcours de paiement | Quels schémas de cartes, en TND et en devises ? | Documentation et tests |
| Encaissement centralisé | Les fonds passent par Elite avant reversement | Le prestataire encaisse-t-il pour le compte d'Elite ? | Contrat, description des flux |
| Séparation des fonds | Protection des montants partenaires | Les fonds d'Elite et ceux des partenaires sont-ils séparés ? Comment ? | Description écrite |
| Paiements récurrents | Seulement si D5 retient un modèle de tranches | Gestion d'un solde ultérieur à un acompte ? | Documentation |
| Remboursements (partiel et total) | Exigé par D3 | Remboursement partiel d'un paiement capturé ? Délai ? | API et sandbox |
| Webhooks signés | Confirmation fiable sans polling | Événements disponibles ? Signature vérifiable ? | Documentation et rejeu |
| Idempotence | Éviter les doubles encaissements | Clé d'idempotence ? Durée de rétention ? | Documentation et test |
| Réconciliation | Rapprochement avec le grand livre | Export des transactions ? Identifiant stable ? | Export de test |
| Frais | Impact direct sur la commission nette | Grille des frais, frais de remboursement, frais de virement ? | Grille signée |
| Délais de règlement | Impact sur le reversement (D2) | Délai entre encaissement et versement sur le compte d'Elite ? | Document contractuel |
| Reversements aux partenaires | Marketplace | Le prestataire sait-il payer des tiers ? Coût ? | Documentation |
| API et documentation | Intégration | Documentation publique, versionnement, SDK ? | Lien et version |
| Environnement sandbox | Tests sans argent réel | Sandbox disponible, données de test ? | Accès sandbox |
| Conformité et autorisation | Exigence légale à vérifier | Quelles autorisations sont requises pour opérer en Tunisie ? | Avis juridique (question, pas de règle ici) |

**Lien avec le code.** `SimulatedPaymentProvider` ne doit pas être actif en production. Le feature flag de T-F02 est nécessaire.

### 3.2 D6 et D12 — Dépendances de facturation

**Chaîne actuelle.**

```
Demande client
  └─ Réservation (PENDING)            [CODE] BookingEntity
       └─ Devis (booking_id NOT NULL) [CODE] V5, QuoteEntity
            └─ Paiement (quote_id NOT NULL, booking_id nullable) [CODE] V6
                 └─ Facture (booking_id et quote_id nullables, pas d'unicité par réservation) [CODE] V7
                      └─ Avoir : [NON IMPLÉMENTÉ] non identifié dans les migrations lues
```

**Dépendances entre décisions.**
- D12 (cardinalité) dépend de D6 (règles de facturation) : une facture par réservation ou plusieurs facture(s) par réservation.
- D6 (TVA) commande le format de la facture et le calcul de la commission (D1 : TVA sur la commission ?).
- D5 (acompte) crée des factures d'acompte potentielles : leur régime doit être connu avant T-H01.
- Les avoirs dépendent de D3 (remboursements) et de D6 (mentions).

**Constats techniques.**
- `invoices.booking_id` n'est plus unique depuis V7 (la contrainte V1 a disparu avec la table renommée). Une réservation peut recevoir plusieurs factures sans contrainte en base. La contrainte est prévue par T-H01 (V33 indicatif).
- La séquence de numérotation (`invoice_seq`) ne revient pas en arrière lors d'un rollback. Des trous de numérotation sont possibles.
- Le montant est en `NUMERIC(12,3)`, soit des millimes. L'arrondi n'est pas documenté.
- `default-tax-amount` est un montant fixe, sans taux.

**Questions pour un expert-comptable tunisien** (sans réponse proposée ici) :
1. Quel régime de TVA s'applique à la location de véhicule, avec et sans chauffeur ?
2. La commission de la plateforme est-elle soumise à TVA ? Sous quel taux ?
3. Une facture doit-elle être émise au nom d'Elite Tunisia ou du partenaire ? Un mandat de facturation est-il nécessaire, et sous quelle forme ?
4. Quelles mentions sont obligatoires sur une facture, sur un avoir, sur une facture d'acompte ?
5. La numérotation doit-elle être continue ? Un numéro consommé par une transaction annulée pose-t-il problème ?
6. Quelle règle d'arrondi en TND (millimes) ?
7. Une facture d'acompte est-elle due à l'encaissement de l'acompte ou à la location ?
8. Quelle forme prend un avoir en cas de remboursement partiel ou total ?
9. Quelle durée de conservation des factures et des justificatifs ?
10. Quelle date retenir pour la facture (fuseau Tunis, date d'encaissement ou de location) ?

---

## 4. Modèle de disponibilité

### 4.1 Séparer quatre notions

| Notion | Description | Représentation actuelle | Représentation cible |
|---|---|---|---|
| État opérationnel du véhicule | Condition physique ou administrative | `vehicles.status` : `AVAILABLE`, `RESERVED`, `RENTED`, `MAINTENANCE`, `UNAVAILABLE` | Ne décrit pas une période. Ne doit plus servir à bloquer des dates futures. |
| Disponibilité temporelle | Possibilité de réserver sur une plage de dates | Contrainte `EXCLUDE` (V4) sur `bookings` ; contrôle applicatif `overlaps` | Calcul à partir des réservations et des blocages, sans dépendre de `RENTED`. |
| Réservations | Engagements sur une plage | `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, … | Seuls `CONFIRMED` et `IN_PROGRESS` bloquent (V4). |
| Blocages | Maintenance, indisponibilité planifiée | **Absent** : aucune table dans V1–V20. | Table de blocages avec contrainte `EXCLUDE` (T-D01, V25 indicatif). |

**Point à clarifier.** `RESERVED` est défini dans `VehicleStatus` et dans la contrainte V3, mais aucune règle ne le documente. Son sens doit être défini avant T-A05 ou T-D01, sinon il reproduira le même type de confusion que `RENTED`.

### 4.2 Règles actuelles

- `BookingService.rentable` exige `VehicleStatus.AVAILABLE` (lignes 193–199). Conséquence : un véhicule `RENTED` est refusé pour une réservation future, même si ses dates ne se chevauchent pas. C'est le problème NEW-01 (T-A05).
- `BookingService.BLOCKING` = `CONFIRMED`, `IN_PROGRESS` (ligne 19). `overlaps` contrôle les chevauchements (lignes 53–54).
- La contrainte V4 `bookings_no_blocking_overlap` (`EXCLUDE USING gist`, `tstzrange(starts_at, ends_at, '[)')`) couvre seulement `CONFIRMED` et `IN_PROGRESS`. Les demandes `PENDING` peuvent se chevaucher.

### 4.3 Exemples de dates (hypothétiques, fuseau Tunis)

Véhicule `V-001`.

| Cas | Réservations | Résultat attendu (cible) | Résultat actuel |
|---|---|---|---|
| A — Location en cours | `IN_PROGRESS` du 10 juin 10:00 au 14 juin 10:00 | Accepté (véhicule `RENTED`). | Accepté. |
| B — Réservation future après la location | Demande `CONFIRMED` du 14 juin 10:00 au 16 juin 18:00 | **Accepté** : l'intervalle `[)` ne chevauche pas A à la borne. | **Refusé** : `rentable` exige `AVAILABLE` (NEW-01). |
| C — Chevauchement | Demande du 13 juin au 15 juin | **Refusé** : chevauche A. | Refusé par `overlaps` (A est `IN_PROGRESS`). |
| D — Maintenance planifiée | Blocage du 20 au 22 juin (cible T-D01) | Une demande du 19 au 21 juin est **refusée**. | Non modélisable : aucune table de blocage. |
| E — Deux demandes `PENDING` | Deux demandes du 1er au 3 juillet | Les deux peuvent exister. Une seule peut être confirmée. | Les deux existent. La confirmation de la seconde doit échouer sur `EXCLUDE` (SQLSTATE `23P01`). `[À VÉRIFIER]` : mapping de cette erreur vers une erreur métier. |

### 4.4 Risque de retard de retour

Si le retour est en retard, la réservation `IN_PROGRESS` a dépassé son `ends_at` alors que le véhicule est toujours chez le client. Une fois T-A05 implémentée, une réservation future pourrait être acceptée sur ce véhicule. **Ce risque est créé par la correction de T-A05 elle-même.** La règle de retard (durée de tolérance, requalification de la réservation, blocage) doit être décidée avant d'ouvrir l'accès à T-A05. Aucune règle de retard n'est documentée.

### 4.5 Garanties de concurrence nécessaires

| Garantie | Mécanisme | Statut |
|---|---|---|
| Pas de deux réservations bloquantes qui se chevauchent | `EXCLUDE` sur `bookings` (V4) | `[CODE]` |
| Ordre de verrouillage cohérent véhicule → réservation | Verrou applicatif | T-A02, `[À VÉRIFIER]` reproduction |
| Un seul `CAPTURED` par devis | Index unique partiel (V21) | `[PROPOSITION]`, non appliquée |
| Pas de chevauchement de blocages | `EXCLUDE` sur la table de blocages | `[PROPOSITION]` T-D01 |
| Contrôle applicatif de disponibilité | `overlaps` / `rentable` | `[CODE]`, mais non atomique seul |

La contrainte `EXCLUDE` est la dernière ligne de défense. Le contrôle applicatif sert à l'expérience utilisateur, pas à la sécurité des données.

---

## 5. Partenaires, chauffeurs et matrice de permissions

### 5.1 Constats

- Aucun `partner_id` sur `vehicles` dans V1–V20 : l'isolation des partenaires n'est pas modélisée.
- Rôles existants (V1) : `SUPER_ADMIN`, `MANAGER`, `AGENT`, `CONCIERGE`, `ACCOUNTANT`, `CUSTOMER`. Aucun `PARTNER_*` ni `DRIVER`.
- Les permissions existantes sont des clés (`bookings:read`, `quotes:accept`, …) dans `permissions` et `role_permissions`.
- Le contrôle Angular (routes, gardes) n'est pas une sécurité. Chaque règle doit être appliquée côté serveur. `[À VÉRIFIER]` : la configuration de sécurité backend n'a pas été relue dans cette passe.

### 5.2 Matrice de permissions cible

Légende : **Existant** = présent dans les migrations ; **Proposé** = à construire ; **Non prévu** = pas de règle documentée.

| Action | Client (`CUSTOMER`) | Staff (`SUPER_ADMIN`, `MANAGER`, `AGENT`) | `ACCOUNTANT` | `CONCIERGE` | Partenaire (proposé) | Chauffeur (proposé) |
|---|---|---|---|---|---|---|
| Consulter le catalogue | Existant | Existant | Existant | Existant | Proposé (ses véhicules) | Non prévu |
| Créer une demande de devis | Existant | Existant (`quotes:create`) | Non | Non | Non prévu | Non |
| Accepter / refuser un devis | Existant (`quotes:accept`, `quotes:reject`) | Non | Non | Non | Non | Non |
| Voir ses réservations | Existant (`bookings:read`) | Existant | Existant (`bookings:read`) | Existant | Proposé (véhicules propres uniquement) | Proposé (missions propres, T-I02) |
| Voir les réservations d'autrui | Non | Existant | Existant | Existant | **Interdit** | **Interdit** |
| Confirmer / refuser une réservation | Non | Existant (`bookings:manage`) | Non | Non prévu | Non prévu | Non |
| Annuler une réservation | Existant (`bookings:cancel`, ses réservations) | Existant | Non | Non prévu | Non prévu (D3, D7) | Non |
| Gérer ses véhicules | Non | Existant | Non | Non | Proposé (T-C03) | Non |
| Voir les finances (commission, reversements) | Non | Existant (partiel) | Existant (partiel) | Non | Proposé : **ses propres relevés uniquement** (T-G03) | Non |
| Modifier `commission_rate` | Non | Non prévu | Non | Non | **Interdit** | Non |
| Voir les factures | Existant (V13, ses factures) | Existant | Existant | Non prévu | Proposé (factures de ses réservations, D6) | Non |
| Voir les documents d'un autre partenaire | Non | Existant (administration) | Non | Non | **Interdit** | Non |
| Créer une facture | Non | Existant | Non prévu | Non | Non | Non |
| Paiement | Existant (V10, ses paiements) | Existant | Non prévu | Non | Non | Non |
| Missions de transfert | Non | Existant | Non | Non | Non | Proposé (ses missions, T-I02) |

Ces lignes sont des **intentions de cadrage**. Elles doivent être validées par le propriétaire avant implémentation. Les cellules « Non prévu » signalent une absence de règle documentée, non une interdiction.

### 5.3 Isolation des partenaires

- Un partenaire ne voit que les véhicules et réservations dont `partner_id` correspond à son identifiant (T-C02). Ce filtre doit être appliqué dans les requêtes serveur, pas dans Angular.
- Un partenaire ne doit jamais recevoir `commission_rate` d'un autre partenaire.
- Le préfixe `/api/partner/**` n'existe pas. La matrice d'isolation de T-C02 (A→B refusé, 100 % des endpoints) reste à construire.

---

## 6. D13 et T-I01

D13 (exclusion des chauffeurs sur transferts) est classée **Reportable** dans [business-decisions-pending.md](./business-decisions-pending.md). Pourtant, T-I01 (P1 dans la roadmap) implémente l'exclusion. Les deux positions sont incompatibles si T-I01 est planifiée avant production.

**Constat technique.** `TransferService` (lignes 116, 121–123) et `DriverAssignmentService` (ligne 40) contrôlent le chevauchement chauffeur au niveau applicatif. `TransferRepository` (lignes 23–26) porte les requêtes associées. Aucune contrainte `EXCLUDE` sur les chauffeurs n'existe dans V1–V20.

**Lecture de cohérence.** Une exclusion en base est une contrainte technique, indépendante du statut juridique des chauffeurs (D9). Elle peut être reportée seulement si le contrôle applicatif est jugé suffisant, ce qui n'est pas le cas au sens de T-I01 (« refusé par la base »). Le propriétaire doit donc trancher : soit D13 est sortie de « Reportable » et T-I01 est planifiée, soit T-I01 est reportée et D13 reste reportée. Ce choix n'est pas fait ici.

---

## 7. Décisions ouvertes à soumettre au propriétaire

Seules les questions dont aucune option n'est documentée sont listées ici. Les autres décisions figurent dans [business-decisions-pending.md](./business-decisions-pending.md).

1. **Remboursement et imputation** : qui porte un remboursement (plateforme, partenaire, partage) ? Aucune option documentée.
2. **Grille d'annulation** : seuils et pourcentages (D3). La grille par délai est documentée, pas ses valeurs.
3. **Annulation par le partenaire** : est-elle permise ? Dans quelles conditions ? Aucune option documentée.
4. **Refus après paiement** : remboursement intégral automatique ou manuel ?
5. **No-show** : état, délai de constatation, paiement retenu ou non.
6. **Acompte différé** : définition précise de l'option.
7. **Paiement sur place** : ajout à D5 ou rejet ? Hors registre actuel.
8. **Pourcentage et moment de l'acompte** (D5).
9. **Retard de retour** : tolérance et conséquences (§4.4). Aucune règle documentée.
10. **Statut de publication des véhicules** : processus de validation par Elite (§1, étape 2).
11. **Statut de D13** : Reportable ou à traiter avant production (§6).
12. **Sens de `RESERVED`** (`VehicleStatus`) : état opérationnel distinct ou non utilisé.
13. **Règles tarifaires** : saison, durée, zone (T-D02) ne figurent dans aucune décision D1–D15. Un registre est à créer.

---

## 8. Recommandations techniques (sans décision métier)

1. **Trancher D5 avant V21.** La recommandation actuelle (acompte à la validation) est incompatible avec l'invariant V21. Appliquer V21 avant cette décision figerait un modèle à réécrire.
2. **Traiter le snapshot de commission comme exigence technique**, indépendamment du modèle de taux choisi (D1).
3. **Ne pas coder les flux financiers** (T-F01, T-G01, T-G03, T-H01) avant D1, D2, D3, D5, D6, D11 et D12. Le code écrit avant ces décisions serait à réécrire.
4. **Isoler les partenaires avant toute finance partenaire** (T-C01, T-C02) : sans cela, les relevés (T-G03) n'ont pas de périmètre sûr.
5. **Séparer le statut de publication de l'état opérationnel** avant T-A05 et T-D01.
6. **Décider la règle de retard** avant d'ouvrir la disponibilité future sur un véhicule en location (§4.4).
7. **Ne pas introduire de prestataire, de Redis ou de service externe** sans décision (T-F02, T-L01b).

---

## 9. Limites de cette analyse

- Migrations V1–V20 lues par extraits et greps ciblés, non intégralement relues dans cette passe. Les points `[À VÉRIFIER]` le signalent.
- Aucun test exécuté. Les comportements décrits comme `[CODE]` sont des lectures de code, non des preuves d'exécution.
- Aucune réglementation tunisienne (fiscalité, paiement, transport) n'a été consultée ni proposée.
- Les montants et taux sont hypothétiques.

## 10. Verdict

**READY FOR OWNER DECISIONS.** Le modèle est cadré, mais la plupart des flux financiers et de la disponibilité dépendent de décisions non prises. Aucune implémentation n'est autorisée par ce document.

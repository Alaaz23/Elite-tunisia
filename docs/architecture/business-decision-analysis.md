# Analyse des décisions métier — Elite Tunisia

Statut : **Phase 0.2, documentaire**. Ce document analyse les décisions métier ouvertes pour préparer la décision du propriétaire du produit. **Aucune décision n'est adoptée ici.** Les recommandations sont techniques et restent des propositions.

Documents liés : [business-decisions-pending.md](./business-decisions-pending.md) (registre), [implementation-roadmap.md](./implementation-roadmap.md) (§15, référence canonique D1 à D15), [master-project-completion-plan.md](./master-project-completion-plan.md), [technical-work-reconciliation.md](./technical-work-reconciliation.md).

## 0. Règles de lecture

- **Options** : uniquement celles présentes dans les sources citées. Aucune option n'a été ajoutée. Si aucune option n'est documentée, le texte le signale.
- **Exemples chiffrés** : illustratifs, en dinars tunisiens (TND), avec des pourcentages fictifs. Ils ne représentent ni une grille, ni un taux, ni une règle validée.
- **Fiscalité** : aucune règle de TVA ou de facturation tunisienne n'est proposée. Toute question fiscale relève d'un expert-comptable tunisien (roadmap §15, D6).
- **Juridique** : aucune conclusion juridique. Les points de droit sont renvoyés à un avis qualifié.
- **Preuves code** : les constats viennent de lectures de fichiers réalisées pendant cette phase. Ils sont marqués « à confirmer » lorsqu'une recherche complète n'a pas été faite.
- **Numérotation** : D1 à D15 = roadmap §15. `BM-Dx` = benchmark §10.4. Les deux séries ne sont pas équivalentes.

## 1. Synthèse

| ID | Question | Bloque le développement de | Échéance documentée | Décision humaine | Bloquant pour le développement ? |
|---|---|---|---|---|---|
| D1 | Modèle et snapshot de la commission | Module G (snapshot de commission) | Étape D (roadmap §15) | Oui | **Oui** |
| D2 | Fréquence et méthode de reversement | T-G03 | Avant T-G03 | Oui | **Oui** (pour T-G03) |
| D3 | Grille d'annulation, remboursement, no-show | T-E03, T-F01 | Bloquante (roadmap §15) | Oui | **Oui** |
| D5 | Paiement total ou acompte, et moment | T-F01 | Bloquante (roadmap §15) | Oui | **Oui** |
| D6 | TVA, numérotation, mentions de facture | T-H01 | Bloquante (roadmap §15) | Oui, après avis expert-comptable | **Oui** |
| D10 | Motifs de refus et confirmation | T-E01 | Bloquante (roadmap §15) | Oui | **Oui** |
| D11 | Prestataire de paiement | T-F02 | Bloquante (roadmap §15) | Oui | **Oui** |
| D12 | Cardinalité des factures et facturation depuis le devis | T-H01 | Bloquante, avec D6 | Oui | **Oui** |
| D7 | Contrats partenaires | Module B | Avant production | Oui, avis juridique | Non (avant production) |
| D8 | Assurance et sinistres | Modules C, E | Avant production | Oui, avis juridique et assureur | Non (avant production) |
| D13 | Exclusion des chauffeurs sur transferts | T-I01 | **Statut incohérent à confirmer** | Oui | **À confirmer** |
| P-01 | Routes placeholder | T-L04 | Avant production | Oui | Non (bloque seulement T-L04) |
| P-02 | Référence « D-02 » de T-A05 | Affectation de T-A05 | Avant planification du lot | Oui (ou correction documentaire) | Non pour le code, **oui pour la planification de T-A05** |

Décisions **non analysées** dans cette phase, mais restant reportables selon la roadmap : D4 (cautions), D9 (statut des chauffeurs), D14 (canaux de notification), D15 (nettoyage des migrations inertes).

## 2. Ordre de dépendance

```
D1 (commission) ──► D2 (reversements) ──► exécution via D11
  │                    ▲
  └──► D6 (TVA sur commission ?)
D11 (prestataire) ──► D3 (remboursement partiel) ──► D5 (acompte)
D5 (acompte) ◄──► D3 (annulation) ◄──► D10 (confirmation et refus)
D6 (TVA, numérotation) ◄──► D12 (cardinalité des factures)
D7 (contrats) ◄──► D8 (assurance) ◄──► D3 (sinistre, no-show)
```

Conséquence : **D5 et D11 doivent être arbitrés ensemble avant T-F01 et T-F02**. **D6 et D12 doivent être arbitrés ensemble avant T-H01.** Développer T-F01 avant D5 risque une réécriture, en particulier à cause de l'invariant de paiement déjà implémenté (voir D5, section 3.4).

## 3. Analyse détaillée

### 3.1 D1 — Commission

1. **Question** : quel taux de commission, et comment est-il figé pour chaque réservation ?
2. **Options documentées** (source : [marketplace-gap-analysis.md](./marketplace-gap-analysis.md) §13, « Base de calcul ») : taux global ; taux par partenaire ; taux par catégorie. Le snapshot du taux sur chaque réservation est documenté comme exigence de traçabilité (§7.5).
3. **Exemple Elite Tunisia (illustratif)** : une location de 1 000 TND. Avec un taux global de 15 %, la commission est de 150 TND. Avec un taux partenaire de 10 %, elle est de 100 TND. Si le taux change le mois suivant, seul un snapshot permet de savoir quel taux s'appliquait à la réservation d'origine.
4. **Avantages** :
   - taux global : simple, un seul paramètre ;
   - taux par partenaire : permet de négocier avec chaque partenaire ;
   - taux par catégorie : aligne la commission sur la marge par type de véhicule.
5. **Inconvénients** :
   - taux global : aucune souplesse commerciale ;
   - taux par partenaire : complexité, risque d'incohérence entre partenaires ;
   - taux par catégorie : multiplication des règles à maintenir.
6. **Conséquences financières** : la commission est le chiffre d'affaires d'Elite. Sans snapshot, un changement de taux modifie rétroactivement les montants dus aux partenaires (voir D2).
7. **Conséquences techniques** :
   - colonne de taux figée sur la réservation (ou sur le paiement) au moment du paiement, à définir ;
   - `partners.commission_rate NUMERIC(5,2)` existe (V1, [V1__initial_schema.sql](../../database/migrations/V1__initial_schema.sql), ligne 187), mais n'est relié ni à `vehicles` ni à `bookings`. **Constat confirmé.**
   - arrondi et précision monétaire à définir avec D6.
8. **Tables, entités, services** : table `partners` (V1), `vehicles`, `bookings`, futur module de reversement ; services `BookingService` et `PaymentService` pour le moment de figement.
9. **Dépendances** : D2 (le reversement dépend de la commission), D6 (TVA appliquée ou non à la commission : question fiscale à poser à l'expert-comptable), D11.
10. **Risque de décision tardive** : des réservations historiques sans taux figé deviennent impossibles à recalculer. Une migration de backfill serait alors nécessaire, avec les risques documentés pour les données historiques (voir [postgres-stabilization-report.md](../postgres-stabilization-report.md)).
11. **Recommandation technique** (non décisionnelle) : quelle que soit l'option retenue, figer le taux sur la réservation ou le paiement. La roadmap recommande « taux par partenaire avec défaut global ». Ne pas coder de taux en dur.
12. **Questions au propriétaire** :
   - Quel modèle de taux : global, par partenaire, par catégorie ?
   - Quel événement fige le taux : confirmation, paiement, fin de location ?
   - Quel taux initial, et qui peut le modifier ?

### 3.2 D2 — Reversements aux partenaires

1. **Question** : à quelle fréquence, par quel moyen et avec quels contrôles Elite reverse-t-il aux partenaires ?
2. **Options documentées** (source : marketplace-gap-analysis §7.5 et §13) : reversement mensuel par virement (recommandé par la roadmap) ; relevés ; délai de contestation (durée non documentée) ; virement manuel ou automatique.
3. **Exemple Elite Tunisia (illustratif)** : un partenaire a 5 réservations en mars pour 4 000 TND bruts. Avec une commission de 15 %, Elite retient 600 TND. Le reversement serait de 3 400 TND, versé en avril après délai de contestation. Le délai et la date restent à définir.
4. **Avantages** :
   - mensuel : un seul virement par partenaire et par mois, traçable ;
   - relevé : transparence pour le partenaire ;
   - virement manuel : contrôle humain avant chaque sortie d'argent.
5. **Inconvénients** :
   - mensuel : un réclamation après reversement peut nécessiter une régularisation ;
   - automatique : dépend du prestataire (D11) et de la gestion des erreurs ;
   - manuel : charge opérationnelle, risque d'erreur.
6. **Conséquences financières** : Elite détient des montants qui ne lui appartiennent pas entièrement. La séparation des fonds est une exigence documentée (BM-D3), mais **son traitement juridique et bancaire n'est pas documenté et relève d'un avis qualifié**.
7. **Conséquences techniques** : aucun module de reversement n'existe. La marketplace-gap-analysis le constate (« Pas de payouts »). Il faudra des entités de reversement, un statut, une idempotence des virements et un lien avec les réservations annulées après reversement.
8. **Tables, entités, services** : à créer (aucune table `payouts` n'a été trouvée) ; `bookings`, `payments`, `partners`.
9. **Dépendances** : D1 (montant), D3 (remboursement après reversement), D11 (exécution), D6 (éventuelle facture de reversement, question fiscale).
10. **Risque de décision tardive** : des reversements manuels hors système, sans traçabilité, rendent les contrôles impossibles.
11. **Recommandation technique** : commencer par un cycle mensuel avec relevé et virement manuel (proposition de la roadmap), sans automatisation tant que D11 n'est pas choisi.
12. **Décision attendue** : fréquence ; délai de contestation ; virement manuel ou automatique ; traitement des réservations annulées après reversement.
13. **Calendrier** : la roadmap indique « avant T-G03 ». Le plan maître indiquait « avant production » : ce dernier est **obsolète** (voir [business-decisions-pending.md](./business-decisions-pending.md)).

### 3.3 D3 — Annulation, remboursement et no-show

1. **Question** : quelle grille d'annulation et de remboursement, et que se passe-t-il en cas de no-show ?
2. **Options documentées** (sources : marketplace-gap-analysis §7.8 et §13 ; benchmark BM-D9 « barème d'annulation ») : grille par délai avant départ (recommandée par la roadmap) ; remboursement partiel ; traitement du no-show ; écritures d'annulation et de remboursement.
3. **Exemple Elite Tunisia (mécanique seulement, pourcentages fictifs)** : un client a versé un acompte de 300 TND pour une location de 1 000 TND. S'il annule 10 jours avant, la grille pourrait prévoir zéro pénalité. S'il annule 2 jours avant, elle pourrait retenir une fraction de l'acompte. Ces pourcentages ne sont pas documentés et ne sont pas proposés ici.
4. **Avantages** :
   - grille par délai : règle lisible par le client et l'équipe ;
   - remboursement partiel : équilibre entre client et Elite ;
   - règle no-show : protège contre la perte d'un véhicule réservé.
5. **Inconvénients** :
   - grille : risque de litige sur les seuils ;
   - remboursement partiel : dépend du prestataire (D11) ;
   - no-show : risque de contestation si la règle n'est pas claire.
6. **Conséquences financières** : montant remboursé, montant retenu, et **sort de la commission sur une annulation avec pénalité : aucune source ne le documente**. C'est une question à poser en plus de D1.
7. **Conséquences techniques** (constats) :
   - `BookingService.customerCancel` n'applique aucune pénalité ni remboursement (marketplace-gap-analysis §7.8) ;
   - les colonnes `payments.refunded_amount` (V6, `numeric(12,3)`, `DEFAULT 0`, puis `NOT NULL` en V17) et `refunded_at` ne sont pas alimentées par le flux d'annulation. **À confirmer par recherche de code complète avant T-E03.**
8. **Tables, entités, services** : `bookings`, `payments`, `BookingService.customerCancel`, `PaymentService`.
9. **Dépendances** : D5 (acompte), D11 (remboursement partiel), D8 (sinistre et no-show), D1 (commission sur pénalité).
10. **Risque de décision tardive** : des annulations traitées sans règle créent des litiges. Les montants historiques ne peuvent plus être justifiés.
11. **Recommandation technique** : stocker la règle appliquée (version de grille) sur la réservation au moment de l'annulation, pour que les changements futurs ne modifient pas les montants passés.
12. **Questions au propriétaire** :
   - Quels seuils et quels pourcentages, et à partir de quel délai ?
   - Le no-show est-il traité comme une annulation tardive ou comme une facturation complète ?
   - La commission reste-t-elle due sur une annulation avec pénalité ?

### 3.4 D5 — Paiement total ou acompte, et moment

1. **Question** : le client paie-t-il l'intégralité, un acompte, ou paie-t-il sur place ? Si acompte, à quel moment ?
2. **Options documentées** :
   - roadmap §15 et marketplace-gap-analysis : paiement total ; acompte différé ; acompte à la validation (recommandé par la roadmap) ;
   - benchmark BM-D3 : prépaiement intégral, acompte, **paiement sur place**. L'option « paiement sur place » n'est pas dans le registre et doit y être ajoutée.
3. **Exemple Elite Tunisia (illustratif)** : location de 1 200 TND sur 4 jours.
   - paiement total à la réservation : 1 200 TND encaissés en ligne ;
   - acompte à la validation : 30 % (360 TND) puis solde de 840 TND à la prise en charge (pourcentage fictif) ;
   - paiement sur place : 0 TND en ligne, 1 200 TND encaissés au comptoir, **donc hors système si rien n'est saisi**.
4. **Point bloquant identifié** : l'invariant implémenté en T-A04 (`PaymentService.createForQuote`, `PaymentRepository.existsByQuote_IdAndStatusIn`) et l'index unique partiel prévu en V21 imposent **un seul `CAPTURED` par devis**. Un modèle acompte + solde produit **deux `CAPTURED` pour un même devis**. Ce modèle est donc incompatible avec l'invariant actuel. **Constat confirmé dans le code et dans [V21](../../database/proposed/V21__payments_one_captured_per_quote.sql).** Ce point doit être arbitré **avant** l'application de V21.
5. **Avantages** :
   - paiement total : sécurité financière maximale ;
   - acompte : engagement client moins contraignant ;
   - sur place : pas de dépendance à un prestataire.
6. **Inconvénients** :
   - total : friction à la réservation ;
   - acompte : risque d'impayé sur le solde ;
   - sur place : aucune trace automatique, contrôle manuel nécessaire.
7. **Conséquences financières** : trésorerie, exposition à l'impayé, rétention de commission sur un acompte (voir D1).
8. **Conséquences techniques** : nouveau statut ou montant partiel ; relation entre un paiement et plusieurs encaissements ; mise à jour de l'invariant ; `quote-detail.component.ts` (`paymentLocked`) qui bloque le paiement dès qu'un paiement est en cours ou réussi.
9. **Tables, entités, services** : `payments`, `PaymentService`, `PaymentRepository`, `quote-detail.component.ts`.
10. **Dépendances** : D3 (annulation d'un acompte), D11 (encaissement partiel), D4 (caution, reportable), D1.
11. **Risque de décision tardive** : V21 appliquée puis modèle acompte = nouvelle migration de contrainte et refonte de `PaymentService`. V21 n'est **pas appliquée**, ce qui laisse encore le choix.
12. **Recommandation technique** : si le modèle acompte + solde est retenu, remplacer l'invariant « un seul `CAPTURED` » par « somme des `CAPTURED` ≤ total du devis », avec verrouillage du devis. **Ne pas implémenter avant décision.**
13. **Questions au propriétaire** :
   - paiement total, acompte, ou paiement sur place (ou combinaison par type de client ?) ;
   - si acompte : pourcentage et moment (validation, réservation, autre) ;
   - le paiement sur place doit-il être saisi dans le système ?

### 3.5 D6 — TVA, factures et fiscalité

1. **Question** : quel régime de TVA, quelle numérotation, quelles mentions obligatoires, et au nom de qui la facture est-elle émise ?
2. **Options documentées** :
   - marketplace-gap-analysis §13 : TVA en taux ou en montant fixe ; facture au nom d'Elite ou du partenaire avec mandat de facturation ;
   - benchmark BM-D2 (V1-02, §8.5) : taux de TVA, arrondi, mentions, continuité de numérotation.
   - Aucune règle tunisienne n'est documentée. **Ne pas en proposer.**
3. **Exemple Elite Tunisia (structure seulement)** : si un taux T était retenu, la TVA d'une base B serait B × T. Le taux T n'est pas documenté et n'est proposé nulle part dans ce dossier.
4. **Avantages / inconvénients** :
   - montant fixe : simple, mais ne suit pas un taux légal ;
   - taux paramétré : flexible, mais risque d'erreur sur les factures historiques si le taux change ;
   - mandat de facturation : clarifie l'émetteur, mais suppose un contrat (D7).
5. **Conséquences financières et fiscales** : **à confirmer par un expert-comptable tunisien.** Aucune conclusion ici.
6. **Conséquences techniques** (constats) :
   - `application.yml` ligne 35 : `default-tax-amount: ${QUOTE_DEFAULT_TAX_AMOUNT:0}`. La TVA est un montant fixe, sans taux, avec un défaut à 0. **Constat confirmé.**
   - `InvoiceService.createFromQuote` recopie `quote.getTaxAmount()` dans la facture.
   - numérotation par séquence PostgreSQL (V7, « invoice sequence ») : non rétroactive, un rollback peut laisser des trous. **À évaluer par l'expert-comptable.**
   - `invoices.reference` est `UNIQUE` (V7).
7. **Tables, entités, services** : `invoices`, `invoice_lines`, `InvoiceEntity`, `InvoiceService`, `application.yml`, `QuoteEntity`.
8. **Dépendances** : D12 (indissociable selon la roadmap), D1 (TVA sur la commission ? question fiscale), D7 (mandat).
9. **Risque de décision tardive** : des factures émises avec un mauvais montant de TVA ne se corrigent que par avoir, avec impact comptable et client.
10. **Recommandation technique** : ne pas coder de taux tant que l'avis de l'expert-comptable n'est pas écrit. Prévoir un paramétrage daté (taux valide à compter d'une date) pour ne pas modifier les factures passées.
11. **Questions au propriétaire / à l'expert-comptable** :
   - régime de TVA applicable à la location et à la commission ;
   - mentions obligatoires et numérotation ;
   - émetteur de la facture (Elite, partenaire, mandat) ;
   - traitement des trous de numérotation en cas d'échec.

### 3.6 D10 — Motifs de refus et confirmation

1. **Question** : quels motifs de refus, et la confirmation d'une demande est-elle manuelle ou instantanée ?
2. **Options documentées** : liste de motifs (recommandée par la roadmap §15) ; confirmation manuelle ou instantanée (benchmark BM-D8). Aucune expiration des `PENDING` : décision métier définitive (lié à T-E02, abandonnée).
3. **Exemple Elite Tunisia (illustratif)** : une demande `PENDING` pour un véhicule. L'administrateur la refuse avec le motif « véhicule indisponible » ou « dossier incomplet ». Sans liste, le motif est un texte libre non exploitable.
4. **Avantages** :
   - liste de motifs : statistiques et traçabilité ;
   - texte libre : souplesse, mais non exploitable.
5. **Inconvénients** :
   - liste : maintenance de la liste ;
   - texte libre : pas de reporting fiable.
6. **Conséquences financières** : si un paiement précède la confirmation puis que la demande est refusée, un remboursement est nécessaire (D3, D11).
7. **Conséquences techniques** (constats) :
   - la transition `PENDING` → `CONFIRMED` passe par `AdminBookingController.confirm` (route `/{id}/confirm`, autorité `bookings:manage`) ;
   - `BookingStatus.PENDING` autorise `CONFIRMED`, `REJECTED` et `CANCELLED` ([BookingStatus.java](../../backend/src/main/java/tn/elitetunisia/booking/BookingStatus.java)) ;
   - `BookingService` vérifie `canTransitionTo` avant confirmation ;
   - `BookingSummary` contient `internalNote`. **Aucun champ de motif structuré n'a été constaté** : à confirmer.
   - Point lié : `BookingService` (règle `rentable`, lignes 193 à 199) exige `VehicleStatus.AVAILABLE` (NEW-01, à re-vérifier).
8. **Tables, entités, services** : `bookings`, `BookingEntity`, `BookingStatus`, `BookingService`, `AdminBookingController`.
9. **Dépendances** : D3 (remboursement d'un refus payé), D8 (sinistre), D11 (paiement avant ou après confirmation).
10. **Risque de décision tardive** : des refus sans motif structuré rendent les statistiques et les litiges impossibles à analyser.
11. **Recommandation technique** : liste fermée de motifs avec une option « autre » commentée ; conserver la confirmation manuelle comme défaut tant que le propriétaire n'a pas répondu.
12. **Questions au propriétaire** :
   - liste des motifs de refus ;
   - confirmation manuelle ou instantanée ;
   - un paiement doit-il précéder la confirmation ?
   - délai d'expiration d'une réservation `PENDING` (et action à l'échéance : expiration automatique ou relance ?).

### 3.7 D11 — Prestataire de paiement

1. **Question** : quel prestataire de paiement pour les encaissements et les remboursements ?
2. **Options documentées** : **aucun prestataire n'est nommé** dans les sources lues. Les exigences documentées (benchmark BM-D3, roadmap §15) sont : encaissement centralisé, séparation des fonds, webhooks, remboursement partiel, devises. **La compatibilité avec le modèle marketplace reste à prouver.** Aucun fournisseur n'est recommandé dans ce dossier.
3. **Exemple Elite Tunisia (illustratif)** : un client paie 1 200 TND par carte. Le prestataire encaisse, Elite reverse ensuite au partenaire (D2). Si le prestataire ne gère pas le remboursement partiel, le remboursement d'une annulation doit être fait hors système.
4. **Avantages / inconvénients** : impossibles à comparer sans options nommées. **Critères de comparaison** proposés : coût des frais, support marketplace, séparation des fonds, webhooks, remboursement partiel, support de la devise TND (**à vérifier auprès du prestataire**).
5. **Conséquences financières** : frais de transaction, délai de versement, exposition aux litiges.
6. **Conséquences techniques** (constats) :
   - `PaymentService` appelle un fournisseur via une interface ; le fournisseur simulé ne produit que `CAPTURED` ou `FAILED`, sans webhook (constat T-A04) ;
   - idempotence couverte par `PaymentServiceIdempotenceCompatibleTest` ;
   - la page `/admin/payments` est un placeholder : « Aucun fournisseur de paiement n'est configuré » ([app.routes.ts](../../frontend/src/app/app.routes.ts)).
   - Le fournisseur simulé ne doit pas être utilisé en profil `prod` (exigence documentée dans [business-decisions-pending.md](./business-decisions-pending.md)).
7. **Tables, entités, services** : `payments`, `PaymentService`, `PaymentRepository`, adaptateur fournisseur.
8. **Dépendances** : D2, D3, D5, D1.
9. **Risque de décision tardive** : intégrer un prestataire après T-F01 oblige à reprendre `PaymentService` et les états `PENDING` bloqués sans webhook.
10. **Recommandation technique** : établir une liste écrite de critères (ci-dessus) et obtenir une confirmation de compatibilité par le prestataire **avant** développement. Ne pas figer de prestataire sans cette preuve.
11. **Questions au propriétaire** : prestataire retenu ou liste à évaluer ; devises acceptées ; modèle d'encaissement (centralisé, par partenaire).

### 3.8 D12 — Facturation depuis le devis et cardinalité des factures

1. **Question** : une ou plusieurs factures par réservation ? Quand une facture est-elle émise ?
2. **Options documentées** : une facture par réservation ; plusieurs factures par réservation avec unicité en base (roadmap §15, marketplace-gap-analysis §13). Le benchmark BM-D1 ajoute le **moment d'émission** (confirmation, paiement ou restitution).
3. **Exemple Elite Tunisia (illustratif)** : un devis accepté donne une réservation de 4 jours. À la restitution, un supplément est facturé. Si le devis a déjà été facturé, le supplément crée une deuxième facture.
4. **Avantages / inconvénients** :
   - une facture : simple, mais les suppléments doivent être intégrés à la facture initiale ;
   - plusieurs factures : traçable, mais numérotation et TVA plus complexes (D6).
5. **Conséquences financières** : risque de double facturation si la facture du devis et celle des suppléments couvrent les mêmes montants.
6. **Conséquences techniques** (constats) :
   - `InvoiceService.createFromQuote` (ligne 30) : **aucun appelant trouvé dans le code applicatif** (la recherche a porté sur `backend/src/main/java`). Ce chemin est inactif. Constat à confirmer par les tests ;
   - `InvoiceService.createReturnChargeInvoice` (ligne 64) est appelé par `BookingOperationService` (ligne 95) lorsque `extraCharges > 0`. **Ce chemin est actif, et crée une facture sans lien avec le devis.** Une réservation peut donc déjà avoir plusieurs factures ;
   - V7 ne contient qu'une contrainte `UNIQUE` sur `invoices.reference`. **Aucune unicité sur `booking_id` ou `quote_id` n'a été constatée dans V7** (à reconfirmer sur les migrations ultérieures) ;
   - `invoice_payments` a une clé primaire `(invoice_id, payment_id)` (V7). `InvoiceService` fait un SELECT puis un INSERT : une course entre deux requêtes provoquerait une erreur de clé dupliquée, et non un doublon silencieux ;
   - ISS-04 (roadmap) reste non traitée.
7. **Tables, entités, services** : `invoices`, `invoice_lines`, `invoice_payments`, `InvoiceEntity` (champs quote et booking), `InvoiceService`, `BookingOperationService.recordReturn`.
8. **Dépendances** : D6 (indissociable), D3 (annulation d'une facture), D5 (facture d'acompte).
9. **Risque de décision tardive** : des factures déjà émises selon deux cardinalités coexistent. Une contrainte d'unicité ajoutée après coup échouera sur les données existantes.
10. **Recommandation technique** : décider la cardinalité et le moment d'émission avant T-H01. Décider aussi du sort du chemin inactif `createFromQuote` et du chemin actif `createReturnChargeInvoice`. Ne pas supprimer de code sans décision.
11. **Questions au propriétaire** :
   - une facture par réservation, ou plusieurs ?
   - facture émise à la confirmation, au paiement ou à la restitution ?
   - les suppléments de restitution sont-ils intégrés à la facture initiale ?

### 3.9 P-01 — Routes placeholder

1. **Question** : chaque page placeholder doit-elle être livrée, masquée ou conservée avec un message explicite avant pré-production ?
2. **Constat** : **12 routes** utilisent le composant `page` dans [app.routes.ts](../../frontend/src/app/app.routes.ts) :
   - publiques : `/chauffeur`, `/airport-transfer`, `/services`, `/about`, `/faq`, `/contact` ;
   - authentification : `/auth/forgot-password` ;
   - client : `/customer/notifications` ;
   - administration : `/admin` (vue d'ensemble), `/admin/payments`, `/admin/partners`, `/admin/reports`.
   - La roadmap (T-L04) et l'audit (AUD-18) citent seulement les lignes 42 et 98. **Ces deux lignes existent** (`/chauffeur` et `/admin/partners`), mais la référence est **incomplète** : elle doit couvrir les 12 routes (lignes 42 à 47, 58, 74, 84, 96, 98, 100). AUD-18 nomme `/partners` alors que la route réelle est `/admin/partners`.
3. **Incohérences relevées** :
   - la route publique `/services` affiche « Les services seront configurables depuis le back-office », alors qu'une page admin `/admin/services` existe, et que V20 introduit les permissions `services:*` ;
   - les routes `/chauffeur` et `/airport-transfer` affichent « non encore connectées », alors que T-I01 (transferts) est en cours. Leur statut dépend de D13.
4. **Options** : à documenter par le propriétaire. Aucune option n'est listée dans les sources : livrer, masquer, ou conserver un message. Ce document ne tranche pas.
5. **Nature** : décision produit, non métier au sens de §15. Elle bloque **T-L04 uniquement**.
6. **Recommandation technique** : retirer de la navigation les pages qui ne doivent pas être visibles en pré-production, sans supprimer le code, après décision.
7. **Question** : quelles pages doivent être visibles, masquées ou livrées avant pré-production ?

### 3.10 P-02 — Référence « D-02 » de T-A05

1. **Question** : à quelle décision renvoie « D-02 », dont dépend T-A05 ?
2. **Constat** :
   - « D-02 » apparaît dans [implementation-roadmap.md](./implementation-roadmap.md) (lignes 202, 246, 327, 330), dans [master-project-completion-plan.md](./master-project-completion-plan.md) (lignes 122, 153, 187, 247, 251) et dans [project-completion-audit.md](./project-completion-audit.md) (AUD-22, ligne 69) ;
   - **aucune entrée D-02 n'existe dans la roadmap §15**, qui suit D1 à D15 ;
   - AUD-22 suppose que D-02 = D2 (reversements). **Cette hypothèse n'est pas prouvée et semble incohérente** : T-A05 porte sur le chevauchement de dates des réservations (disponibilité), non sur les reversements.
3. **Options** :
   - (a) D-02 est une erreur de référence, et la dépendance est à retirer ;
   - (b) D-02 désigne une décision existante sous un autre nom (à identifier par le propriétaire) ;
   - (c) D-02 est une décision non documentée (à créer).
4. **Dépendance réelle** : le critère de T-A05 (réservation future acceptée pendant une location en cours ; chevauchement refusé) ne dépend d'aucune décision métier documentée. Une marge de préparation entre deux locations serait une règle métier **non documentée** : à demander seulement si le propriétaire la souhaite.
5. **Bloquant** : non pour le code. **Oui pour la planification de T-A05** (sa position dans les lots).
6. **Recommandation technique** : corriger la référence après confirmation du propriétaire. Si aucune réponse n'est donnée, traiter T-A05 comme indépendante des décisions métier.
7. **Question** : D-02 désigne-t-elle une décision précise, ou la référence doit-elle être retirée ?

### 3.11 D13 — Exclusion des chauffeurs sur transferts

1. **Question** : un chauffeur déjà affecté à un transfert qui chevauche doit-il être exclu d'un nouveau transfert ?
2. **Options documentées** : durée par défaut puis contrainte `EXCLUDE` (roadmap §15). Le benchmark le nomme BM-D12 (exclusion de chauffeur, migration V25).
3. **Constats** :
   - `TransferService` (lignes 116, 121 à 122) et `TransferRepository` (lignes 23 à 26) contrôlent le chevauchement **uniquement au niveau applicatif** ;
   - aucune contrainte `EXCLUDE` n'existe en base ; T-I01 prévoit la migration V34 ;
   - la roadmap classe D13 comme **Reportable**, alors que T-I01 est **IN PROGRESS**.
4. **Bloquant** : oui pour T-I01 si le transfert avec chauffeur est dans le périmètre de pré-production. **Statut à confirmer** avant toute poursuite de T-I01.
5. **Recommandation technique** : ne pas poursuivre T-I01 sans avoir confirmé si D13 est reportable. Si le statut « Reportable » est maintenu, mettre T-I01 en pause ou en périmètre réduit.
6. **Question** : le transfert avec chauffeur fait-il partie de la pré-production ?

### 3.12 D7 — Contrats partenaires

1. **Question** : quel contrat lie Elite aux partenaires, et qui porte quelle responsabilité ?
2. **Options documentées** : contrat-cadre partenaire (recommandé par la roadmap §15). Aucune autre option n'est documentée.
3. **Constat** : aucun module de gestion de contrat n'a été identifié dans les parcours lus (à confirmer par recherche).
4. **Bloquant pour le développement** : non. **Bloquant pour la pré-production** : oui, avec un avis juridique.
5. **Dépendances** : D8, D9, D1 (commission dans le contrat), D2 (conditions de reversement).
6. **Question** : quel contrat-cadre, et doit-il être accepté dans l'application (version et date d'acceptation) ?

### 3.13 D8 — Assurance et sinistres

1. **Question** : quelle politique d'assurance, et quel traitement des sinistres ?
2. **Options documentées** : politique écrite (recommandée par la roadmap §15) ; champs et documents d'assurance ; workflow de sinistre (benchmark, sans alternative documentée).
3. **Bloquant pour le développement** : non. **Bloquant pour la pré-production** : oui, avec un avis juridique et de l'assureur.
4. **Conséquences techniques** : champs et documents liés aux réservations ; statut de sinistre si retenu.
5. **Dépendances** : D7, D3 (no-show et annulation).
6. **Question** : quelle politique écrite, et quels documents doivent être conservés par réservation ?

## 4. Décisions à prendre par le propriétaire (liste consolidée)

1. D1 : modèle de taux, moment de figement, taux initial.
2. D2 : fréquence, délai de contestation, virement manuel ou automatique.
3. D3 : grille, no-show, sort de la commission sur une annulation avec pénalité.
4. D5 : paiement total, acompte ou sur place ; pourcentage et moment ; décision à prendre **avant V21**.
5. D6 : avis de l'expert-comptable tunisien sur le régime de TVA, les mentions et la numérotation.
6. D10 : liste de motifs, confirmation manuelle ou instantanée, paiement avant confirmation. (Expiration `PENDING` : tranchée, aucune expiration.)
7. D11 : prestataire, devises, modèle d'encaissement.
8. D12 : cardinalité des factures et moment d'émission.
9. D13 : statut à confirmer (pré-production ou reportable).
10. P-01 : visibilité des pages placeholder.
11. P-02 : référence « D-02 ».
12. D7 et D8 : avis juridique et assureur, avant pré-production.

## 5. Tâches ouvertes sans décision métier

- Recherche de code complémentaire : utilisation de `refunded_*`, appelants de `createFromQuote`, contrat partenaire.
- Vérification de la compatibilité du prestataire (D11) par écrit.
- Correction documentaire des références aux lignes de `app.routes.ts` et de « D-02 ».

Ces tâches ne nécessitent aucune connexion PostgreSQL, aucun SQL, aucun test et aucune modification de code.

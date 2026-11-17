# Elite Tunisia — Décisions ouvertes (paiement, sessions, documents, garanties)

Statut : en attente de validation. Aucune de ces décisions n'a été implémentée.

## 1. Fournisseur de paiement
- **État actuel** : `SimulatedPaymentProvider` répond CAPTURED pour un montant positif, FAILED sinon. Ce n'est pas un fournisseur réel.
- **Problème** : aucune garantie de débit réel, pas de référence fournisseur vérifiable, pas de webhook.
- **Options** : (a) fournisseur tunisien ou international avec API de statut et de remboursement ; (b) paiement hors ligne avec validation manuelle.
- **Risques** : sans API de statut, la réconciliation (point 5) est impossible.
- **Recommandation** : choisir un fournisseur exposant une clé d'idempotence, une API de consultation par référence et des remboursements.
- **Conséquences techniques** : implémenter `PaymentProvider` avec `process`, `lookup(transactionRef|idempotencyKey)`, `refund`. Ajouter les secrets via configuration, jamais dans le code.

## 2. Remboursements
- **État actuel** : `PaymentEntity` ne porte que `refundedAt` et `refundedAmount`. Aucun chemin de code ne rembourse.
- **Problème** : pas de règle de montant (total, partiel, frais) ni de délai.
- **Options** : remboursement intégral avant une date limite ; remboursement partiel selon le délai ; aucun remboursement automatique (crédit à la place).
- **Risques** : double remboursement, remboursement d'un paiement non capturé.
- **Recommandation** : remboursement uniquement sur paiement CAPTURED, avec statut REFUND_PENDING puis REFUNDED après confirmation fournisseur, et un seul remboursement actif par paiement.
- **Conséquences** : nouvelle migration pour les statuts et une table de remboursements.

## 3. Annulations
- **État actuel** : `BookingStatus` et `PaymentStatus.CANCELLED` existent, sans règle de déclenchement lié au paiement.
- **Problème** : une réservation payée peut-elle être annulée, et qui décide du remboursement ?
- **Options** : annulation client avec règle de délai ; annulation partenaire (remboursement intégral obligatoire) ; annulation administrative.
- **Recommandation** : définir une matrice annulation × délai × auteur avant implémentation.

## 4. Paiement PENDING après PROVIDER_ERROR
- **État actuel** : `PaymentService.createForQuote` écrit `PROVIDER_ERROR` et laisse le paiement en PENDING. Le devis reste bloqué (`PAYMENT_IN_PROGRESS`) sans fin.
- **Problème** : blocage indéfini, et impossibilité de savoir si le client a été débité.
- **Options** : (a) statut UNKNOWN, réconciliation avant tout retry ; (b) FAILED immédiat (risqué : le débit peut avoir eu lieu) ; (c) retry automatique avec la même clé.
- **Recommandation** : (a). UNKNOWN ne peut être libéré que par une réponse fournisseur confirmée (CAPTURED ou FAILED) ou par une décision administrative tracée.
- **Conséquences** : ajouter `UNKNOWN` à `PaymentStatus` (colonne `varchar(30)`, pas de contrainte CHECK trouvée dans V6, à confirmer par migration de vérification) ; adapter les tests `providerErrorKeepsAttemptPendingWithoutFailedOrException`.

## 5. Réconciliation automatique ou manuelle
- **État actuel** : aucune réconciliation.
- **Recommandation** : job planifié qui interroge le fournisseur pour les paiements UNKNOWN (par clé d'idempotence), puis file d'attente administrative pour les cas non résolus après un délai. Chaque action admin est journalisée (`payment_events`).
- **Conséquences** : tâche planifiée, endpoint admin, migration pour la file de réconciliation.

## 6. Politique de session et logout
- **État actuel** : JWT avec refresh et logout (voir section 14 du rapport).
- **Problème** : durée de vie des sessions et effet du logout sur les access tokens déjà émis non tranchés.
- **Options** : access token court (5–15 min) sans révocation immédiate ; liste de révocation ; sessions serveur.
- **Recommandation** : access token court + révocation des refresh tokens au logout.

## 7. Révocation des refresh tokens
- **Options** : révocation de la seule session courante ; révocation de toutes les sessions ; rotation avec détection de réutilisation (révoquer la famille).
- **Recommandation** : rotation à chaque usage, et révocation de la famille si un token déjà utilisé est présenté.

## 8. Refresh simultanés
- **Problème** : deux refresh concurrents avec le même token.
- **Options** : premier gagnant, second rejeté (risque de déconnexion d'un onglet) ; tolérance d'une courte fenêtre.
- **Recommandation** : verrou sur la ligne du refresh token, premier gagnant, second rejeté avec révocation de la famille seulement si la fenêtre est dépassée.

## 9. Visibilité des documents
- **État actuel** : factures et devis accessibles par identifiant ; la règle d'accès par propriétaire doit être confirmée sur chaque endpoint.
- **Options** : client seul ; client + partenaire concerné ; + administration.
- **Recommandation** : client propriétaire, partenaire concerné pour ses réservations, administration pour audit. Accès refusé par défaut.

## 10. Retrait de garantie
- **Décision confirmée par le client** : le contrôle doit viser uniquement les réservations réellement couvertes (CONFIRMED ou IN_PROGRESS), et non un simple chevauchement de dates.
- **État actuel** : la validation du retrait repose encore sur un chevauchement de période dans le code. Le correctif reste à implémenter.
- **Conséquences** : requête limitée aux réservations couvertes, avec test PostgreSQL dédié.

## Statut de ce document
Aucune décision structurante n'a été appliquée au code. Les points 1 à 9 attendent validation ; le point 10 est confirmé mais non encore implémenté.

## Scénarios PostgreSQL exécutés (état au 2e passage)

Classe `PaymentScenariosPostgresIT` (16 tests, `elite_a4_test`, fournisseur de test contrôlé) : 9 scénarios et variantes, tous validés, y compris la réconciliation (9a à 9g). Résultats et logs : section 14.17 de `ELITE_TUNISIA_FINAL_STABILIZATION_REPORT.md`.

Constats qui nécessitent une décision avant tout changement du workflow :
- Capture réussie puis échec de `finish` : le paiement reste PENDING sans référence fournisseur. Le fournisseur a débité, l'application ne le sait pas (décision UNKNOWN).
- Réponse perdue après capture : même effet.
- Un PENDING bloque le devis (`PAYMENT_IN_PROGRESS`) sans chemin de réconciliation.

Mise à jour (2026-11-16) : le mécanisme technique UNKNOWN, la persistance de la référence et la réconciliation sont implémentés et validés sur PostgreSQL (section 14.17 du rapport). Les règles de transition ci-dessus restent une proposition : elles ne sont pas approuvées métier.

Mise à jour (planificateur) : `PaymentReconciliationWorker` est désactivé par défaut (`app.payment.reconciliation.enabled=true` pour l'activer). Il traite par lots (`batch-size`, défaut 50) les PENDING plus anciens que `pending-older-than` (défaut PT15M) et les UNKNOWN. Il ne déclenche jamais de capture : il consulte uniquement le statut existant par clé. Un verrou advisory de session (clé `7412031001`), tenu sur une connexion dédiée, empêche deux cycles simultanés, y compris entre instances. Tests PostgreSQL : s14 (verrou détenu ailleurs, puis reprise après libération) et s15 (capture prouvée récupérée, incertain conservé en UNKNOWN, second cycle sans effet).

Décisions métier toujours en attente, non prises par l'assistant : politique `PROVIDER_ERROR`, réconciliation automatique ou manuelle au-delà du planificateur, résolution de `MANUAL_REVIEW`, réponse `FAILED` fournisseur après capture confirmée, remboursements, annulations, choix du fournisseur de paiement, acceptation du contrat de preuve de capture synchrone.

## Conception UNKNOWN et récupération (proposition, non implémentée)

### Règles de statut (intégrées)
- `FAILED` : uniquement si le fournisseur confirme l'échec.
- `UNKNOWN` : timeout, réponse perdue, interruption réseau, ou résultat impossible à confirmer.
- `UNKNOWN` ne déclenche jamais de nouveau paiement automatiquement.
- Une tentative `UNKNOWN` bloque toute nouvelle tentative sur le même devis tant qu'elle n'est pas résolue.
- `UNKNOWN` vers `CAPTURED` ou `FAILED` exige une preuve lue auprès du fournisseur (référence de transaction ou clé d'idempotence), jamais une simple réponse réseau.
- Une intervention administrative est tracée (acteur, motif, horodatage) et ne vaut pas résultat fournisseur confirmé ; elle reste un état distinct (`MANUAL_REVIEW`) tant que la décision métier n'est pas validée.

### Persistance avant appel fournisseur
- La tentative est persistée (statut PENDING, identifiant interne, clé d'idempotence stable) dans une transaction committée avant l'appel fournisseur. C'est déjà le cas dans `PaymentService.reserve`.
- La clé d'idempotence transmise au fournisseur doit être l'identifiant interne de la tentative, ou dérivée de lui, afin qu'une relecture retrouve la même opération.
- Après l'appel, la référence fournisseur est écrite dès qu'elle est connue, dans une transaction séparée de `finish`.

### Scénarios de récupération
| Cas | État après incident | Action de récupération |
|---|---|---|
| Capture puis arrêt de l'application | PENDING | Job de reprise : lecture du fournisseur par clé d'idempotence, puis CAPTURED si confirmé |
| Capture puis échec de `finish` | PENDING ou UNKNOWN | Même lecture fournisseur ; aucun nouvel appel `process` |
| Réponse perdue | UNKNOWN | Lecture fournisseur ; sans réponse, reste UNKNOWN |
| Interruption entre deux transactions | PENDING | Reprise au redémarrage, sans nouvel appel si la tentative existe |
| Redémarrage avec PENDING ou UNKNOWN | inchangé | Le devis reste bloqué jusqu'à résolution |

### Garantie contre le second débit
- Un nouvel appel `process` n'est autorisé que si le fournisseur confirme l'absence de capture pour la clé d'idempotence (FAILED confirmé).
- Un paiement CAPTURED est unique par devis (index partiel de V21), ce qui empêche le second débit même en cas de rejeu.

### Dépendances et décisions en attente
- Dépend du choix du fournisseur (point 1) : il doit offrir une lecture de statut par clé d'idempotence. Sans cette capacité, la réconciliation automatique est impossible et seule la file administrative reste disponible.
- Dépend de la validation des transitions ci-dessus avant toute modification du workflow `PROVIDER_ERROR` (point 4).
- Une nouvelle migration sera nécessaire pour le statut `UNKNOWN`, la référence fournisseur et la file de réconciliation.

### Ce qui n'est pas encore fait
- Aucun code de workflow modifié.
- Les neuf scénarios PostgreSQL (section 3.3 de la mission) ne sont pas écrits ni exécutés.
- Aucune décision sur les remboursements, les annulations ou le fournisseur n'a été prise.

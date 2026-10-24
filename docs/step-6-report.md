# Rapport étape 6 : Devis et tarification avancée

**Date :** 5 octobre 2026. **Production : NOT READY.**

## Décisions du commanditaire

Tarif chauffeur global : la ligne DRIVER est sa différence avec la location, sans additionner deux prix globaux. Chaque version a une référence DEV unique et une contrainte booking/version. Les tranches de 24 heures commencées et les PENDING non bloquants restent les règles validées de l'étape 5.

## Résultats

| Critère | Résultat | Preuve |
| --- | --- | --- |
| Quote model | PASS | Entité JPA, V5, référence/version/statuts/montants/validité/notes/horodatages et association Booking |
| Quote lines | PASS | Lignes ordonnées, quantité/prix/montant et metadata JSONB, contraintes PostgreSQL |
| Versioning | PASS | Références distinctes, unicité booking/version, anciennes versions et événements conservés |
| Quote calculation | PASS | Service RentalPricing partagé, chauffeur global/différence, services réellement configurés, remise contrôlée, taxe fixe configurable zéro par défaut |
| Price snapshot | PASS | Tarifs persistés dès DRAFT, conservés à l'édition/envoi/acceptation; nouvelle version aux tarifs courants |
| Quote validity | PASS | Validité explicite agence, acceptation expirée refusée, expiration persistée/auditée et worker borné |
| Customer quote | PASS | Liste/détail propres au client, lignes/montants/validité/statut et absence de notes internes |
| Quote acceptance | PASS | Contrôles propriétaire/état/échéance/version/cohérence/disponibilité; ACCEPTED sans paiement ni confirmation automatique |
| Quote rejection | PASS | SENT vers REJECTED, motif facultatif et audit |
| Change request | PASS | Message/événement traçable sans réécriture du devis; nouvelle version par l'agence |
| Admin quote management | PASS | Recherche/filtres/pagination/détail, création depuis Booking, édition DRAFT, envoi/annulation/nouvelle version/historique |
| Booking confirmation | PASS | Action agence explicite, version courante ACCEPTED requise lorsqu'un devis existe, reprise de son snapshot |
| Availability recheck | PASS | Recontrôle transactionnel avant confirmation, verrous et exclusion PostgreSQL V4 conservés |
| RBAC | PASS | Permissions quotes read/create/manage/send/accept/reject; droits agence limités, isolation client serveur |
| Audit | PASS | QUOTE_CREATED/UPDATED/SENT/ACCEPTED/REJECTED/EXPIRED/CANCELLED/CHANGE_REQUESTED avec acteur/entité/timestamp/résultat |

## Vérification

| Contrôle | Résultat |
| --- | --- |
| Backend tests | **33/33** sur PostgreSQL 18.3 : 12 auth/catalogue, 8 réservations, 10 devis, 3 stockage |
| Frontend tests | **30/30** Vitest : 19 antérieurs et 11 devis |
| E2E tests | **15/15** : 9 antérieurs et 6 devis |
| Concurrence PostgreSQL | **3/3**, incluses dans le backend : deux étapes 5 et confirmations simultanées de deux devis acceptés |
| PostgreSQL | PASS, bases UTF-8 de test, aucune modification de base permanente |
| Flyway | PASS, V1–V5 appliquées sur schéma vierge par Flyway |
| Angular build | PASS, templates/types stricts et routes lazy |
| Backend build | PASS, Maven verify et JAR Spring Boot reconditionné |
| Responsive | PASS, captures détail à 1366/375 px et éditeur à 375 px, pas de débordement de page; tableaux défilables |

Les six E2E devis utilisent l'API réellement reliée à PostgreSQL, sans interception :

1. Client crée sa demande; agent ouvre Booking, crée le devis, ajoute chauffeur/remise, voit le total backend et envoie; client consulte.
2. Client accepte; Booking reste PENDING; agent valide; Booking CONFIRMED reprend le montant accepté.
3. Client refuse et l'agence constate REJECTED.
4. Client demande modification; agence voit le message, crée v2 et l'envoie; v1 et ses montants restent présents.
5. Un autre client reçoit 404 sur lecture/acceptation et ne voit pas le devis.
6. Après acceptation, une réservation concurrente prend la période; confirmation agence refusée, Booking reste PENDING.

La concurrence additionnelle utilise deux transactions synchronisées sur PostgreSQL, pas H2 : deux devis ACCEPTED et une seule confirmation réussie. Les deux autres tests couvrent service Booking et écritures SQL directes sous exclusion.

## Sources de vérité et limites

- Aucun total/prix unitaire/taxe/référence/version client n'est officiel. La remise est seulement une saisie agence permissionnée, revalidée et bornée; le serveur produit le total.
- Taxe configurée à zéro dans la recette; le test backend active explicitement un montant de test pour prouver configuration/arrondi/snapshot. Aucun taux fiscal, régime TVA ou exonération n'est inventé.
- Services issus du backend uniquement; aucun tarif commercial de livraison/transfert n'est codé dans Angular. Le chauffeur est une ligne tarifaire, sans affectation opérationnelle.
- L'envoi publie dans l'espace client. NotificationService et QuoteDocumentService sont des contrats préparés, sans e-mail/SMS/push ni PDF livré.
- Paiement, facture légale, remboursement, banque et comptabilité complète : NOT IMPLEMENTED.
- V5 refuse des devis V1 non vides sans mapping Booking approuvé; la reprise de données historiques n'est pas inventée.
- Le worker d'expiration traite au plus 100 éléments par passage. Un SENT expiré peut rester affiché dans la liste jusqu'au scan; détail/acceptation vérifient immédiatement l'échéance.
- Confirmation sans devis reste possible selon l'étape 5; si un devis existe, sa dernière version doit être ACCEPTED. Une ancienne version acceptée ne peut pas contourner une proposition plus récente.
- Les deux avertissements frontend existants restent visibles : CSS de l'accueil et CommonJS jsbi du polyfill Temporal; aucun budget n'est augmenté pour les masquer.

Les clusters de vérification ont été nettoyés. L'application locale (4200, API 8080) utilise le PostgreSQL de recette temporaire 55442 et la base elite_step6_e2e avec des comptes test. Service PostgreSQL Windows et base permanente non modifiés. Secrets, règles fiscales/commerciales, déploiement, sauvegardes/supervision et recette permanente restent nécessaires avant production.

Documentation : [quotes.md](quotes.md), [api.md](api.md), [database.md](database.md), [security.md](security.md), [testing.md](testing.md).
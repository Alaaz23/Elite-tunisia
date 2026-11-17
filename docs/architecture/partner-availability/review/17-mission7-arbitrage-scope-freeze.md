# 17 — Mission 7 : arbitrage métier et gel du périmètre MVP

> **Nature des décisions ci-dessous.** Ces choix sont pris par l'équipe de conception pour permettre la réalisation du MVP de démonstration. Ce sont des **hypothèses MVP** : elles ne sont pas des décisions propriétaire et ne constituent aucun engagement commercial, juridique ou financier réel. Elles sont réversibles et doivent être réévaluées avant toute exploitation commerciale.
>
> Statuts utilisés : **Décision MVP** (choix pris, appliqué au périmètre), **Hypothèse** (valeur de démonstration à ajuster), **Hors périmètre** (reporté).

## 1. Décisions MVP

| ID | Sujet | Décision MVP | Justification | Conséquences | Statut |
|---|---|---|---|---|---|
| D-PA-01 | Validité d'une garantie | Période bornée `valid_from`/`valid_until` (TIMESTAMPTZ, fuseau Africa/Tunis), expiration automatique à la date de fin (statut EXPIRED calculé ou contrôlé à la lecture) | Simple, auditable, pas d'oubli humain | Une réservation doit être entièrement couverte par une garantie ACTIVE à sa création | Décision MVP |
| D-PA-01 (durée) | Durée maximale | 90 jours | Valeur de démonstration, couvre les scénarios de location premium saisonnière | Paramètre de configuration, non codé en dur | Hypothèse |
| D-PA-14 | Chevauchement | Refus à la création de toute garantie ACTIVE chevauchant une autre garantie ACTIVE du même véhicule | Déterministe, cohérent avec un seul partenaire courant | Contrainte base + contrôle service ; code d'erreur explicite | Décision MVP |
| D-PA-04 | Réservations PENDING | Hold temporaire de 30 minutes sur les dates, puis expiration automatique | Évite les doublons en démonstration sans immobiliser les véhicules | Traitement d'expiration à implémenter (Mission 11) ; test de concurrence obligatoire | Décision MVP (durée : Hypothèse) |
| D-PA-07 | Retrait d'une garantie | Retrait refusé si une réservation CONFIRMED ou IN_PROGRESS la référence ; retrait autorisé sinon, avec motif et audit | Protège le client, pas de compensation financière à gérer | Pas d'exception en MVP | Décision MVP |
| D-PA-12 | Transfert de partenaire | Transfert refusé si garantie ACTIVE couvrant ou suivant la date, réservation CONFIRMED future, réservation IN_PROGRESS, ou hold PENDING actif | Liste bloquante minimale, cohérente avec les règles validées | Transfert hors périmètre MVP d'interface si la liste n'est pas entièrement implémentée | Décision MVP |
| D-PA-10 / D5 | Confirmation et paiement | Confirmation après paiement **simulé intégral** dans l'environnement de démonstration ; aucun pourcentage d'acompte en MVP | Simplicité ; aucun encaissement réel | Pas de politique d'acompte réelle ; pas d'engagement commercial | Décision MVP ; pourcentage réel : Hors périmètre |
| D-PA-15 | Type de partenaire | Valeurs : `INDIVIDUAL`, `COMPANY` | Couvre les cas usuels sans dépendre d'un régime juridique | Champ contrôlé par CHECK ; à compléter après avis juridique | Hypothèse |
| D-PA-16 | Maintenance | Mise en maintenance refusée si une réservation CONFIRMED future existe | Évite les clients sans véhicule | Pas de mode d'urgence en MVP | Décision MVP |
| D-PA-17 | Partenaire désactivé | Plus de nouvelle garantie ; garanties existantes régies par leurs dates ; aucune annulation automatique | Respecte la règle de retrait D-PA-07 | Alerte affichée à l'administration | Décision MVP |
| D-PA-18 | Accès partenaire | Rôle `PARTNER` en lecture seule, limité aux données de son propre partenaire (véhicules, garanties, réservations associées). Aucune écriture par le partenaire | Permet « consulter leurs engagements » sans créer de surface d'écriture | Authentification et contrôle de propriété obligatoires ; tests de non-accès inter-partenaires | Décision MVP |
| Paiements | Environnement | Simulateur interne uniquement ; aucun prestataire réel, aucune carte réelle | Exigé par la mission (environnement contrôlé) | Pas d'encaissement réel ; pas de prestataire externe | Décision MVP |
| Archivage véhicule | Suppression | Pas de suppression physique d'un véhicule référencé ; passage à un statut archivé (à ajouter en Mission 9) | Préserve l'historique | Ajout d'un statut ; migration additive | Décision MVP |

## 2. Décisions restant hors périmètre MVP

| Sujet | Raison |
|---|---|
| Acompte en pourcentage et échéances | Choix commercial réel |
| Pénalités d'annulation et remboursements réels | Engagement financier, avis juridique requis (D-PA-09) |
| Retrait exceptionnel d'une garantie CONFIRMED | Compensation à définir |
| Transfert contractuel avec consentement des parties | Évolution future explicitement exclue |
| Comptes partenaires avec écriture | Surface de sécurité à traiter après MVP |
| Prestataire de paiement externe | Engagement contractuel |
| Instant booking, calendrier en temps réel public | Hors besoin de démonstration |

## 3. Périmètre gelé

**Inclus dans le MVP :**
1. Gestion des partenaires (Elite, administration).
2. Rattachement véhicule ↔ partenaire et historique (`vehicle_partner_assignments`).
3. Propositions de disponibilités par le partenaire, validation par Elite (garanties).
4. Catalogue public limité aux véhicules ayant une garantie ACTIVE couvrant la période.
5. Recherche client par dates.
6. Réservation avec référence obligatoire à la garantie, hold PENDING de 30 min, conflits empêchés.
7. Espace partenaire lecture seule (D-PA-18).
8. Administration : suivi des partenaires, véhicules, garanties, réservations, alertes.
9. Paiement simulé en environnement contrôlé.
10. Tests essentiels et démonstration de bout en bout.

**Exclus :** tout ce qui figure en §2, ainsi que les nouvelles fonctionnalités non listées en §3.

## 4. Hypothèses de travail

- Les données historiques ne sont pas disponibles : elles ne sont pas attribuées automatiquement à un partenaire (règle de mission). Les véhicules historiques restent sans partenaire courant jusqu'à rattachement manuel par Elite.
- La démonstration utilise un jeu de données créé pour l'occasion, sur base isolée.
- Node 18 n'est pas conforme au build Angular : le build frontend nécessite un environnement compatible, à vérifier en Mission 8.

## 5. Sécurité et conformité : rappel

Cet arbitrage ne constitue ni une certification ni une conformité réglementaire. Les paiements sont simulés ; aucune donnée bancaire réelle ne doit être traitée.

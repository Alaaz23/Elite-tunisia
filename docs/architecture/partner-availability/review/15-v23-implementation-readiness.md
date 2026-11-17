# 15 — Préparation V23 et plan de tests futurs (Mission 6)

> **Aucun test n'a été exécuté dans cette mission.** Les tests ci-dessous sont un plan. Ils ne doivent pas être déclarés réussis tant qu'ils ne sont pas lancés sur PostgreSQL isolé.

## 1. Verdict de préparation V23

**NON PRÊT pour cadrage V23.** Motifs : décisions structurantes ouvertes (D-PA-01, D-PA-04, D-PA-07, D-PA-12 §3.5, D-PA-14 règle), absence de copie de données historiques, identifiants provisoires D-PA-15 à D-PA-18 non confirmés.

## 2. Plan de tests (non exécuté)

| Niveau | Cible | Cas à couvrir | Environnement |
|---|---|---|---|
| Unitaire | BookingService | Refus réservation sans garantie ; couverture partielle ; transitions | JVM, sans base |
| Unitaire | Règle de transfert | Liste bloquante ; historique non bloquant | JVM |
| Intégration Spring | Contrôleurs availability | 401, 403 pour CLIENT, 400, 404, 409 | Spring Boot test, base de test |
| PostgreSQL | EXCLUDE garanties ACTIVE | Chevauchement refusé ; statut non ACTIVE autorisé | PostgreSQL 18 isolé, jetable |
| PostgreSQL | EXCLUDE rattachements | Pas de deux rattachements actifs | Idem |
| PostgreSQL | FK RESTRICT | Suppression véhicule ou partenaire référencé refusée | Idem |
| PostgreSQL | CHECK booking_model | LEGACY sans garantie accepté ; GUARANTEED sans garantie refusé | Idem |
| Flyway | Migration V1 à V23 depuis base vide | Succès ; checksums | Base vide jetable |
| Flyway | Migration sur copie historique | Aucun échec, comptage avant/après | Copie isolée (**non disponible**) |
| Concurrence | Deux créations de réservation simultanées | Une seule réussit | PostgreSQL isolé, deux connexions |
| Concurrence | Deux transferts simultanés | Un seul réussit | Idem |
| Rollback | Échec en milieu de transfert | Rien n'est persisté partiellement | Idem |
| Sécurité | Accès aux garanties d'un partenaire tiers | Refus | Spring test |
| E2E Angular | Parcours admin : créer garantie, tenter transfert refusé | Message clair | Navigateur, après build fonctionnel |

## 3. Commandes prévues (non exécutées)

```text
# Backend, tests unitaires et intégration (après validation)
cd backend
mvn test
# PostgreSQL isolé : lancer uniquement sur base jetable, jamais sur la base locale
scripts\test-backend-postgres.ps1   # à relire avant exécution
```

Les commandes doivent être relues avant exécution. La base cible doit être un conteneur ou une base créée pour l'occasion.

## 4. Preuves requises pour déclarer chaque étape réussie

| Étape | Preuve |
|---|---|
| Migrations | Journal Flyway complet, `flyway_schema_history` sans échec |
| Contraintes | Requêtes qui échouent comme attendu, sortie copiée |
| Services | Rapports surefire (`target/surefire-reports`) |
| Concurrence | Résultat des deux threads, décompte |
| Restauration | Comptage avant/après sur base restaurée |
| E2E | Rapport Playwright et captures |

## 5. Dépendances de validation

Ne pas démarrer les tests PostgreSQL de V23 avant : D-PA-14 (règle), D-PA-01 (dates), et la décision backfill.

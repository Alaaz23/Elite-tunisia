# 09 — Stratégie de tests

## 1. Niveaux

| Niveau | Outil | Périmètre | Exécution possible maintenant |
|---|---|---|---|
| Unitaire | JUnit 5, Mockito | Fonction de couverture, règles de statut, calculs | Oui (JDK 21, H2 ou sans base) |
| Intégration H2 | `@SpringBootTest`, H2 | Services, sécurité, DTO, erreurs | Oui |
| Intégration PostgreSQL | `*PostgresIT`, port 15432 | EXCLUDE, FK, verrous, concurrence | **Non** (interdit par cette mission) |
| Concurrence PostgreSQL | Threads parallèles | Deux réservations simultanées, deux garanties simultanées | **Non** |
| Frontend unitaire | Karma/Jasmine ou Vitest selon projet | Services API, formulaires | **Non** (Node 18.20.8) |
| End-to-end | Playwright | Parcours client et admin | **Non** |

## 2. Cas de test : couverture (unitaires)

| # | Cas | Attendu |
|---|---|---|
| C1 | Garantie = période exacte | Couvert |
| C2 | Garantie plus longue | Couvert |
| C3 | Garantie partielle (début couvert, fin non) | Non couvert |
| C4 | Deux garanties successives `[1,5[` et `[5,10[`, demande `[3,8[` | Non couvert (pas de fusion implicite) |
| C5 | Deux garanties qui se chevauchent, union couvrante | Selon D-PA-14 (règle de chevauchement à préciser) ; par défaut refus à la création |
| C6 | Demande de fin = début de garantie (borne exclue) | Pas de chevauchement |
| C7 | Demande `start >= end` | Erreur 400 |
| C8 | Garantie retirée | Non couvert |
| C9 | Garantie future, demande passée | Non couvert |

## 3. Cas de test : chevauchements (intégration H2 et PostgreSQL)

| # | Cas | Niveau |
|---|---|---|
| O1 | Création de garantie chevauchante refusée | H2 (contrainte applicative) puis PostgreSQL (EXCLUDE) |
| O2 | Deux garanties contiguës `[a,b[` et `[b,c[` acceptées | PostgreSQL |
| O3 | Garantie retirée puis recréée sur la même période | PostgreSQL |
| O4 | Réservation chevauchant une garantie partiellement | H2 |
| O5 | Réservation exactement sur une garantie | H2 |

## 4. Cas de test : réservation et concurrence

| # | Cas | Niveau | Attendu |
|---|---|---|---|
| R1 | Réservation sans garantie | H2 | Refus, code `VEHICLE_NOT_GUARANTEED` |
| R2 | Réservation avec garantie | H2 | Accepté |
| R3 | Deux réservations simultanées même véhicule | PostgreSQL concurrence | Une seule réussit |
| R4 | Retrait de garantie pendant une réservation | PostgreSQL concurrence | Une seule issue, pas d'incohérence |
| R5 | Réservation avant confirmation de garantie | H2 | Refus |
| R6 | Réservation annulée puis nouvelle sur même période | H2 | Accepté |

## 5. Cas de test : administration

| # | Cas | Attendu |
|---|---|---|
| A1 | Création de partenaire sans nom | 400 |
| A2 | Désactivation d'un partenaire avec garanties futures | Décision D-PA-17 (provisoire) ; par défaut garanties non affichées |
| A3 | Rattachement véhicule à partenaire inactif | 422 |
| A4 | Modification de garantie avec réservation confirmée | 409 |
| A5 | Historique créé à chaque action | Présent |
| A6 | Changement de partenaire du véhicule après création d'une garantie | La garantie conserve le partenaire porteur d'origine (D-PA-05) |
| A7 | Création de garantie sur un véhicule dont le partenaire rattaché est inactif | 422 `PARTNER_INACTIVE` |
| A8 | Requête de modification avec `carryingPartnerId` ou `partnerId` forgé | Ignoré ou 400 ; le partenaire porteur ne change pas |

## 6. Cas de test : sécurité

| # | Cas | Attendu |
|---|---|---|
| S1 | Client appelle `/api/admin/partners` | 403 |
| S2 | Partenaire lit un véhicule d'un autre partenaire | 404 |
| S3 | Partenaire modifie une garantie | 403 |
| S4 | Partenaire forge un `partnerId` en paramètre | Ignoré ou 400 |
| S5 | Réponse d'erreur sans trace SQL | Vérifié |

## 7. Cas de test : frontend

| # | Cas | Attendu |
|---|---|---|
| F1 | Recherche sans dates | Pas d'appel API |
| F2 | Recherche avec dates inversées | Message, pas d'appel |
| F3 | Résultat vide | État vide affiché |
| F4 | Erreur API | Message générique |
| F5 | Véhicule non couvert sélectionné | Erreur 409 affichée |

## 8. Exécution

Commandes (à lancer dans une mission dédiée, JDK 21) :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
.\mvnw.cmd -B -ntp -DskipITs test-compile test
```

PostgreSQL uniquement sur base isolée, port 15432, avec `ELITE_POSTGRES_TEST=true`. Jamais le port 5432.

Preuves attendues : code de sortie Maven (`$LASTEXITCODE`), ligne `Tests run` du résumé Surefire, liste des tests ignorés.

# 12 — Synthèse pour le propriétaire du projet

## État actuel

- Le module partenaire **n'existe pas** dans le code. Une table `partners` existe en base (V1), sans entité et sans lien avec les véhicules.
- Les véhicules n'ont aucun propriétaire.
- La recherche publique affiche les véhicules sans réservation bloquante, **sans vérifier une garantie Elite**. C'est le principal écart avec le modèle validé.
- La protection contre les doubles réservations est solide : verrou véhicule et contrainte PostgreSQL.
- Les protections du cycle de vie des véhicules sont en place (tests H2 verts).

## Problèmes à traiter

1. Aucune garantie de disponibilité : un véhicule non confirmé peut être proposé. **Critique**.
2. Aucun rattachement véhicule-partenaire : isolation impossible. **Bloquant**.
3. `partners.commission_rate` contredit la décision D1. **À corriger avant tout usage**.
4. Dossier `backend/src/main/resources/db/migration/` incomplet : confusion possible sur la source Flyway.
5. README frontend des réservations obsolète.

## Décisions à prendre en priorité

Décisions validées : partenaire porteur (D-PA-05, Option A) ; transfert de véhicule refusé en présence d'engagement actif (D-PA-12) ; identifiant du chevauchement (D-PA-14, règle à préciser) ; table d'historique des rattachements ; référence obligatoire réservation → garantie pour les nouvelles réservations. Registre : `review/08-owner-decisions-validation-record.md`.

Décisions restant à prendre :
1. D-PA-14 : règle de chevauchement (interdiction, fusion manuelle ou automatique).
2. D-PA-01 et D-PA-07 : durée de validité, expiration, retrait d'une garantie.
3. D-PA-04 : réservations PENDING.
4. D-PA-09 : retrait et conséquences financières (avis juridique et comptable requis).
5. D5 : moment de l'acompte et cycle de paiement.

## Recommandation

Commencer par le lot 0 (décisions), puis le lot 1 (partenaires), sans migration tant que les décisions ne sont pas tranchées. Le lot 3 (garanties) ne doit démarrer qu'après autorisation écrite et validation PostgreSQL sur base isolée.

## Ce qui n'a pas été vérifié

- Tests PostgreSQL et concurrence.
- Build et tests Angular (Node non conforme).
- Comportement réel des clés étrangères sans `ON DELETE`.
- Données historiques de production.

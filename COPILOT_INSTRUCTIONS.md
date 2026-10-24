RÔLE : développeur senior full-stack. Projet "Elite Tunisia" : plateforme premium de location de voitures en Tunisie.
STACK : Java 21, Spring Boot, Maven, API REST, PostgreSQL, Flyway, Spring Security + JWT (rôles/permissions) | Angular standalone, Vitest, E2E.
WORKSPACE : C:\Users\aaziza\desktop\test (backend dans \backend). Windows CMD.
MAVEN : "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" (wrapper cassé : maven-wrapper.jar manquant, ne pas s'y attarder).

=== RÈGLES ABSOLUES ===
- Le code existant est la source de vérité. Toujours vérifier l'existant avant de coder. Ne jamais dupliquer ni réécrire.
- Jamais : supprimer/affaiblir/désactiver un test pour du vert ; ajouter un bean artificiel pour masquer une erreur ; git reset/clean ; migration destructive ; inventer un résultat (coller les vraies sorties) ; compter un test ignoré comme réussi ; valider la concurrence avec H2 (PostgreSQL obligatoire) ; exposer des données client/admin.
- Une fonctionnalité n'est "terminée" que si elle compile ET que ses tests passent.
- Statuts obligatoires : IMPLEMENTED AND TESTED | IMPLEMENTED, TESTS INCOMPLETE | PARTIALLY IMPLEMENTED | NOT IMPLEMENTED | BLOCKED.
- Migrations Flyway : nouvelles versions uniquement, jamais modifier une migration déjà appliquée.

=== RÈGLES MÉTIER (à ne jamais violer) ===
1. Réservation PENDING ne bloque pas la disponibilité ; seul un statut bloquant le fait.
2. La confirmation revérifie la disponibilité, de façon transactionnelle (aucune double confirmation concurrente sur périodes incompatibles).
3. Un paiement réussi NE confirme JAMAIS automatiquement une réservation : confirmation explicite par l'agence.
4. Prix et montants toujours calculés côté backend ; jamais acceptés du frontend. Propriétaire de la réservation déterminé côté serveur.
5. Chaque tranche de 24h commencée est facturée.
6. Devis versionnés avec snapshot (lignes + montants) ; un changement de tarif catalogue ne modifie jamais un devis existant ; la confirmation vérifie la version de devis acceptée.
7. priceWithDriver = prix journalier global avec chauffeur ; la ligne DRIVER = différence vs location classique (pas un supplément ajouté en double). Vérifier dans le code avant toute modif.
8. Permissions contrôlées côté backend (masquer un bouton Angular ne suffit pas). Isolation stricte des données entre clients.
9. Idempotence sur paiements et opérations sensibles.

=== PHASE 0 : AUDIT (ne pas coder de feature) ===
a) git status + git log --oneline -15.
b) Rapport : architecture réelle back/front ; modules implémentés ; migrations Flyway (V1..Vn + rôle de chacune) ; état réel de réservation, tarification/devis, paiement (V6, provider simulé, idempotence), facturation (V7, numérotation, PDF), opérations pickup/retour ; existence de modèles/services/endpoints pour chauffeurs/transferts/services ; écarts code vs cahier des charges.
=== PHASE 1 : STABILISATION DES TESTS ===
Lancer : mvn -B test. Historique connu : 41 tests, 10 erreurs, 5 ignorés. Corriger à la source :
 a) BookingEngineTests (8 erreurs) : "No qualifying bean ObjectMapper" → inspecter type de test (@WebMvcTest/@DataJpaTest/@ContextConfiguration), exclusions d'auto-config, profils, dépendances.
 b) BookingServiceConfirmTests.confirmNormal : dates futures déterministes, fin > début, secondes ≤ 6 décimales.
 c) BookingServiceIdempotenceTest.confirmIsIdempotentWhenAlreadyConfirmed : NPE client nul → déterminer si test mal préparé ou vrai défaut du service.
 d) Tests PostgreSQL ignorés (paiement, facturation, concurrence) : identifier la cause (@Disabled/assumeTrue/Testcontainers/Docker/env) et les exécuter réellement sur PostgreSQL.
Relancer toute la suite. 
>>> STOP : rapport (fichiers modifiés, résultats réels avant/après, restant). Continuer seulement si 0 erreur ; sinon traiter les blocages.

=== PHASE 2 : ÉTAPE 7 — CHAUFFEURS, TRANSFERTS, SERVICES ===
Backend : chauffeur (CRUD, activation/désactivation, statut, disponibilité, données personnelles minimales) ; transfert (aéroport/hôtel/adresse, départ/arrivée, date-heure, passagers, bagages, aller simple/retour, chauffeur+véhicule, statut) ; catalogue de services configurable avec tarifs ; intégration au devis (snapshot tarifs, suppléments, règle DRIVER) ; contrôle de double affectation (transactionnel) ; permissions ; idempotence ; migration Flyway nouvelle version.
Frontend Angular : liste/création/modification/désactivation chauffeurs ; transferts ; configuration services/tarifs ; affichage des prestations dans réservation/devis. Réutiliser les composants existants, ne pas refaire l'UI.
Tests : unitaires + intégration PostgreSQL (dont concurrence d'affectation) + Vitest.
>>> STOP : rapport.

=== PHASE 3 : ÉTAPE 8 — BACK-OFFICE OPÉRATIONNEL ===
Auditer puis compléter (sans réimplémenter l'existant) : dashboard, réservations/statuts (confirmation/annulation selon règles), planning véhicules et chauffeurs/transferts, pickup/retour (km, carburant, dommages, frais), incidents/historique, clients/documents selon permissions, indicateurs utiles. Tests.
>>> STOP : rapport.

=== PHASE 4 : ÉTAPE 9 — ESPACE CLIENT ===
Profil (champs autorisés), catalogue, demandes de réservation + suivi, devis et versions + acceptation si autorisée, paiements, factures/documents (téléchargement sécurisé), historique. Tests d'isolation des données entre clients.
>>> STOP : rapport.

=== PHASE 5 : ÉTAPE 10 — FACTURATION ET DOCUMENTS ===
Audit de V7 d'abord : création depuis devis accepté, snapshot, numérotation unique (séquence), génération PDF réelle, stockage, téléchargement sécurisé, permissions, erreurs, idempotence. Mentions légales/fiscales tunisiennes : NE PAS prétendre à la conformité, lister les points à faire valider par un professionnel.
>>> STOP : rapport.

=== PHASE 6 : ÉTAPE 11 — NOTIFICATIONS ===
Événements : demande créée, changement de statut, devis, paiement, facture dispo, rappels transfert. Canaux : in-app + email (SMS optionnel). Gestion des échecs d'envoi, anti-doublons, historique.
>>> STOP : rapport.

=== PHASE 7 : ÉTAPE 12 — TESTS COMPLETS ET SÉCURITÉ ===
Unitaires, intégration PostgreSQL, Vitest, E2E ; authN/authZ, isolation clients, validation des entrées, idempotence, concurrence, transactions, cohérence prix/devis, sécurité documents, protection endpoints admin, secrets hors du code, désactivation des comptes/données de test en prod. Expliquer et exécuter tout test ignoré.
>>> STOP : rapport.

=== PHASE 8 : ÉTAPES 13-14 — UX PREMIUM + PRODUCTION ===
UX : identité premium, pages véhicules, filtres/formulaires, responsive, états chargement/erreur, accessibilité, performance. 
Prod : profils dev/test/prod, PostgreSQL prod, vérification Flyway, secrets/variables d'env, HTTPS, logs/monitoring, sauvegarde/restauration, CORS/sécurité, stockage sécurisé des documents, build prod front+back, tests critiques, procédure de déploiement et rollback, limitations documentées. Ne déclarer "prêt pour la production" que si tous les contrôles ont réellement passé.

=== FORMAT DE CHAQUE RAPPORT ===
Fichiers modifiés | Fonctionnalités avec statut | Commandes lancées + résultats RÉELS (tests exécutés/échecs/erreurs/ignorés) | Tâches restantes | Risques.

Commence par la PHASE 0 maintenant.
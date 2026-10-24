# Rapport d'étape 4 — Catalogue de véhicules

**Date de validation finale :** 5 octobre 2026  
**État :** implémenté et vérifié sur environnements locaux de test; pas une recette de production.

## Périmètre livré

| Domaine | Contrat livré | Frontend | État |
| --- | --- | --- | --- |
| Marques | CRUD, slug unique généré, publication, logo URL, suppression refusée si utilisée | Pages publiques et gestion admin | Implémenté |
| Catégories | CRUD, slug unique généré, publication, suppression refusée si utilisée | Pages publiques et gestion admin | Implémenté |
| Véhicules | CRUD, marque/catégorie, statut, année, capacité, portes, transmission, carburant, équipements | Liste flotte et formulaire admin | Implémenté |
| Tarifs | `BigDecimal`: jour, semaine et tarif chauffeur facultatif avec devise ISO | Fiche publique et formulaire admin | Implémenté |
| Images | JPEG/PNG/WebP, taille/signature contrôlées, UUID, stockage local hors DB, métadonnées et ordre | Upload, choix image principale, retrait | Implémenté |
| Catalogue public | Recherche, marque, catégorie, prix min/max, places, transmission, chauffeur, tri whitelisté, pagination | États chargement/vide/erreur, filtres URL, fiche véhicule | Implémenté |
| RBAC catalogue | `vehicles:read/manage`, `brands:read/manage`, `categories:read/manage`, audit CRUD | Formulaires masqués aux rôles lecture seule | Implémenté |

Les statuts publics sont limités à `AVAILABLE` et aux marques/catégories actives. Les filtres ne calculent pas de disponibilité sur une période et aucune réservation n'est créée.

## Modèle de données

La migration `V3__vehicle_catalogue.sql` complète le schéma avec les descriptions/slugs/horodatages de marque et catégorie, les tarifs et caractéristiques véhicule, les statuts/transmission/carburant contrôlés, les images ordonnées et les équipements. Les métadonnées d'image sont persistées en PostgreSQL; les fichiers sont enregistrés sous `STORAGE_LOCAL_PATH` et servis via `/api/files/{key}`. `STORAGE_LOCAL_PATH` est configurable; la valeur locale par défaut est sous le dossier utilisateur.

## Vérifications

| Contrôle | Résultat |
| --- | --- |
| Maven tests sur PostgreSQL 18 (auth, CRUD/RBAC catalogue et stockage) | 15/15 passés, dont cycle image et mises à jour successives |
| Vitest frontend | 10/10 passés |
| Playwright | 5/5 passés; parcours admin réel marques → catégories → véhicule → fiche publique et client refusé avec HTTP 403. Deux scénarios publics utilisent des réponses contrôlées pour filtres et états vide/erreur. |
| Build Angular strict | Réussi; seule alerte CSS préexistante de l'accueil |
| Contrôle visuel | Captures catalogue à 1366 px et 375 px, formulaire admin à 375 px; débordement de l'en-tête admin corrigé, largeur finale 375 px |
| Flyway sur PostgreSQL 18 vierge | V1, V2 et V3 appliquées avec succès |
| Hibernate `ddl-auto=validate` PostgreSQL | Démarrage réussi sans écart de mapping |
| `GET /actuator/health` sur instance PostgreSQL temporaire | 200, `UP` |
| `GET /api/vehicles?page=0&size=12&sort=newest` | 200, page vide attendue |

Le test final utilise la base temporaire UTF-8 `elite_final_test` sur PostgreSQL 18.3. Flyway a lui-même appliqué V1–V3 puis Hibernate a validé le schéma, avant les 15 tests. Ce cluster a été arrêté et supprimé. La base PostgreSQL permanente n'a pas été modifiée ni validée. Le serveur local utilisé par Playwright fonctionne en profil `test` H2 : le parcours admin de création n'intercepte aucune API. Les tests backend ont notamment confirmé upload/lecture, conservation après deux modifications, refus de suppression d'une image référencée et nettoyage après suppression du véhicule.

Un vrai appel client à l'API admin avait exposé une transformation incorrecte de 403 en 401 lors du dispatch `/error` de Tomcat. La règle autorise maintenant uniquement le dispatch interne `ERROR`; les routes admin ordinaires restent protégées, et Playwright vérifie le 403 attendu.

## Hors périmètre

Aucune réservation, disponibilité par date, calendrier, chevauchement, devis, paiement, facture, chauffeur opérationnel, notification, demande client ou service métier n'a été implémenté. Ces modules restent hors étape 4.

## Points de déploiement à traiter

Le stockage livré est local et doit être remplacé ou adapté à un stockage persistant partagé avant un déploiement multi-instance. Il reste à fournir la base permanente, les secrets, les origines CORS de production, HTTPS, sauvegardes, supervision et politique de rétention des fichiers. Un upload abandonné à la fermeture du formulaire n'a pas encore de collecte automatique; le retrait explicite d'une photo non enregistrée la supprime. Les listes de référentiels Angular sont limitées aux 100 premières entrées, bien que l'API soit paginée.

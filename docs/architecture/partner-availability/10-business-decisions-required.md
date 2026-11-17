# 10 — Décisions métier requises

Légende : **Bloquant** = le développement de la couche concernée ne peut pas démarrer sans décision. Les recommandations ne sont pas des décisions validées.

## 1. Décisions déjà validées (ne pas remettre en cause)

- Elite ne possède aucun véhicule ; les véhicules appartiennent à des partenaires.
- Disponibilité confirmée par le partenaire, enregistrée par Elite, réputée garantie.
- Réservation directe par le client, sans nouvelle validation partenaire.
- Disponibilités liées à un véhicule et à une période.
- Une réservation confirmée n'est pas annulée pour retrait du véhicule.
- Cycle de vie : MAINTENANCE interdite avec réservation future CONFIRMED ; suppression interdite avec historique ; UNAVAILABLE = indisponibilité temporaire.
- Commission par niveau commercial, configurable par Elite, non modifiable par le partenaire, snapshot par réservation, non rétroactive.
- **D-PA-05 validé (Option A)** : chaque disponibilité garantie est liée simultanément à un véhicule et au partenaire porteur de l'engagement. Le partenaire porteur est figé à la création ; un changement de partenaire du véhicule ne modifie jamais les garanties existantes. Détail : `review/07-dpa-05-validation-record.md`.
- **D-PA-12 validé** : un véhicule ne change pas de partenaire tant qu'un engagement actif existe (garantie active, réservation future CONFIRMED, réservation IN_PROGRESS, autre engagement identifié). Transfert contrôlé par Elite, tracé, sans modification rétroactive. Détail : `review/08-owner-decisions-validation-record.md` §3.
- **D-PA-14 identifiant validé** : chevauchement des disponibilités garanties. La règle de traitement reste à préciser.
- **Historique des rattachements validé** : table dédiée `vehicle_partner_assignments` (review/08 §4).
- **Référence réservation → garantie validée dans son principe** : obligatoire pour toute nouvelle réservation, NULL toléré pour l'historique (review/08 §5).

## 2. Décisions ouvertes

> Registre canonique des identifiants : `review/08-owner-decisions-validation-record.md` §2.2. Les identifiants de ce tableau ont été réalignés sur ce registre ; en cas de divergence, le registre fait foi.

| ID | Question | Options | Recommandation | Bloquant pour | Avis externe |
|---|---|---|---|---|---|
| D-PA-01 | Durée de validité d'une garantie | (a) jusqu'à date de fin saisie ; (b) durée maximale fixe ; (c) expiration automatique à J+N sans réservation | (a) avec alerte avant expiration ; pas d'expiration automatique silencieuse | Schéma `guaranteed_availabilities` (statut EXPIRED) | Non |
| D-PA-12 (validé) | Changement de partenaire d'un véhicule | (a) interdit ; (b) autorisé avec procédure et historique ; (c) autorisé librement | (b), avec garanties futures retirées et historique conservé | Rattachement, V23 | Juridique (transfert de propriété) |
| D-PA-03 | Modification d'une garantie avant réservation | (a) libre ; (b) libre avec motif ; (c) interdite après création | (b) : modifiable tant qu'aucune réservation ne la couvre | Endpoint PUT | Non |
| D-PA-07 | Retrait par le partenaire d'un véhicule garanti | (a) interdit ; (b) préavis puis retrait sans pénalité ; (c) retrait immédiat avec pénalité | (b) avec préavis minimal à fixer ; pas de retrait si réservation | Endpoint withdraw, pénalités | Juridique, comptable |
| D-PA-14 (identifiant validé) | Chevauchement de garanties (ancien « D-PA-05 » chevauchement ; règle de traitement à préciser) | (a) interdit ; (b) fusion automatique ; (c) fusion manuelle | (a) à la création, fusion manuelle par l'admin | EXCLUDE, cas C5 | Non |
| D-PA-04 | Réservation PENDING sur garantie qui doit être retirée | (a) bloque le retrait ; (b) n'empêche rien ; (c) annulée automatiquement | (a) jusqu'à confirmation ou rejet, cohérent avec la prudence | Retrait | Non |
| D-PA-16 (provisoire) | Maintenance planifiée dans une garantie | (a) interdite si garantie ; (b) autorisée et réduit la garantie ; (c) alerte admin | (a) : la maintenance ne doit pas empiéter sur une garantie | Statut `MAINTENANCE` | Non |
| D-PA-17 (provisoire) | Partenaire désactivé avec garanties futures | (a) garanties suspendues ; (b) garanties maintenues ; (c) retrait forcé | (a) : invisibles au client, réactivées à la réactivation | Recherche | Juridique |
| D-PA-09 | Pénalités en cas de non-respect | (a) aucune ; (b) pénalité forfaitaire ; (c) pourcentage du contrat | Ne pas décider sans avis juridique et comptable | Pénalités, D3 | **Oui** (juridique, comptable) |
| D-PA-10 | Moment de confirmation de la réservation et paiement intégral (D5) | Voir D5 | Non tranché ici | Workflow de réservation | Prestataire de paiement |
| D-PA-11 | Droit de création de partenaires | Rôles autorisés | `MANAGER` et `SUPER_ADMIN` | Permissions | Non |
| D-PA-18 (provisoire) | Rôle `PARTNER` | Créer ou non un accès partenaire en phase 1 | Reporter après le module admin | Section 5 de 05-api-contracts | Non |
| D-PA-13 | Durée minimale de location pour la recherche | Hérité des règles existantes | À confirmer | Recherche | Non |

## 3. Décisions bloquantes les plus urgentes

1. D-PA-14 (chevauchement) : identifiant validé ; la règle de traitement bloque la contrainte d'exclusion.
2. D-PA-12 validé : la procédure de transfert reste à implémenter.
3. D-PA-07 et D-PA-09 (retrait et pénalités) : bloquent l'endpoint de retrait et les conditions contractuelles.

## 4. Points techniques qui dépendent des décisions

- Schéma : D-PA-01, D-PA-14 (chevauchement). D-PA-05 (partenaire porteur) est validé et ne bloque plus.
- Recherche : D-PA-04, D-PA-08.
- Admin : D-PA-03, D-PA-07, D-PA-16.
- Permissions : D-PA-11, D-PA-18.

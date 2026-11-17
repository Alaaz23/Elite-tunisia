# Réconciliation des paiements (Lot A.1, Décision 3)

**Statut : ARCHITECTURE DOCUMENTÉE, NON IMPLÉMENTÉE.** Aucun fournisseur fictif, aucune API de consultation et aucun job de réconciliation n'existent dans le code. Le fournisseur actuel (`SimulatedPaymentProvider`) ne fournit pas de consultation de statut. La réconciliation réelle dépend du contrat et de l'API du fournisseur de paiement, qui ne sont pas encore définis.

## 1. États

| État | Source | Signification | Terminal |
|---|---|---|---|
| `PENDING` | enum `PaymentStatus` (défaut à la création) | Paiement enregistré, issue fournisseur non confirmée. | Non |
| `AUTHORIZED` | enum `PaymentStatus` | Autorisation accordée, capture non confirmée. Compté comme « en cours » par `PaymentService`. | Non |
| `CAPTURED` | enum `PaymentStatus` | Encaissement confirmé par le fournisseur. | Oui pour la clé d'idempotence |
| `FAILED` | enum `PaymentStatus` | Refus confirmé par le fournisseur. | Oui pour la clé d'idempotence |
| `UNKNOWN` | **état logique, absent de l'enum** | Réponse fournisseur absente ou inconnue, ou finalisation échouée après un appel fournisseur. Ne doit pas être confondu avec `FAILED`. | Non |

Ajouter `UNKNOWN` à l'enum `PaymentStatus` ferait partie de l'implémentation future. Ce n'est pas fait dans ce passage, car cela impacterait la base (colonne `status` en `VARCHAR(30)`, avec contrainte éventuelle) et les règles d'idempotence.

## 2. Transitions autorisées

```
(création) ──► PENDING
PENDING ──► CAPTURED   confirmation fournisseur de capture
PENDING ──► FAILED     refus confirmé par le fournisseur (jamais sur simple erreur réseau ou timeout)
PENDING ──► UNKNOWN    réponse inconnue, timeout, ou finalisation échouée après capture (logique, à persister si retenu)
UNKNOWN ──► CAPTURED   réconciliation : le fournisseur confirme la capture
UNKNOWN ──► FAILED     réconciliation : le fournisseur confirme le refus ou l'absence de transaction
UNKNOWN ──► PENDING    réconciliation : le fournisseur confirme qu'une opération est encore en cours
AUTHORIZED ──► CAPTURED / FAILED / CANCELLED   selon le contrat fournisseur (non défini)
CAPTURED ──► REFUNDED  hors périmètre de ce document (remboursement non simulé)
```

Interdits :
- `PENDING` ou `UNKNOWN` vers `FAILED` sans confirmation du fournisseur.
- Toute transition depuis `CAPTURED` ou `FAILED` vers un autre état de paiement pour la même clé.
- Un second appel de capture pour une clé dont l'état n'est pas déterminé.

## 3. Règles métier

- Un timeout n'est jamais un `FAILED`.
- Aucun second débit automatique : une opération de réconciliation consulte le fournisseur, elle n'appelle jamais une capture.
- Une nouvelle tentative de communication conserve l'identité du paiement initial (même `payment_id`, même clé d'idempotence).
- Une nouvelle opération de paiement n'est autorisée que lorsque le paiement précédent est déterminé (`CAPTURED` ou `FAILED`).
- Une même opération de réconciliation ne doit pas produire plusieurs transitions ou événements incohérents : la transition doit être conditionnelle à l'état courant verrouillé (`SELECT ... FOR UPDATE` sur le paiement), et la relecture doit confirmer l'état attendu avant écriture.
- Chaque vérification et chaque transition doit être traçable (journal d'événements de paiement à créer, ou événement d'audit obligatoire, avec horodatage, acteur ou job, référence fournisseur et ancien/nouvel état).
- Les paiements `UNKNOWN` doivent rester consultables (liste filtrée par statut) pour une intervention manuelle.

## 4. Interface fournisseur à définir (non implémentée)

Interface de consultation, à brancher sur le fournisseur réel :

```java
// Non implémentée : contrat à valider avec le fournisseur réel.
public interface PaymentStatusProvider {
    /** Consulte le statut réel d'une opération, sans jamais déclencher de débit. */
    PaymentStatusResult queryStatus(String providerReference, String idempotencyKey);
}

public record PaymentStatusResult(ProviderState state, String providerReference, String rawCode) {}

public enum ProviderState { CAPTURED, FAILED, PENDING, NOT_FOUND, UNAVAILABLE }
```

Règles d'usage :
- `NOT_FOUND` ne signifie pas `FAILED` sans règle explicite du fournisseur (cas à décider).
- `UNAVAILABLE` laisse le paiement en `UNKNOWN` ou `PENDING`, sans transition.
- Aucune méthode d'interface ne doit exposer une capture ou un remboursement.

## 5. Responsabilités

| Composant | Responsabilité | Ne fait pas |
|---|---|---|
| `PaymentService` (actuel) | Réservation du paiement en transaction, appel de capture hors verrou, finalisation. Refuse toute nouvelle opération tant que le précédent est indéterminé. | Ne consulte pas le fournisseur. Ne transforme pas `PENDING` en `FAILED` sur erreur réseau. |
| Service de réconciliation (futur) | Sélectionne les paiements `PENDING`/`AUTHORIZED`/`UNKNOWN` anciens, appelle `PaymentStatusProvider.queryStatus`, applique la transition conditionnelle, journalise. | Ne déclenche jamais de capture. |
| `PaymentStatusProvider` (futur) | Adaptateur vers l'API réelle du fournisseur. | N'écrit pas en base. |
| Journal d'audit | Trace obligatoire de chaque vérification et transition. | Ne masque pas les erreurs (écriture obligatoire, pas best-effort). |

## 6. Points à décider avant implémentation

1. Contrat du fournisseur : existe-t-il une API de consultation de statut par référence ou par clé d'idempotence ?
2. Signification exacte de « non trouvé » côté fournisseur (absence de transaction ou transaction en cours).
3. Délai à partir duquel un `PENDING` devient éligible à la réconciliation.
4. Persistance de `UNKNOWN` : nouvelle valeur d'enum et migration Flyway versionnée, ou statut dérivé.
5. Politique de remboursement (hors périmètre, aucune simulation ajoutée).

## 7. Statut

- Architecture et transitions : **documentées**.
- Interface `PaymentStatusProvider` : **spécifiée, non implémentée**.
- Réconciliation : **NON CORRIGÉE**, en attente de décisions et du contrat fournisseur.

## 8. Écarts constatés entre le modèle et le code (revue Lot A.4)

Revue faite sur `PaymentStatus`, `PaymentEntity`, `PaymentService` et `BookingRepository` :

- L'enum `PaymentStatus` contient `PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED`. `AUTHORIZED`, `REFUNDED` et `CANCELLED` sont présents dans le code mais ne sont pas décrits dans la table de la section 2 (seul `REFUNDED` est évoqué comme hors périmètre).
- `UNKNOWN` n'existe ni dans l'enum ni dans la colonne `status` (`VARCHAR(30)`, `PaymentEntity`) : le modèle logique reste à persister.
- `PaymentService` refuse une nouvelle capture tant qu'un paiement est `PENDING` ou `AUTHORIZED` pour le devis (contrôle conforme à la section 3).
- Aucune transition automatique vers `FAILED` ou `CAPTURED` n'est faite hors de l'appel fournisseur ; aucun service de réconciliation n'existe (section 5 : futur).
- Aucune classe `PaymentStatusProvider` n'existe dans `backend/src/main/java` : conforme au statut « non implémentée ».
- Les événements de paiement ne sont pas journalisés par une table dédiée : la traçabilité repose sur l'enregistrement `payments` et l'audit existant. Une table d'événements de réconciliation reste à concevoir avec le contrat fournisseur.

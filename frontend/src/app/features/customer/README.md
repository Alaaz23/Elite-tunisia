# Espace client

État réel (Lot 7). Les écrans ci-dessous existent et sont branchés sur des endpoints backend réels, vérifiés par des tests `HttpTestingController` sur les contrats HTTP. Aucun test navigateur ni test d'intégration contre un backend lancé n'a été exécuté.

## Écrans

| Route | Composant | Endpoints utilisés | Statut |
|---|---|---|---|
| `/customer/dashboard` | `CustomerDashboardComponent` | aucun (navigation) | Implémenté |
| `/customer/bookings` | `BookingsListComponent` | `GET /api/bookings`, `GET /api/bookings/policy` | Implémenté |
| `/customer/bookings/:id` | `BookingDetailComponent` | `GET /api/bookings/{id}`, `POST /api/bookings/{id}/cancel` | Implémenté; message de demande non bloquante |
| `/customer/quotes` | `QuotesListComponent` | `GET /api/customer/quotes`, `GET /api/bookings/policy` | Implémenté |
| `/customer/quotes/:id` | `QuoteDetailComponent` | `GET /api/customer/quotes/{id}`, `POST …/accept\|reject\|request-change`, `POST …/payments` | Implémenté; paiement simulé |
| `/customer/invoices` | `CustomerInvoicesComponent` | `GET /api/invoices` (liste) | Implémenté (liste avec lien vers le détail et téléchargement PDF) |
| `/customer/invoices/:id` | `CustomerInvoiceDetailComponent` | `GET /api/invoices/{id}`, `GET /api/invoices/{id}/pdf` | Implémenté (détail, PDF, erreurs 404/403/401/500) |
| `/customer/profile` | `CustomerProfileComponent` | `GET /api/customer/profile`, `PUT /api/customer/profile` | Implémenté |
| `/customer/notifications` | placeholder | aucun | Non implémenté |

## Règles métier reflétées dans l'interface

- Une demande de réservation est en `PENDING`. Elle n'expire pas automatiquement, ne bloque pas les dates et n'a pas d'échéance de conservation. Plusieurs demandes PENDING peuvent coexister pour les mêmes dates. L'écran l'indique au client. La disponibilité est vérifiée par l'agence lors de la confirmation; un conflit y est refusé avec `VEHICLE_NOT_AVAILABLE`.
- Un paiement `CAPTURED` ne confirme pas la réservation. Seul `PATCH /api/admin/bookings/{id}/confirm` passe la réservation à `CONFIRMED`. L'interface affiche « Paiement enregistré … en attente de validation par l'agence », jamais « réservation confirmée ».
- Le paiement envoie le montant total du devis persisté. Le client ne calcule aucun prix.
- Un paiement déjà en cours n'est pas renvoyé. La clé d'idempotence reste la même pour un devis tant que le résultat est incertain (erreur réseau, `PAYMENT_IN_PROGRESS`).
- `errorInterceptor` transmet le code métier du backend dans `ApiHttpError.code` (`PAYMENT_IN_PROGRESS`, `QUOTE_ALREADY_PAID`, `IDEMPOTENCY_CONFLICT`, `PAYMENT_AMOUNT_MISMATCH`).

## Non exposé par le backend (non implémenté côté client)

- **Historique des paiements** : aucun endpoint de liste de paiements. Un paiement n'est visible qu'au moment de sa création. Il faut un endpoint backend dédié avant d'afficher un historique.
- **Remboursement, pénalité, acompte** : hors périmètre MVP; rien n'est affiché.
- **Détail et PDF de facture côté client** : le service `InvoiceApiService` les expose, mais aucun écran client ne les utilise encore.
- **Notifications client** : placeholder.

## Tests

- `features/bookings/booking.spec.ts` : création, disponibilité, annulation, demande PENDING non bloquante, accès refusé.
- `features/quotes/quotes.spec.ts` : acceptation, refus, demande de modification, devis expiré, paiement `CAPTURED`, `FAILED`, `PAYMENT_IN_PROGRESS`, absence de second envoi.
- `core/interceptors/error.interceptor.spec.ts` : transmission du statut et du code.

# Création du premier administrateur (SUPER_ADMIN)

Aucun endpoint public ne permet de devenir administrateur : l'inscription
(`RegisterRequest`) ne contient aucun champ de rôle. Le premier `SUPER_ADMIN`
est donc créé hors application, directement en base, avec
[`database/admin/create-first-admin.sql`](../database/admin/create-first-admin.sql).

Le script :

- lit `ELITE_ADMIN_EMAIL` et `ELITE_ADMIN_BCRYPT` dans l'environnement
  (le mot de passe en clair n'est jamais transmis à la base) ;
- refuse un e-mail invalide ou une valeur qui n'est pas un hash bcrypt ;
- refuse (code de sortie 3) s'il existe déjà un `SUPER_ADMIN` ;
- crée `users`, `user_roles` et `employees` dans une seule transaction.

## Procédure

1. Générer un hash bcrypt hors production, sans laisser le mot de passe dans
   l'historique du shell (par exemple avec `BCryptPasswordEncoder` de Spring,
   ou `select crypt('<mdp>', gen_salt('bf', 10))` avec pgcrypto sur une base
   locale jetable).
2. Exporter les variables, puis exécuter le script sur la base cible :

```powershell
$env:ELITE_ADMIN_EMAIL  = 'admin@votre-domaine.tld'
$env:ELITE_ADMIN_BCRYPT = '<hash bcrypt>'
psql -v ON_ERROR_STOP=1 -h <hote> -U <utilisateur> -d <base> -f database/admin/create-first-admin.sql
Remove-Item Env:ELITE_ADMIN_BCRYPT
```

3. Se connecter puis changer le mot de passe immédiatement.

## Limites

- L'infrastructure de production n'étant pas connue, aucune configuration
  d'hébergement ou de gestion de secrets n'est supposée ici : stocker le hash
  et les accès base dans le gestionnaire de secrets de la cible.
- La procédure a été validée uniquement sur une base jetable clonée dans le
  cluster PostgreSQL de test isolé (refus d'un mauvais hash, création, refus
  d'une seconde exécution).
- Rien n'empêche aujourd'hui un `SUPER_ADMIN` de se désactiver ou de supprimer
  le dernier `SUPER_ADMIN` via l'API : décision à prendre.

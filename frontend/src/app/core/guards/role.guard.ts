import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { map } from 'rxjs';

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const allowedRoles = route.data['roles'] as string[] | undefined;
  const router = inject(Router);
  const decision = () => allowedRoles && auth.hasAnyRole(allowedRoles) ? true : router.createUrlTree(['/forbidden']);
  if (auth.session()) return decision();
  return auth.restoreSession().pipe(map(() => decision()));
};
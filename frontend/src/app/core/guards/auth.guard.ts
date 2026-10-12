import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.session()) return true;
  return auth.restoreSession().pipe(map((valid) => valid ? true : router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } })));
};
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ApiHttpError } from '../models/api-http-error';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  return next(request).pipe(
  catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
    if (error.status === 401 && request.headers.has('Authorization') && !request.url.includes('/api/auth/')) {
      return auth.refreshSession().pipe(
        switchMap((session) => {
          const headers: Record<string, string> = { Authorization: `Bearer ${session.accessToken}` };
          if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method)) {
            const csrfToken = auth.csrfHeaderToken();
            if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;
          }
          return next(request.clone({ setHeaders: headers, withCredentials: true }));
        }),
        catchError(() => {
          auth.clearSession();
          return throwError(() => new ApiHttpError(401, 'Votre session a expiré. Veuillez vous reconnecter.'));
        }),
      );
    }
    if (error.status === 401) auth.clearSession();
    const safeMessage = error.status === 0
      ? 'Le service est momentanément indisponible. Réessayez dans quelques instants.'
      : error.status >= 500
        ? 'Une erreur est survenue. Veuillez réessayer.'
        : error.status === 401
          ? error.error?.detail === 'Votre compte a été désactivé.'
            ? 'Votre compte a été désactivé.'
            : 'Votre session a expiré ou vos identifiants sont incorrects.'
          : error.status === 403
            ? 'Vous n’avez pas les droits nécessaires pour accéder à cette page.'
        : error.error?.detail ?? 'Vérifiez les informations saisies puis réessayez.';
    return throwError(() => new ApiHttpError(error.status, safeMessage));
  }),
  );
};
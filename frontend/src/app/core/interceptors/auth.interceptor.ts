import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  if (!request.url.includes('/api/')) return next(request);
  const accessToken = auth.accessToken();
  const authenticatedRequest = accessToken
    ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` }, withCredentials: true })
    : request;
  const isAuthEndpoint = request.url.includes('/api/auth/');
  if (isAuthEndpoint || !['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method)) return next(authenticatedRequest);
  return auth.refreshCsrfToken().pipe(switchMap((csrfToken) => next(authenticatedRequest.clone({
    setHeaders: { 'X-XSRF-TOKEN': csrfToken },
    withCredentials: true,
  }))));
};
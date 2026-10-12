import { HttpClient } from '@angular/common/http';
import { HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AuthSession } from '../models/auth-session.model';

interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  userId: string;
  email: string;
  roles: string[];
}

interface CurrentUserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  roles: string[];
  status: 'ACTIVE' | 'DISABLED';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly sessionState = signal<AuthSession | null>(null);
  private csrfToken: string | null = null;
  private refreshInFlight: Observable<AuthSession> | null = null;
  readonly session = this.sessionState.asReadonly();

  constructor(private readonly http: HttpClient) {}

  login(email: string, password: string): Observable<AuthSession> {
    return this.csrfPost<TokenResponse>('/api/auth/login', { email, password }).pipe(
      switchMap((tokens) => this.activate(tokens)),
    );
  }

  register(details: { firstName: string; lastName: string; email: string; phone: string; password: string; confirmPassword: string }): Observable<AuthSession> {
    return this.csrfPost<TokenResponse>('/api/auth/register', details).pipe(
      switchMap((tokens) => this.activate(tokens)),
    );
  }

  restoreSession(): Observable<boolean> {
    return this.refreshSession().pipe(
      map(() => true),
      catchError(() => {
        this.clearSession();
        return of(false);
      }),
    );
  }

  logout(): Observable<void> {
    return this.csrfPost<void>('/api/auth/logout', {}).pipe(
      catchError((error: unknown) => throwError(() => error)),
      finalize(() => this.clearSession()),
    );
  }

  accessToken(): string | null {
    return this.sessionState()?.accessToken ?? null;
  }

  csrfHeaderToken(): string | null {
    return this.csrfToken;
  }

  refreshCsrfToken(): Observable<string> {
    return this.fetchCsrf().pipe(map((response) => response.token));
  }

  clearSession(): void {
    this.sessionState.set(null);
  }

  hasAnyRole(roles: string[]): boolean {
    return this.sessionState()?.roles.some((role) => roles.includes(role)) ?? false;
  }

  refreshSession(): Observable<AuthSession> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.fetchCsrf().pipe(
        switchMap(() => this.postWithCsrf<TokenResponse>('/api/auth/refresh', {})),
        switchMap((tokens) => this.activate(tokens)),
        finalize(() => this.refreshInFlight = null),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }

  private csrfPost<T>(path: string, body: unknown): Observable<T> {
    return this.fetchCsrf().pipe(switchMap(() => this.postWithCsrf<T>(path, body)));
  }

  private fetchCsrf(): Observable<{ token: string }> {
    return this.http.get<{ token: string }>(`${this.baseUrl}/api/auth/csrf`, { withCredentials: true }).pipe(
      tap((response) => this.csrfToken = response.token),
    );
  }

  private postWithCsrf<T>(path: string, body: unknown): Observable<T> {
    const headers = new HttpHeaders(this.csrfToken ? { 'X-XSRF-TOKEN': this.csrfToken } : {});
    return this.http.post<T>(`${this.baseUrl}${path}`, body, { headers, withCredentials: true });
  }

  private activate(tokens: TokenResponse): Observable<AuthSession> {
    const provisional: AuthSession = { ...tokens, firstName: '', lastName: '', phone: '', status: 'ACTIVE' };
    this.sessionState.set(provisional);
    return this.fetchCsrf().pipe(
      switchMap(() => this.http.get<CurrentUserResponse>(`${this.baseUrl}/api/auth/me`)),
      map((profile) => ({ ...provisional, userId: profile.id, ...profile })),
      tap((session) => this.sessionState.set(session)),
      catchError((error: unknown) => {
        this.clearSession();
        return throwError(() => error);
      }),
    );
  }
}
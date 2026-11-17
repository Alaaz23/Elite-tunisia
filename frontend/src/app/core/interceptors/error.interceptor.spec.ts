import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ApiHttpError } from '../models/api-http-error';
import { errorInterceptor } from './error.interceptor';

const auth = { clearSession: vi.fn(), refreshSession: vi.fn(), csrfHeaderToken: () => null };

describe('errorInterceptor', () => {
  let http: HttpClient;
  let testing: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    testing = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => testing.verify());

  function failWith(status: number, body: Record<string, string>): Promise<unknown> {
    const result = new Promise<unknown>((resolve) => {
      http.get('/api/probe').subscribe({ error: (error) => resolve(error) });
    });
    testing.expectOne('/api/probe').flush(body, { status, statusText: 'error' });
    return result;
  }

  it('keeps the HTTP status and forwards the backend business code', async () => {
    const error = await failWith(409, { detail: 'Le paiement est déjà en cours.', code: 'PAYMENT_IN_PROGRESS' });
    expect(error).toBeInstanceOf(ApiHttpError);
    expect((error as ApiHttpError).status).toBe(409);
    expect((error as ApiHttpError).code).toBe('PAYMENT_IN_PROGRESS');
    expect((error as ApiHttpError).message).toBe('Le paiement est déjà en cours.');
  });

  it('leaves the code undefined when the backend sends none', async () => {
    const error = await failWith(400, { detail: 'Dates invalides.' });
    expect((error as ApiHttpError).status).toBe(400);
    expect((error as ApiHttpError).code).toBeUndefined();
    expect((error as ApiHttpError).message).toBe('Dates invalides.');
  });

  it('keeps the fixed French message for server errors without exposing the body', async () => {
    const error = await failWith(500, { detail: 'SQL: relation does not exist', code: 'INTERNAL' });
    expect((error as ApiHttpError).status).toBe(500);
    expect((error as ApiHttpError).message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect((error as ApiHttpError).code).toBe('INTERNAL');
  });

  function call(withSession = true): Promise<{ value?: unknown; error?: unknown }> {
    return new Promise((resolve) => {
      const headers: Record<string, string> = withSession ? { Authorization: 'test-access' } : {};
      http.get('/api/probe', { headers }).subscribe({
        next: (value) => resolve({ value }),
        error: (error) => resolve({ error }),
      });
    });
  }

  it('passes a request with a valid session through without refreshing', async () => {
    const result = call();
    testing.expectOne('/api/probe').flush({ ok: true });
    expect(await result).toEqual({ value: { ok: true } });
    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(auth.clearSession).not.toHaveBeenCalled();
  });

  it('refreshes once after an expired access token and retries the request', async () => {
    auth.refreshSession.mockReturnValue(of({ accessToken: 'new-access' }));
    const result = call();
    testing.expectOne('/api/probe').flush({}, { status: 401, statusText: 'Unauthorized' });
    const retry = testing.expectOne('/api/probe');
    expect(retry.request.headers.get('Authorization')).toContain('new-access');
    retry.flush({ ok: true });
    expect(await result).toEqual({ value: { ok: true } });
    expect(auth.refreshSession).toHaveBeenCalledTimes(1);
    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('clears the session and redirects to login with the requested page when the refresh token is rejected', async () => {
    auth.refreshSession.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const result = call();
    testing.expectOne('/api/probe').flush({}, { status: 401, statusText: 'Unauthorized' });
    const { error } = await result;
    expect(error).toBeInstanceOf(ApiHttpError);
    expect((error as ApiHttpError).status).toBe(401);
    expect((error as ApiHttpError).message).toBe('Votre session a expiré. Veuillez vous reconnecter.');
    expect(auth.clearSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/' } });
  });

  it('does not treat a server error during the retry as an expired session', async () => {
    auth.refreshSession.mockReturnValue(of({ accessToken: 'new-access' }));
    const result = call();
    testing.expectOne('/api/probe').flush({}, { status: 401, statusText: 'Unauthorized' });
    testing.expectOne('/api/probe').flush({ detail: 'SQL: relation does not exist' }, { status: 500, statusText: 'error' });
    const { error } = await result;
    expect((error as ApiHttpError).status).toBe(500);
    expect((error as ApiHttpError).message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('clears the session and redirects when the retried request is still unauthorized', async () => {
    auth.refreshSession.mockReturnValue(of({ accessToken: 'new-access' }));
    const result = call();
    testing.expectOne('/api/probe').flush({}, { status: 401, statusText: 'Unauthorized' });
    testing.expectOne('/api/probe').flush({}, { status: 401, statusText: 'Unauthorized' });
    const { error } = await result;
    expect((error as ApiHttpError).status).toBe(401);
    expect(auth.clearSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/' } });
  });
});

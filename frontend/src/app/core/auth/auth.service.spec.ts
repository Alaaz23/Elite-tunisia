import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { errorInterceptor } from '../interceptors/error.interceptor';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registers with CSRF, keeps the refresh cookie and hydrates the profile from the API', () => {
    let result: unknown;
    service.register({
      firstName: 'Amira', lastName: 'Ben Salem', email: 'amira@example.test', phone: '+216 20 123 456',
      password: 'Strong-Pass-123!', confirmPassword: 'Strong-Pass-123!',
    }).subscribe((session) => result = session);

    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    const register = http.expectOne('/api/auth/register');
    expect(register.request.method).toBe('POST');
    expect(register.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(register.request.withCredentials).toBe(true);
    expect(register.request.body.role).toBeUndefined();
    register.flush({ accessToken: 'memory-only-access', expiresIn: 900, userId: 'user-1', email: 'amira@example.test', roles: ['CUSTOMER'] });

    http.expectOne('/api/auth/csrf').flush({ token: 'rotated-csrf-token' });
    const currentUser = http.expectOne('/api/auth/me');
    expect(currentUser.request.headers.get('Authorization')).toBe('Bearer memory-only-access');
    currentUser.flush({ id: 'user-1', firstName: 'Amira', lastName: 'Ben Salem', email: 'amira@example.test', phone: '+216 20 123 456', roles: ['CUSTOMER'], status: 'ACTIVE' });

    expect(service.session()?.firstName).toBe('Amira');
    expect(service.session()?.roles).toEqual(['CUSTOMER']);
    expect(service.csrfHeaderToken()).toBe('rotated-csrf-token');
    expect(result).toEqual(service.session());
  });

  it('logs out through the CSRF-protected API and clears the in-memory session', () => {
    service.logout().subscribe();
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    const logout = http.expectOne('/api/auth/logout');
    expect(logout.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(logout.request.withCredentials).toBe(true);
    logout.flush(null, { status: 204, statusText: 'No Content' });
    expect(service.session()).toBeNull();
  });

  it('does not restore a session when the refresh cookie is invalid', () => {
    let restored: boolean | undefined;
    service.restoreSession().subscribe((value) => restored = value);
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    const refresh = http.expectOne('/api/auth/refresh');
    expect(refresh.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    refresh.flush({ detail: 'session ended' }, { status: 401, statusText: 'Unauthorized' });
    expect(restored).toBe(false);
    expect(service.session()).toBeNull();
  });
});
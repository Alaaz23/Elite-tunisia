import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/config/api.config';
import { AuthService } from '../../core/auth/auth.service';
import { AdminUsersComponent } from './admin-users.component';

function user(index: number) {
  return { id: `u-${index}`, firstName: 'User', lastName: `${index}`, email: `user${index}@example.test`, phone: '+21620000000', jobTitle: 'Agent', roles: ['AGENT'], status: 'ACTIVE' };
}

describe('AdminUsersComponent pagination', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
        { provide: AuthService, useValue: { session: () => ({ userId: 'current' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the real backend total and navigates to the next page with a bounded size', () => {
    const fixture = TestBed.createComponent(AdminUsersComponent);
    fixture.detectChanges();

    const first = http.expectOne((request) => request.url === '/api/admin/users');
    expect(first.request.params.get('page')).toBe('0');
    expect(Number(first.request.params.get('size'))).toBeLessThanOrEqual(100);
    first.flush({ content: [user(1)], totalElements: 60, totalPages: 3, page: 0, size: 25, first: true, last: false });
    http.expectOne('/api/admin/roles').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.count-label').textContent).toContain('60 comptes');
    const next = fixture.nativeElement.querySelector('.pagination button:last-child') as HTMLButtonElement;
    expect(next.disabled).toBe(false);
    next.click();

    const second = http.expectOne((request) => request.url === '/api/admin/users');
    expect(second.request.params.get('page')).toBe('1');
    second.flush({ content: [user(2)], totalElements: 60, totalPages: 3, page: 1, size: 25, first: false, last: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Page 2 / 3');
  });
});

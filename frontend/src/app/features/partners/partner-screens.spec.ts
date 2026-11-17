import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { roleGuard } from '../../core/guards/role.guard';
import { PagedResponse, PartnerApiService, PartnerSummary, GuaranteeSummary } from '../../core/services/partner-api.service';
import { AdminPartnersComponent } from '../admin/admin-partners.component';
import { AdminGuaranteesComponent } from '../admin/admin-guarantees.component';
import { PartnerPortalComponent } from '../partner/partner-portal.component';

function page<T>(content: T[]): PagedResponse<T> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1, first: true, last: true };
}

const partner: PartnerSummary = {
  id: 'partner-1',
  name: 'Ahmed Ben Salah',
  partnerType: 'INDIVIDUAL',
  email: 'ahmed@example.tn',
  phone: null,
  address: null,
  city: 'Tunis',
  taxIdentifier: null,
  active: true,
  userId: null,
  createdAt: '2030-01-01T00:00:00Z',
  updatedAt: '2030-01-01T00:00:00Z',
  deactivatedAt: null,
};

const guarantee: GuaranteeSummary = {
  id: 'guarantee-1',
  vehicleId: 'vehicle-1',
  partnerId: 'partner-1',
  partnerName: 'Ahmed Ben Salah',
  startsAt: '2030-02-01T10:00:00Z',
  endsAt: '2030-02-10T10:00:00Z',
  status: 'ACTIVE',
  reason: 'Entretien',
  createdByUserId: 'user-1',
  createdAt: '2030-01-01T00:00:00Z',
  withdrawnByUserId: null,
  withdrawnAt: null,
};

function authFor(role: string) {
  return {
    session: () => ({ roles: [role] }),
    restoreSession: () => of(null),
    hasAnyRole: (roles: string[]) => roles.includes(role),
  };
}

function apiMock() {
  return {
    listPartners: vi.fn(() => of(page([partner]))),
    listGuarantees: vi.fn(() => of(page([guarantee]))),
    getMyPartner: vi.fn(() => of(partner)),
    getMyVehicles: vi.fn(() => of([{ id: 'vehicle-1', brandName: 'Mercedes', model: 'Classe S', year: 2024, status: 'AVAILABLE' }])),
    getMyGuarantees: vi.fn(() => of([guarantee])),
  };
}

function text(element: HTMLElement): string {
  return element.textContent ?? '';
}

describe('AdminPartnersComponent role rendering', () => {
  function setup(role: string) {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [AdminPartnersComponent],
      providers: [
        provideRouter([]),
        { provide: PartnerApiService, useValue: api },
        { provide: AuthService, useValue: authFor(role) },
      ],
    });
    const fixture = TestBed.createComponent(AdminPartnersComponent);
    fixture.detectChanges();
    return { fixture, api };
  }

  it('shows the partner list and write actions to MANAGER', () => {
    const { fixture, api } = setup('MANAGER');
    const element: HTMLElement = fixture.nativeElement;
    expect(api.listPartners).toHaveBeenCalled();
    expect(text(element)).toContain('Ahmed Ben Salah');
    expect(text(element)).toContain('Nouveau partenaire');
    expect(text(element)).toContain('Modifier');
    expect(text(element)).toContain('Désactiver');
  });

  it('hides every write form and action from AGENT while keeping the list', () => {
    const { fixture } = setup('AGENT');
    const element: HTMLElement = fixture.nativeElement;
    expect(text(element)).toContain('Ahmed Ben Salah');
    expect(text(element)).not.toContain('Nouveau partenaire');
    expect(text(element)).not.toContain('Modifier');
    expect(text(element)).not.toContain('Désactiver');
    expect(text(element)).not.toContain('Nouveau partenaire');
    expect(text(element)).not.toContain('Affecter ou transférer');
    expect(element.querySelectorAll('form').length).toBe(1);
  });

  it('lets AGENT consult the assignment history lookup without the assignment form', () => {
    const { fixture } = setup('AGENT');
    const element: HTMLElement = fixture.nativeElement;
    expect(text(element)).toContain('Affectation');
    expect(text(element)).toContain('Afficher l’historique');
    expect(text(element)).not.toContain('Affecter ou transférer');
  });
});

describe('AdminGuaranteesComponent role rendering', () => {
  function setup(role: string) {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [AdminGuaranteesComponent],
      providers: [
        provideRouter([]),
        { provide: PartnerApiService, useValue: api },
        { provide: AuthService, useValue: authFor(role) },
      ],
    });
    const fixture = TestBed.createComponent(AdminGuaranteesComponent);
    fixture.detectChanges();
    return { fixture, api };
  }

  it('lets SUPER_ADMIN create and withdraw guarantees', () => {
    const { fixture } = setup('SUPER_ADMIN');
    const element: HTMLElement = fixture.nativeElement;
    expect(text(element)).toContain('Créer la garantie');
    expect(text(element)).toContain('Retirer');
  });

  it('shows AGENT the guarantee list without creation or withdrawal', () => {
    const { fixture } = setup('AGENT');
    const element: HTMLElement = fixture.nativeElement;
    expect(text(element)).toContain('Entretien');
    expect(text(element)).not.toContain('Créer la garantie');
    expect(text(element)).not.toContain('Retirer');
  });
});

describe('PartnerPortalComponent read-only space', () => {
  it('loads the partner profile, vehicles and guarantees without any write control', () => {
    const api = apiMock();
    TestBed.configureTestingModule({
      imports: [PartnerPortalComponent],
      providers: [{ provide: PartnerApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(PartnerPortalComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(api.getMyPartner).toHaveBeenCalledTimes(1);
    expect(api.getMyVehicles).toHaveBeenCalledTimes(1);
    expect(api.getMyGuarantees).toHaveBeenCalledTimes(1);
    expect(text(element)).toContain('Ahmed Ben Salah');
    expect(text(element)).toContain('Mercedes Classe S');
    expect(text(element)).toContain('Entretien');
    expect(element.querySelectorAll('button, form, input, select, textarea').length).toBe(0);
  });
});

describe('roleGuard for partner and admin areas', () => {
  function run(role: string, allowed: string[]) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authFor(role) }],
    });
    const route = { data: { roles: allowed } } as unknown as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, state));
  }

  it('allows a PARTNER into the partner space', () => {
    expect(run('PARTNER', ['PARTNER'])).toBe(true);
  });

  it('redirects a PARTNER away from admin screens', () => {
    const result = run('PARTNER', ['SUPER_ADMIN', 'MANAGER', 'AGENT']);
    expect(String(result)).toBe('/forbidden');
  });

  it('redirects a non-partner user away from the partner space', () => {
    const result = run('AGENT', ['PARTNER']);
    expect(String(result)).toBe('/forbidden');
  });
});

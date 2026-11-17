import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PartnerApiService } from './partner-api.service';

describe('PartnerApiService', () => {
  let service: PartnerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PartnerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists partners with pagination parameters on the admin endpoint', () => {
    service.listPartners(1, 10).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/partners');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('creates a COMPANY partner with the request body unchanged', () => {
    const body = { partnerType: 'COMPANY' as const, name: 'Garage Carthage', taxIdentifier: '123' };
    service.createPartner(body).subscribe();
    const request = http.expectOne('/api/admin/partners');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('updates a partner with PUT on its identifier', () => {
    const body = { partnerType: 'INDIVIDUAL' as const, name: 'Ali Ben Salah' };
    service.updatePartner('p-1', body).subscribe();
    const request = http.expectOne('/api/admin/partners/p-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('deactivates and activates a partner with POST actions', () => {
    service.deactivatePartner('p-1').subscribe();
    const deactivate = http.expectOne('/api/admin/partners/p-1/deactivate');
    expect(deactivate.request.method).toBe('POST');
    deactivate.flush({});

    service.activatePartner('p-1').subscribe();
    const activate = http.expectOne('/api/admin/partners/p-1/activate');
    expect(activate.request.method).toBe('POST');
    activate.flush({});
  });

  it('assigns a vehicle to a partner on the vehicle history endpoint', () => {
    service.assignVehicle('v-1', { partnerId: 'p-2', reason: 'Transfert contractuel' }).subscribe();
    const request = http.expectOne('/api/admin/vehicles/v-1/partner-assignments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ partnerId: 'p-2', reason: 'Transfert contractuel' });
    request.flush({});
  });

  it('reads the assignment history of a vehicle', () => {
    service.getVehicleAssignments('v-1').subscribe();
    const request = http.expectOne('/api/admin/vehicles/v-1/partner-assignments');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a guarantee and withdraws it without touching its history', () => {
    const body = { vehicleId: 'v-1', startsAt: '2030-01-01T00:00:00Z', endsAt: '2030-02-01T00:00:00Z', reason: 'Garantie' };
    service.createGuarantee(body).subscribe();
    const create = http.expectOne('/api/admin/guarantees');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(body);
    create.flush({});

    service.withdrawGuarantee('g-1').subscribe();
    const withdraw = http.expectOne('/api/admin/guarantees/g-1/withdraw');
    expect(withdraw.request.method).toBe('POST');
    withdraw.flush({});
  });

  it('lists guarantees and reads the guarantees of one vehicle', () => {
    service.listGuarantees(0, 25).subscribe();
    const list = http.expectOne((candidate) => candidate.url === '/api/admin/guarantees');
    expect(list.request.params.get('size')).toBe('25');
    list.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true });

    service.getVehicleGuarantees('v-9').subscribe();
    http.expectOne('/api/admin/guarantees/by-vehicle/v-9').flush([]);
  });

  it('calls only the read-only partner portal endpoints', () => {
    service.getMyPartner().subscribe();
    http.expectOne('/api/partner/me').flush({});

    service.getMyVehicles().subscribe();
    http.expectOne('/api/partner/vehicles').flush([]);

    service.getMyVehicle('v-3').subscribe();
    http.expectOne('/api/partner/vehicles/v-3').flush({});

    service.getMyGuarantees().subscribe();
    http.expectOne('/api/partner/guarantees').flush([]);
  });
});

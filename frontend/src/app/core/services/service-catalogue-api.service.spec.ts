import { FormControl } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { maxThreeDecimals } from '../../features/admin/admin-services.component';
import { ServiceCatalogueApiService } from './service-catalogue-api.service';

describe('ServiceCatalogueApiService', () => {
  let service: ServiceCatalogueApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ServiceCatalogueApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists with search, status filter and pagination', () => {
    service.list('siège', true, 2).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/services');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('siège');
    expect(request.request.params.get('active')).toBe('true');
    expect(request.request.params.get('page')).toBe('2');
    request.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('omits the active filter when all services are requested', () => {
    service.list('', null).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/services');
    expect(request.request.params.has('active')).toBe(false);
    request.flush({ content: [] });
  });

  it('sends the currency only when creating and never when updating', () => {
    service.create({ name: 'Wifi', description: '', price: 5, currency: 'TND' }).subscribe();
    const create = http.expectOne('/api/admin/services');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.currency).toBe('TND');
    create.flush({});

    service.update('abc', { name: 'Wifi', description: '', price: 6 }).subscribe();
    const update = http.expectOne('/api/admin/services/abc');
    expect(update.request.method).toBe('PUT');
    expect('currency' in update.request.body).toBe(false);
    expect('active' in update.request.body).toBe(false);
    update.flush({});
  });

  it('uses dedicated endpoints to activate and deactivate (no delete)', () => {
    service.deactivate('abc').subscribe();
    expect(http.expectOne('/api/admin/services/abc/deactivate').request.method).toBe('POST');
    service.activate('abc').subscribe();
    expect(http.expectOne('/api/admin/services/abc/activate').request.method).toBe('POST');
  });
});

describe('maxThreeDecimals', () => {
  it('accepts up to three decimals and rejects more', () => {
    expect(maxThreeDecimals(new FormControl(12.5))).toBeNull();
    expect(maxThreeDecimals(new FormControl(0.001))).toBeNull();
    expect(maxThreeDecimals(new FormControl(95.25))).toBeNull();
    expect(maxThreeDecimals(new FormControl(1.2345))).toEqual({ decimals: true });
    expect(maxThreeDecimals(new FormControl('abc'))).toEqual({ decimals: true });
  });
});

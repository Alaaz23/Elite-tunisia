import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CustomerAdminApiService } from './customer-admin-api.service';

describe('CustomerAdminApiService', () => {
  let service: CustomerAdminApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CustomerAdminApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the directory search and pagination parameters to the admin API', () => {
    service.list('a client', 2).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/admin/customers');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('search')).toBe('a client');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    request.flush({ content: [], totalElements: 0, totalPages: 0, number: 2 });
  });
});

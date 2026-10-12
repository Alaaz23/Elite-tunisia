import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { VehicleCatalogueComponent } from './vehicle-catalogue.component';

describe('VehicleCatalogueComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehicleCatalogueComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => { http.verify(); vi.restoreAllMocks(); });

  it('shows the empty state when the API has no published vehicles', () => {
    const fixture = TestBed.createComponent(VehicleCatalogueComponent);
    fixture.detectChanges();
    http.expectOne('/api/brands?page=0&size=100').flush(page([]));
    http.expectOne('/api/categories?page=0&size=100').flush(page([]));
    http.expectOne('/api/bookings/policy').flush({ businessTimeZone: 'Africa/Tunis' });
    http.expectOne((request) => request.url === '/api/vehicles').flush(page([]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.empty-state')?.textContent).toContain('Aucun véhicule');
  });

  it('shows a retryable error when the catalogue request fails', () => {
    const fixture = TestBed.createComponent(VehicleCatalogueComponent);
    fixture.detectChanges();
    http.expectOne('/api/brands?page=0&size=100').flush(page([]));
    http.expectOne('/api/categories?page=0&size=100').flush(page([]));
    http.expectOne('/api/bookings/policy').flush({ businessTimeZone: 'Africa/Tunis' });
    http.expectOne((request) => request.url === '/api/vehicles').flush({ detail: 'Service indisponible' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('Service indisponible');
  });

  it('sends selected filters and paginates the server response', async () => {
    const fixture = TestBed.createComponent(VehicleCatalogueComponent);
    fixture.detectChanges();
    http.expectOne('/api/brands?page=0&size=100').flush(page([{ id: 'brand-1', name: 'Aster', slug: 'aster', description: '', logoUrl: null, active: true }]));
    http.expectOne('/api/categories?page=0&size=100').flush(page([]));
    http.expectOne('/api/bookings/policy').flush({ businessTimeZone: 'Africa/Tunis' });
    http.expectOne((request) => request.url === '/api/vehicles').flush(page([vehicle()], 0, 2, 10));
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const brand = root.querySelector<HTMLSelectElement>('select[formControlName="brand"]')!;
    brand.value = 'aster';
    brand.dispatchEvent(new Event('change'));
    const minimum = root.querySelector<HTMLInputElement>('input[formControlName="minPrice"]')!;
    minimum.value = '60';
    minimum.dispatchEvent(new Event('input'));
    const start = root.querySelector<HTMLInputElement>('input[formControlName="start"]')!;
    start.value = '2090-01-01T10:00'; start.dispatchEvent(new Event('input'));
    const end = root.querySelector<HTMLInputElement>('input[formControlName="end"]')!;
    end.value = '2090-01-02T10:00'; end.dispatchEvent(new Event('input'));
    root.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await Promise.resolve();
    fixture.detectChanges();

    const filtered = http.expectOne((request) => request.url === '/api/vehicles/available' && request.params.get('brand') === 'aster');
    expect(filtered.request.params.get('minPrice')).toBe('60');
    expect(filtered.request.params.get('startDateTime')).toBe('2090-01-01T09:00:00Z');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(page([vehicle()], 0, 2, 10));
    fixture.detectChanges();
    const buttons = [...root.querySelectorAll<HTMLButtonElement>('.page-button')];
    buttons[1].click();
    await Promise.resolve();
    fixture.detectChanges();
    const nextPage = http.expectOne((request) => request.url === '/api/vehicles/available' && request.params.get('brand') === 'aster');
    expect(nextPage.request.params.get('page')).toBe('1');
    nextPage.flush(page([vehicle()], 1, 2, 10));
    fixture.detectChanges();
    expect(root.querySelector('.pagination')?.textContent).toContain('Page 2 / 2');
  });
});

function page<T>(content: T[], currentPage = 0, totalPages = 0, totalElements = content.length) {
  return { content, page: currentPage, size: 9, totalElements, totalPages, first: currentPage === 0, last: currentPage + 1 >= totalPages };
}

function vehicle() {
  return {
    id: 'vehicle-1', brand: { id: 'brand-1', name: 'Aster', slug: 'aster', description: '', logoUrl: null, active: true },
    model: 'Touring', year: 2025, category: { id: 'category-1', name: 'Berline', slug: 'berline', description: '', active: true },
    description: 'Un véhicule de test.', pricePerDay: 80, pricePerWeek: 500, priceWithDriver: null, currency: 'TND',
    passengers: 5, doors: 4, transmission: 'AUTOMATIC' as const, fuel: 'HYBRID' as const, withDriverAvailable: false,
    status: 'AVAILABLE' as const, images: [], features: [],
  };
}
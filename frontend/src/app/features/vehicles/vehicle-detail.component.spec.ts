import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { VehicleDetailComponent } from './vehicle-detail.component';

describe('VehicleDetailComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehicleDetailComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'vehicle-1' })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });


  it('shows the published rates and does not present the catalogue status as a date guarantee', () => {
    const fixture = TestBed.createComponent(VehicleDetailComponent);
    fixture.detectChanges();
    http.expectOne('/api/vehicles/vehicle-1').flush(vehicle());
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Aster');
    expect(text).toContain('Touring');
    expect(text).toContain('Ouvert à la réservation');
    expect(text).not.toContain('Disponible');
    expect(text).toContain('À la journée');
    expect(text).toContain('À la semaine');
  });

  it('shows the backend detail when the vehicle cannot be loaded', () => {
    const fixture = TestBed.createComponent(VehicleDetailComponent);
    fixture.detectChanges();
    http.expectOne('/api/vehicles/vehicle-1').flush({ detail: 'Véhicule introuvable.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Véhicule introuvable.');
  });
});

function vehicle() {
  return {
    id: 'vehicle-1', brand: { id: 'brand-1', name: 'Aster', slug: 'aster', description: '', logoUrl: null, active: true },
    model: 'Touring', year: 2025, category: { id: 'category-1', name: 'Berline', slug: 'berline', description: '', active: true },
    description: 'Un véhicule de test.', pricePerDay: 80, pricePerWeek: 500, priceWithDriver: null, currency: 'TND',
    passengers: 5, doors: 4, transmission: 'AUTOMATIC' as const, fuel: 'HYBRID' as const, withDriverAvailable: false,
    status: 'AVAILABLE' as const, images: [], features: [],
  };
}

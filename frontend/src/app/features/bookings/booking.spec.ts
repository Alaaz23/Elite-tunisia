import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { FormGroup } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { toBookingInstant } from '../../core/services/booking-api.service';
import { BookingFormComponent } from './booking-form.component';
import { BookingDetailComponent } from './booking-detail.component';
import { BookingsListComponent } from './bookings-list.component';

const preview = { vehicleId: 'vehicle-1', vehicleName: 'Touring', startDateTime: '2090-01-01T09:00:00Z', endDateTime: '2090-01-02T09:00:00Z', numberOfDays: 1, basePrice: 125.123, additionalCharges: 0, discount: 0, tax: 0, totalAmount: 125.123, currency: 'TND', businessTimeZone: 'Africa/Tunis' };
const booking = { ...preview, id: 'booking-1', reference: 'ET-2090-000001', customerId: 'customer-1', customerName: 'Client Test', status: 'PENDING', pickupLocation: 'Tunis', dropoffLocation: 'Tunis', customerNote: null, createdAt: '2090-01-01T00:00:00Z', updatedAt: '2090-01-01T00:00:00Z', confirmedAt: null, cancelledAt: null, history: [] };
const auth = { hasAnyRole: (roles: string[]) => roles.includes('CUSTOMER'), accessToken: () => 'test-token' };

describe('Booking workflow', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BookingFormComponent, BookingDetailComponent, BookingsListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); vi.restoreAllMocks(); });
  function formFixture() {
    const fixture = TestBed.createComponent(BookingFormComponent);
    fixture.componentRef.setInput('vehicle', { id: 'vehicle-1' }); fixture.detectChanges();
    http.expectOne('/api/bookings/policy').flush({ businessTimeZone: 'Africa/Tunis', pendingBlocks: false, priceRule: 'STARTED_24_HOURS', options: [] });
    const component = fixture.componentInstance as unknown as { form: FormGroup; check(): void; submit(): void };
    return { fixture, component };
  }
  function fill(component: { form: FormGroup }) {
    component.form.patchValue({ start: '2090-01-01T10:00', end: '2090-01-02T10:00', pickupLocation: 'Tunis', dropoffLocation: 'Tunis' });
  }
  it('converts Tunis local time to UTC and rejects ambiguous DST hours', () => {
    expect(toBookingInstant('2090-01-01T10:00', 'Africa/Tunis')).toBe('2090-01-01T09:00:00Z');
    expect(() => toBookingInstant('2026-10-25T02:30', 'Europe/Paris')).toThrow();
  });
  it('does not query availability for missing or reversed dates', () => {
    const { fixture, component } = formFixture(); component.check(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    fill(component); component.form.patchValue({ end: '2089-12-31T10:00' }); component.check();
    http.expectNone((request) => request.url.includes('/availability'));
  });
  it('shows unavailable without offering a submission', () => {
    const { fixture, component } = formFixture(); fill(component); component.check();
    http.expectOne((request) => request.url.endsWith('/availability')).flush({ available: false, reason: 'VEHICLE_ALREADY_BOOKED' });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Indisponible');
    expect(fixture.nativeElement.textContent).not.toContain('Envoyer ma demande');
  });
  it('uses the server preview and clears it when dates change', () => {
    const { fixture, component } = formFixture(); fill(component); component.check();
    const availability = http.expectOne((request) => request.url.endsWith('/availability'));
    expect(availability.request.params.get('startDateTime')).toBe(preview.startDateTime);
    availability.flush({ available: true }); http.expectOne('/api/bookings/quote-preview').flush(preview);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('125,123');
    component.form.patchValue({ end: '2090-01-03T10:00' }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Envoyer ma demande');
  });
  it('submits only client input and opens its returned booking', () => {
    const { fixture, component } = formFixture(); fill(component); component.check();
    http.expectOne((request) => request.url.endsWith('/availability')).flush({ available: true });
    http.expectOne('/api/bookings/quote-preview').flush(preview);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.submit(); const creation = http.expectOne('/api/bookings');
    expect(creation.request.body.totalAmount).toBeUndefined(); expect(creation.request.body.customerId).toBeUndefined();
    creation.flush(booking); fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith(['/customer/bookings', booking.id], { state: { created: true } });
  });
  it('shows a creation error and removes the stale preview', () => {
    const { fixture, component } = formFixture(); fill(component); component.check();
    http.expectOne((request) => request.url.endsWith('/availability')).flush({ available: true });
    http.expectOne('/api/bookings/quote-preview').flush(preview); component.submit();
    http.expectOne('/api/bookings').flush({ detail: 'Conflit' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('Envoyer ma demande');
  });
  it('lists reservations received from the customer endpoint', () => {
    const fixture = TestBed.createComponent(BookingsListComponent); fixture.detectChanges();
    http.expectOne('/api/bookings/policy').flush({ businessTimeZone: 'Africa/Tunis' });
    http.expectOne((request) => request.url === '/api/bookings').flush({ content: [booking], totalElements: 1, totalPages: 1, page: 0, size: 25 });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain(booking.reference);
  });
});

describe('Booking detail and protection', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BookingDetailComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: AuthService, useValue: auth }, { provide: ActivatedRoute, useValue: { snapshot: { data: {}, paramMap: new Map([['id', 'booking-1']]) } } },
    ] }).compileComponents(); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); vi.restoreAllMocks(); });
  it('loads the owned detail and cancels through the server', () => {
    const fixture = TestBed.createComponent(BookingDetailComponent); fixture.detectChanges();
    http.expectOne('/api/bookings/booking-1').flush(booking); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(booking.reference);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    (fixture.componentInstance as unknown as { cancel(): void }).cancel();
    http.expectOne('/api/bookings/booking-1/cancel').flush({ ...booking, status: 'CANCELLED' });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Annulée');
  });
  it('does not render a booking when its server endpoint refuses access', () => {
    const fixture = TestBed.createComponent(BookingDetailComponent); fixture.detectChanges();
    http.expectOne('/api/bookings/booking-1').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain(booking.reference);
  });
});
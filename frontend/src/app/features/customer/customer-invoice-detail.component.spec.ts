import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { ApiHttpError } from '../../core/models/api-http-error';
import { InvoiceDetail } from '../../core/services/invoice-api.service';
import { CustomerInvoiceDetailComponent, loadErrorMessage } from './customer-invoice-detail.component';

const invoice: InvoiceDetail = {
  id: 'invoice-1',
  reference: 'FAC-2090-000001',
  totalAmount: 490,
  currency: 'TND',
  issuedAt: '2090-01-02T09:00:00Z',
  lines: [{ id: 'line-1', type: 'VEHICLE_RENTAL', description: 'Location véhicule', quantity: 1, unitPrice: 500, amount: 500, position: 0, metadata: {} }],
};

describe('CustomerInvoiceDetailComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerInvoiceDetailComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { refreshSession: vi.fn(), clearSession: vi.fn() } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'invoice-1' })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function create() {
    const fixture = TestBed.createComponent(CustomerInvoiceDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('loads the invoice detail through the customer endpoint and shows its lines', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('Chargement de la facture');
    http.expectOne('/api/invoices/invoice-1').flush(invoice);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('FAC-2090-000001');
    expect(text).toContain('Location véhicule');
    expect(text).toContain('490');
    expect(text).not.toContain('Chargement de la facture');
  });

  it('shows a not-found or not-accessible message for a foreign or missing invoice', () => {
    const fixture = create();
    http.expectOne('/api/invoices/invoice-1').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('introuvable ou ne vous est pas accessible');
    expect(fixture.nativeElement.textContent).not.toContain('FAC-2090-000001');
  });

  it('shows an access message when the backend refuses the invoice', () => {
    const fixture = create();
    http.expectOne('/api/invoices/invoice-1').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Vous n’avez pas accès à cette facture');
  });

  it('downloads the PDF from the customer endpoint and triggers a file save', () => {
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    const createObjectURL = vi.fn(() => 'blob:invoice');
    const revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fixture = create();
    http.expectOne('/api/invoices/invoice-1').flush(invoice);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    const request = http.expectOne('/api/invoices/invoice-1/pdf');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }), { headers: { 'content-disposition': 'inline; filename="FAC-2090-000001.pdf"' } });
    fixture.detectChanges();
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:invoice');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  });

  it('shows a download error without losing the invoice', () => {
    const fixture = create();
    http.expectOne('/api/invoices/invoice-1').flush(invoice);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    http.expectOne('/api/invoices/invoice-1/pdf').flush(new Blob(['{}'], { type: 'application/json' }), { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Le PDF n’a pas pu être téléchargé');
    expect(fixture.nativeElement.textContent).toContain('FAC-2090-000001');
  });
});

describe('loadErrorMessage', () => {
  it('maps access and session errors to explicit French messages', () => {
    expect(loadErrorMessage(new ApiHttpError(404, 'x'))).toContain('introuvable');
    expect(loadErrorMessage(new ApiHttpError(403, 'x'))).toContain('Vous n’avez pas accès');
    expect(loadErrorMessage(new ApiHttpError(401, 'x'))).toContain('session a expiré');
  });

  it('falls back to a generic message without exposing the raw error', () => {
    expect(loadErrorMessage(new ApiHttpError(500, 'SQL: secret'))).toBe('Impossible de charger la facture. Réessayez dans quelques instants.');
    expect(loadErrorMessage(new Error('boom'))).toBe('Impossible de charger la facture. Réessayez dans quelques instants.');
  });
});

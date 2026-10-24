import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InvoiceApiService } from './invoice-api.service';

describe('InvoiceApiService', () => {
  let service: InvoiceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InvoiceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the customer invoice endpoints', () => {
    service.listForCustomer().subscribe();
    http.expectOne('/api/invoices').flush([]);

    service.get('invoice-1').subscribe();
    http.expectOne('/api/invoices/invoice-1').flush({});

    service.downloadPdf('invoice-1').subscribe();
    const request = http.expectOne('/api/invoices/invoice-1/pdf');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['pdf']));
  });

  it('uses the admin invoice endpoints for admin summaries, details and PDFs', () => {
    service.listForAdmin().subscribe();
    http.expectOne('/api/admin/invoices').flush([]);

    service.get('invoice-1', true).subscribe();
    http.expectOne('/api/admin/invoices/invoice-1').flush({});

    service.downloadPdf('invoice-1', true).subscribe();
    const request = http.expectOne('/api/admin/invoices/invoice-1/pdf');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['pdf']));
  });
});

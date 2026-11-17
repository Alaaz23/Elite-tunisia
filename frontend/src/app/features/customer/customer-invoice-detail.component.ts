import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiHttpError } from '../../core/models/api-http-error';
import { InvoiceApiService, InvoiceDetail } from '../../core/services/invoice-api.service';
import { extractFilenameFromContentDisposition } from '../../shared/utils/http';

@Component({
  selector: 'app-customer-invoice-detail',
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    <section class="invoice-detail">
      <a routerLink="/customer/invoices">Retour aux factures</a>
      @if (loading()) {
        <p role="status">Chargement de la facture…</p>
      }
      @if (error()) {
        <p class="alert" role="alert">{{ error() }}</p>
      }
      @if (invoice(); as invoice) {
        <header>
          <h1>Facture {{ invoice.reference }}</h1>
          <p>Émise le {{ invoice.issuedAt | date: 'dd/MM/yyyy' }}</p>
          <p class="total">Total : {{ invoice.totalAmount | number: '1.3-3' }} {{ invoice.currency }}</p>
          <button type="button" (click)="download()" [disabled]="downloading()">
            {{ downloading() ? 'Préparation du PDF…' : 'Télécharger le PDF' }}
          </button>
          @if (downloadError()) {
            <p class="alert" role="alert">{{ downloadError() }}</p>
          }
        </header>
        @if (invoice.lines.length === 0) {
          <p>Aucune ligne de facture.</p>
        } @else {
          <table>
            <thead>
              <tr><th>Description</th><th>Quantité</th><th>Prix unitaire</th><th>Montant</th></tr>
            </thead>
            <tbody>
              @for (line of invoice.lines; track line.id) {
                <tr>
                  <td>{{ line.description }}</td>
                  <td>{{ line.quantity }}</td>
                  <td>{{ line.unitPrice | number: '1.3-3' }}</td>
                  <td>{{ line.amount | number: '1.3-3' }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      }
    </section>
  `,
})
export class CustomerInvoiceDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(InvoiceApiService);
  protected readonly invoice = signal<InvoiceDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly downloading = signal(false);
  protected readonly downloadError = signal('');
  private invoiceId = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.invoiceId = params.get('id') ?? '';
      this.load();
    });
  }

  protected download(): void {
    this.downloading.set(true);
    this.downloadError.set('');
    this.api.downloadPdf(this.invoiceId).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.downloadError.set(DOWNLOAD_ERROR);
          this.downloading.set(false);
          return;
        }
        const filename = extractFilenameFromContentDisposition(response.headers.get('content-disposition') ?? '') || 'facture.pdf';
        saveBlob(blob, filename);
        this.downloading.set(false);
      },
      error: () => {
        this.downloadError.set(DOWNLOAD_ERROR);
        this.downloading.set(false);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set('');
    this.invoice.set(null);
    this.api.get(this.invoiceId).subscribe({
      next: (invoice) => {
        this.invoice.set(invoice);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(loadErrorMessage(error));
        this.loading.set(false);
      },
    });
  }
}

const DOWNLOAD_ERROR = 'Le PDF n’a pas pu être téléchargé. Réessayez dans quelques instants.';

export function loadErrorMessage(error: unknown): string {
  const status = error instanceof ApiHttpError ? error.status : 0;
  if (status === 404) return 'Cette facture est introuvable ou ne vous est pas accessible.';
  if (status === 403) return 'Vous n’avez pas accès à cette facture.';
  if (status === 401) return 'Votre session a expiré. Veuillez vous reconnecter.';
  return 'Impossible de charger la facture. Réessayez dans quelques instants.';
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

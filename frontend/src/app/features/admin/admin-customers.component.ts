import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiHttpError } from '../../core/models/api-http-error';
import { AdminCustomer, CustomerAdminApiService } from '../../core/services/customer-admin-api.service';

@Component({
  selector: 'app-admin-customers',
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <section class="customers-page" aria-labelledby="customers-title">
      <header><p class="eyebrow">ADMINISTRATION <span>/</span> CLIENTS</p><h1 id="customers-title">Clients <em>de l’agence.</em></h1><p>Annuaire en lecture seule des comptes clients inscrits.</p></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      <form class="search" [formGroup]="form" (ngSubmit)="search()">
        <label for="customer-search">Rechercher par nom, e-mail ou téléphone</label>
        <div><input id="customer-search" formControlName="search" maxlength="254"><button class="button button-primary" [disabled]="loading()">Rechercher</button></div>
      </form>
      @if (loading()) { <p role="status">Chargement des clients…</p> }
      @if (!loading() && customers().length === 0) { <p class="empty-state">Aucun client ne correspond à la recherche.</p> }
      @if (customers().length > 0) {
        <p class="count">{{ total() }} client(s)</p>
        <div class="table-wrap"><table><thead><tr><th>Client</th><th>Téléphone</th><th>Niveau</th><th>Statut</th><th>Inscription</th></tr></thead><tbody>
          @for (customer of customers(); track customer.id) {
            <tr><td><strong>{{ customer.firstName }} {{ customer.lastName }}</strong><small>{{ customer.email }}</small></td>
              <td>{{ customer.phone }}</td><td>{{ customer.customerLevel }}</td>
              <td><span class="status" [class.inactive]="!customer.enabled"><i></i>{{ customer.enabled ? 'Actif' : 'Désactivé' }}</span></td>
              <td>{{ customer.createdAt | date:'mediumDate' }}</td></tr>
          }
        </tbody></table></div>
        <nav class="pagination" aria-label="Pagination des clients">
          <button type="button" class="button button-secondary" [disabled]="loading() || page() === 0" (click)="load(page() - 1)">Précédent</button>
          <span>Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button type="button" class="button button-secondary" [disabled]="loading() || page() + 1 >= totalPages()" (click)="load(page() + 1)">Suivant</button>
        </nav>
      }
    </section>
  `,
  styles: `
    .customers-page { max-width:1200px; margin:auto; padding:clamp(1.5rem,4vw,3.5rem); }
    header { margin-bottom:2rem; } .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); } h1 { margin:.8rem 0 .5rem; font:500 clamp(2.3rem,5vw,4rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); } header>p:last-child,.count { color:var(--color-muted); font-size:.82rem; }
    .search { display:grid; gap:.5rem; max-width:680px; margin-bottom:1.5rem; } .search label { font-size:.76rem; font-weight:600; }
    .search>div { display:flex; gap:.6rem; } .search input { flex:1; min-width:0; padding:.7rem; border:1px solid var(--color-line); }
    .table-wrap { overflow:auto; } table { width:100%; border-collapse:collapse; text-align:left; } th,td { padding:.85rem .65rem; border-bottom:1px solid var(--color-line); font-size:.78rem; }
    th { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } td strong { font-weight:600; } td small { display:block; margin-top:.2rem; color:var(--color-muted); }
    .status { display:inline-flex; align-items:center; gap:.4rem; color:var(--color-olive); } .status i { width:.45rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status.inactive { color:var(--color-muted); } .status.inactive i { background:#999; }
    .pagination { display:flex; align-items:center; justify-content:flex-end; gap:1rem; margin-top:1rem; font-size:.75rem; }
    @media(max-width:600px) { .customers-page { padding:1.25rem; } .search>div { align-items:stretch; flex-direction:column; } table { min-width:680px; } }
  `,
})
export class AdminCustomersComponent implements OnInit {
  private readonly api = inject(CustomerAdminApiService);
  private readonly builder = inject(FormBuilder).nonNullable;
  protected readonly customers = signal<AdminCustomer[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly page = signal(0);
  protected readonly form = this.builder.group({ search: [''] });

  ngOnInit(): void { this.load(0); }

  protected search(): void { this.load(0); }

  protected load(page: number): void {
    if (this.loading() && page !== 0) return;
    this.loading.set(true);
    this.error.set('');
    this.api.list(this.form.controls.search.value.trim(), page).subscribe({
      next: (result) => {
        this.customers.set(result.content);
        this.total.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.page.set(result.number);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? 'Impossible de charger les clients.' : 'Impossible de charger les clients.');
        this.loading.set(false);
      },
    });
  }
}

import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { GuaranteeSummary, PartnerApiService } from '../../core/services/partner-api.service';
import { apiErrorMessage, guaranteeFormErrors, toGuaranteeRequest } from '../partners/partner-validation';

@Component({
  selector: 'app-admin-guarantees',
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <section class="guarantees-page" aria-labelledby="guarantees-title">
      <header><p class="eyebrow">ADMINISTRATION <span>/</span> GARANTIES</p><h1 id="guarantees-title">Garanties <em>partenaires.</em></h1><p>Une garantie engage un partenaire sur un véhicule pour une période donnée. Les garanties retirées restent consultables.</p></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }

      <form class="filter-form" [formGroup]="filterForm" (ngSubmit)="applyFilter()">
        <label class="field"><span>Filtrer par identifiant de véhicule</span><input formControlName="vehicleId" maxlength="36"></label>
        <button type="submit" class="button button-secondary" [disabled]="loading()">Filtrer</button>
        @if (filterVehicleId()) { <button type="button" class="button button-secondary" (click)="clearFilter()">Tout afficher</button> }
      </form>

      @if (loading()) { <p role="status">Chargement des garanties…</p> }
      @if (!loading() && guarantees().length === 0) { <p class="empty-state">Aucune garantie à afficher.</p> }
      @if (guarantees().length > 0) {
        <div class="table-wrap"><table><thead><tr><th>Véhicule</th><th>Partenaire</th><th>Période</th><th>Statut</th><th>Motif</th>@if (canManage()) {<th><span class="sr-only">Actions</span></th>}</tr></thead><tbody>
          @for (guarantee of guarantees(); track guarantee.id) {
            <tr>
              <td><code>{{ guarantee.vehicleId }}</code></td>
              <td>{{ guarantee.partnerName }}</td>
              <td>{{ guarantee.startsAt | date:'medium' }}<small>au {{ guarantee.endsAt | date:'medium' }}</small></td>
              <td><span class="status" [class.inactive]="guarantee.status !== 'ACTIVE'"><i></i>{{ guarantee.status === 'ACTIVE' ? 'Active' : 'Retirée' }}</span>@if (guarantee.withdrawnAt) {<small>Retirée le {{ guarantee.withdrawnAt | date:'medium' }}</small>}</td>
              <td>{{ guarantee.reason }}</td>
              @if (canManage()) {
                <td>@if (guarantee.status === 'ACTIVE') { <button type="button" class="button button-secondary" (click)="withdraw(guarantee)" [disabled]="busy()">Retirer</button> }</td>
              }
            </tr>
          }
        </tbody></table></div>
        <nav class="pagination" aria-label="Pagination des garanties">
          <button type="button" class="button button-secondary" [disabled]="loading() || page() === 0" (click)="load(page() - 1)">Précédent</button>
          <span>Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button type="button" class="button button-secondary" [disabled]="loading() || page() + 1 >= totalPages()" (click)="load(page() + 1)">Suivant</button>
        </nav>
      }

      @if (canManage()) {
        <section class="panel" aria-labelledby="guarantee-form-title">
          <h2 id="guarantee-form-title">Nouvelle garantie</h2>
          <p class="hint">Le partenaire engagé est celui affecté au véhicule à la date de création. Aucune garantie n’est créée automatiquement.</p>
          @if (formErrors().length > 0) { <ul class="alert alert-error" role="alert">@for (message of formErrors(); track message) {<li>{{ message }}</li>}</ul> }
          <form class="grid-form" [formGroup]="form" (ngSubmit)="create()">
            <label class="field wide"><span>Identifiant du véhicule *</span><input formControlName="vehicleId" maxlength="36" required></label>
            <label class="field"><span>Début *</span><input formControlName="startsAt" type="datetime-local" required></label>
            <label class="field"><span>Fin *</span><input formControlName="endsAt" type="datetime-local" required></label>
            <label class="field wide"><span>Motif *</span><input formControlName="reason" maxlength="500" required></label>
            <div class="form-actions"><button type="submit" class="button button-primary" [disabled]="busy()">Créer la garantie</button></div>
          </form>
        </section>
      }
    </section>
  `,
  styles: `
    .guarantees-page { max-width:1200px; margin:auto; padding:clamp(1.5rem,4vw,3.5rem); }
    header { margin-bottom:2rem; } .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); } h1 { margin:.8rem 0 .5rem; font:500 clamp(2.3rem,5vw,4rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); } header>p:last-child, .hint { color:var(--color-muted); font-size:.82rem; }
    .filter-form { display:flex; align-items:end; gap:.75rem; flex-wrap:wrap; margin-bottom:1.5rem; }
    .table-wrap { overflow:auto; } table { width:100%; border-collapse:collapse; text-align:left; } th,td { padding:.85rem .65rem; border-bottom:1px solid var(--color-line); font-size:.78rem; vertical-align:top; }
    th { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } td small { display:block; margin-top:.2rem; color:var(--color-muted); }
    td code { font-size:.72rem; }
    .status { display:inline-flex; align-items:center; gap:.4rem; color:var(--color-olive); } .status i { width:.45rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status.inactive { color:var(--color-muted); } .status.inactive i { background:#999; }
    .pagination { display:flex; align-items:center; justify-content:flex-end; gap:1rem; margin-top:1rem; font-size:.75rem; }
    .panel { margin-top:2.5rem; padding:1.5rem; border:1px solid var(--color-line); } .panel h2 { margin:0 0 .75rem; font:500 1.4rem/1.2 var(--font-display); }
    .grid-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:.9rem; margin-top:1rem; }
    .field { display:grid; gap:.35rem; font-size:.76rem; font-weight:600; } .field.wide { grid-column:1 / -1; }
    .field input { padding:.65rem; border:1px solid var(--color-line); font:inherit; font-weight:400; }
    .form-actions { grid-column:1 / -1; display:flex; gap:.75rem; }
    .sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0 0 0 0); }
    @media(max-width:700px) { .guarantees-page { padding:1.25rem; } .grid-form { grid-template-columns:1fr; } table { min-width:640px; } }
  `,
})
export class AdminGuaranteesComponent implements OnInit {
  private readonly api = inject(PartnerApiService);
  private readonly auth = inject(AuthService);
  private readonly builder = inject(FormBuilder).nonNullable;

  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER']));
  protected readonly guarantees = signal<GuaranteeSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly formErrors = signal<string[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly filterVehicleId = signal<string | null>(null);

  protected readonly filterForm = this.builder.group({ vehicleId: [''] });
  protected readonly form = this.builder.group({ vehicleId: [''], startsAt: [''], endsAt: [''], reason: [''] });

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    const vehicleId = this.filterVehicleId();
    this.loading.set(true);
    this.error.set('');
    if (vehicleId) {
      this.api.getVehicleGuarantees(vehicleId).subscribe({
        next: (items) => this.showItems(items, 0, 1),
        error: (error: unknown) => this.showError(error),
      });
      return;
    }
    this.api.listGuarantees(page).subscribe({
      next: (result) => this.showItems(result.content, result.page, result.totalPages),
      error: (error: unknown) => this.showError(error),
    });
  }

  protected applyFilter(): void {
    const vehicleId = this.filterForm.controls.vehicleId.value.trim();
    this.filterVehicleId.set(vehicleId || null);
    this.load(0);
  }

  protected clearFilter(): void {
    this.filterForm.reset({ vehicleId: '' });
    this.filterVehicleId.set(null);
    this.load(0);
  }

  protected create(): void {
    const value = this.form.getRawValue();
    const errors = guaranteeFormErrors(value.vehicleId, value.startsAt, value.endsAt, value.reason);
    this.formErrors.set(errors);
    if (errors.length > 0 || this.busy()) return;
    this.busy.set(true);
    this.success.set('');
    this.api.createGuarantee(toGuaranteeRequest(value.vehicleId, value.startsAt, value.endsAt, value.reason)).subscribe({
      next: () => {
        this.busy.set(false);
        this.form.reset({ vehicleId: '', startsAt: '', endsAt: '', reason: '' });
        this.success.set('Garantie créée.');
        this.load(this.page());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.formErrors.set([apiErrorMessage(error, 'La création de la garantie a échoué.')]);
      },
    });
  }

  protected withdraw(guarantee: GuaranteeSummary): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.success.set('');
    this.api.withdrawGuarantee(guarantee.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set('Garantie retirée. L’historique est conservé.');
        this.load(this.page());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(error, 'Le retrait de la garantie a échoué.'));
      },
    });
  }

  private showItems(items: GuaranteeSummary[], page: number, totalPages: number): void {
    this.guarantees.set(items);
    this.page.set(page);
    this.totalPages.set(totalPages);
    this.loading.set(false);
  }

  private showError(error: unknown): void {
    this.guarantees.set([]);
    this.error.set(apiErrorMessage(error, 'Impossible de charger les garanties.'));
    this.loading.set(false);
  }
}

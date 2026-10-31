import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ApiHttpError } from '../../core/models/api-http-error';
import { CatalogueService, ServiceCatalogueApiService } from '../../core/services/service-catalogue-api.service';

/** Au plus trois décimales, comme la colonne NUMERIC(12,3) et la validation du serveur. */
export function maxThreeDecimals(control: AbstractControl): ValidationErrors | null {
  const value = Number(control.value);
  return Number.isFinite(value) && Math.abs(value * 1000 - Math.round(value * 1000)) < 1e-6 ? null : { decimals: true };
}

@Component({
  selector: 'app-admin-services',
  imports: [DecimalPipe, ReactiveFormsModule],
  template: `
    <section class="services-page" aria-labelledby="services-title">
      <header><p class="eyebrow">ADMINISTRATION <span>/</span> SERVICES</p><h1 id="services-title">Services <em>additionnels.</em></h1>
        <p>Options proposées dans les devis. Un service n'est jamais supprimé : il est désactivé et reste référencé par les devis existants.</p></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      <div class="layout">
        <section class="list" aria-label="Services enregistrés">
          <form class="filters" [formGroup]="filters" (ngSubmit)="search()">
            <label class="field"><span>Recherche</span><input formControlName="search" maxlength="120" placeholder="Nom du service"></label>
            <label class="field"><span>Statut</span><select formControlName="status"><option value="all">Tous</option><option value="active">Actifs</option><option value="inactive">Inactifs</option></select></label>
            <button class="button button-secondary" [disabled]="loading()">Filtrer</button>
          </form>
          @if (loading()) { <p role="status">Chargement des services…</p> }
          @if (!loading() && services().length === 0) { <p class="empty-state">Aucun service ne correspond.</p> }
          @if (services().length > 0) {
            <div class="table-wrap"><table><thead><tr><th>Service</th><th>Tarif</th><th>Statut</th><th>Actions</th></tr></thead><tbody>
              @for (item of services(); track item.id) {
                <tr [attr.data-service-name]="item.name">
                  <td><strong>{{ item.name }}</strong><small>{{ item.description }}</small></td>
                  <td>{{ item.price | number:'1.3-3' }} {{ item.currency }}</td>
                  <td><span class="status" [class.inactive]="!item.active"><i></i>{{ item.active ? 'Actif' : 'Inactif' }}</span></td>
                  <td class="actions">
                    @if (canManage()) {
                      <button class="text-action" type="button" (click)="edit(item)">Modifier</button>
                      <button class="text-action" type="button" [disabled]="busy()" (click)="toggle(item)">{{ item.active ? 'Désactiver' : 'Réactiver' }}</button>
                    } @else { <span class="read-only">Consultation</span> }
                  </td>
                </tr>
              }
            </tbody></table></div>
            <nav class="pagination" aria-label="Pagination des services">
              <button type="button" class="button button-secondary" [disabled]="loading() || page() === 0" (click)="load(page() - 1)">Précédent</button>
              <span>Page {{ page() + 1 }} / {{ totalPages() }} · {{ total() }} service(s)</span>
              <button type="button" class="button button-secondary" [disabled]="loading() || page() + 1 >= totalPages()" (click)="load(page() + 1)">Suivant</button>
            </nav>
          }
        </section>
        @if (canManage()) {
          <section class="editor" aria-labelledby="editor-title">
            <p class="eyebrow">{{ editingId() ? 'MODIFICATION' : 'NOUVEAU SERVICE' }}</p>
            <h2 id="editor-title">{{ editingId() ? 'Modifier le service' : 'Créer un service' }}</h2>
            <form [formGroup]="form" (ngSubmit)="save()" novalidate>
              <label class="field"><span>Nom du service</span><input formControlName="name" maxlength="120"></label>
              @if (form.controls.name.touched && form.controls.name.invalid) { <small class="invalid">Le nom doit contenir entre 2 et 120 caractères.</small> }
              <label class="field"><span>Description</span><textarea formControlName="description" maxlength="2000" rows="4"></textarea></label>
              <label class="field"><span>Prix unitaire</span><input type="number" formControlName="price" step="0.001" min="0.001"></label>
              @if (form.controls.price.touched && form.controls.price.invalid) { <small class="invalid">Indiquez un prix supérieur à 0, avec au plus trois décimales.</small> }
              <label class="field"><span>Devise</span><input formControlName="currency" maxlength="3" [readonly]="!!editingId()" autocapitalize="characters"></label>
              @if (editingId()) { <small>La devise ne peut pas être modifiée ; créez un autre service pour une autre devise.</small> }
              @if (form.controls.currency.touched && form.controls.currency.invalid) { <small class="invalid">Code devise à trois lettres majuscules (ex. TND).</small> }
              <div class="form-actions">
                <button class="button button-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Enregistrement…' : editingId() ? 'Enregistrer le service' : 'Créer le service' }}</button>
                @if (editingId()) { <button class="button button-secondary" type="button" (click)="cancel()">Annuler</button> }
              </div>
            </form>
          </section>
        } @else { <p class="read-only-note">Votre rôle autorise la consultation du catalogue.</p> }
      </div>
    </section>
  `,
  styles: `
    .services-page { max-width:1300px; margin:auto; padding:clamp(1.5rem,4vw,3.5rem); }
    header { margin-bottom:2rem; } .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); } h1 { margin:.8rem 0 .5rem; font:500 clamp(2.2rem,5vw,3.8rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); } header>p:last-child { color:var(--color-muted); font-size:.82rem; max-width:70ch; }
    .layout { display:grid; grid-template-columns:minmax(0,1.6fr) minmax(280px,.8fr); gap:1.5rem; align-items:start; }
    .filters { display:flex; flex-wrap:wrap; gap:.8rem; align-items:end; margin-bottom:1rem; } .filters .field { min-width:160px; }
    .field { display:grid; gap:.35rem; } .field span { font-size:.72rem; font-weight:600; }
    .field input,.field textarea,.field select { width:100%; padding:.6rem; border:1px solid var(--color-line); background:white; color:var(--color-ink); font-size:.76rem; }
    .field input[readonly] { background:#eee; color:var(--color-muted); }
    .table-wrap { overflow:auto; } table { width:100%; border-collapse:collapse; text-align:left; } th,td { padding:.8rem .6rem; border-bottom:1px solid var(--color-line); font-size:.78rem; vertical-align:top; }
    th { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } td strong { font-weight:600; } td small { display:block; max-width:40ch; margin-top:.2rem; color:var(--color-muted); }
    .status { display:inline-flex; align-items:center; gap:.4rem; color:var(--color-olive); } .status i { width:.45rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status.inactive { color:var(--color-muted); } .status.inactive i { background:#aaa; }
    .actions { display:flex; gap:.8rem; } .text-action { padding:0; border:0; background:none; color:var(--color-olive); cursor:pointer; font-size:.72rem; text-decoration:underline; text-underline-offset:3px; }
    .read-only { color:var(--color-muted); font-size:.7rem; } .read-only-note { padding:1rem; border-left:3px solid var(--color-metal); background:#f5f0e5; color:var(--color-muted); font-size:.78rem; }
    .editor { padding:1.3rem; border:1px solid var(--color-line); background:#f1f1ec; } .editor h2 { margin:.55rem 0 1rem; font:500 1.45rem var(--font-display); }
    .editor form { display:grid; gap:.7rem; } .invalid { color:#a2443d; } .form-actions { display:flex; flex-wrap:wrap; gap:.55rem; margin-top:.35rem; }
    .pagination { display:flex; align-items:center; justify-content:flex-end; gap:1rem; margin-top:1rem; font-size:.75rem; }
    @media(max-width:980px) { .layout { grid-template-columns:1fr; } .editor { max-width:620px; } } @media(max-width:620px) { table { min-width:600px; } }
  `,
})
export class AdminServicesComponent implements OnInit {
  private readonly api = inject(ServiceCatalogueApiService);
  private readonly builder = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER']));
  protected readonly services = signal<CatalogueService[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly filters = this.builder.group({ search: [''], status: ['all'] });
  protected readonly form = this.builder.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    description: ['', Validators.maxLength(2000)],
    price: [0 as number, [Validators.required, Validators.min(0.001), Validators.max(999999999.999), maxThreeDecimals]],
    currency: ['TND', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
  });

  ngOnInit(): void { this.load(0); }

  protected search(): void { this.load(0); }

  protected load(page: number): void {
    this.loading.set(true);
    const status = this.filters.controls.status.value;
    this.api.list(this.filters.controls.search.value.trim(), status === 'all' ? null : status === 'active', page).subscribe({
      next: (result) => {
        this.services.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error: unknown) => { this.fail(error, 'Impossible de charger les services.'); this.loading.set(false); },
    });
  }

  protected edit(item: CatalogueService): void {
    this.editingId.set(item.id);
    this.form.setValue({ name: item.name, description: item.description, price: item.price, currency: item.currency });
    this.error.set(''); this.success.set('');
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', description: '', price: 0, currency: 'TND' });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    const id = this.editingId();
    const base = { name: value.name.trim(), description: value.description.trim(), price: Number(value.price) };
    this.saving.set(true); this.error.set(''); this.success.set('');
    const request = id ? this.api.update(id, base) : this.api.create({ ...base, currency: value.currency });
    request.subscribe({
      next: (saved) => { this.success.set(id ? `Le service « ${saved.name} » a été modifié.` : `Le service « ${saved.name} » a été créé.`); this.saving.set(false); this.cancel(); this.load(this.page()); },
      error: (error: unknown) => { this.fail(error, 'Impossible d’enregistrer le service.'); this.saving.set(false); },
    });
  }

  protected toggle(item: CatalogueService): void {
    if (this.busy()) return;
    this.busy.set(true); this.error.set(''); this.success.set('');
    const request = item.active ? this.api.deactivate(item.id) : this.api.activate(item.id);
    request.subscribe({
      next: (saved) => { this.success.set(saved.active ? `Le service « ${saved.name} » est de nouveau proposé.` : `Le service « ${saved.name} » n'est plus proposé dans les nouveaux devis.`); this.busy.set(false); this.load(this.page()); },
      error: (error: unknown) => { this.fail(error, 'Impossible de changer l’état du service.'); this.busy.set(false); },
    });
  }

  private fail(error: unknown, fallback: string): void {
    this.error.set(error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? fallback : fallback);
  }
}

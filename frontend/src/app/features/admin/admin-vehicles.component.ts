import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiHttpError } from '../../core/models/api-http-error';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogueApiService, BrandSummary, CategorySummary, VehicleFilters, VehicleImage, VehicleInput, VehicleSummary } from '../../core/services/catalogue-api.service';

@Component({
  selector: 'app-admin-vehicles',
  imports: [ReactiveFormsModule],
  template: `
    <section class="admin-page" aria-labelledby="vehicles-admin-title">
      <header class="page-heading"><div><p class="eyebrow">ADMINISTRATION <span>/</span> FLOTTE</p><h1 id="vehicles-admin-title">Parc <em>véhicules.</em></h1></div><span class="count-label">{{ total() }} véhicules</span></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      <div class="toolbar"><label class="search"><span>Recherche</span><input [value]="searchText()" (input)="searchText.set($any($event.target).value)" (keyup.enter)="search()" placeholder="Marque ou modèle"></label><button class="button button-secondary" type="button" (click)="search()">Rechercher</button>@if (canManage()) { <button class="button button-primary" type="button" (click)="newVehicle()">+ Nouveau véhicule</button> }</div>
      @if (loading()) { <p class="loading-state" role="status">Chargement du parc…</p> }
      @if (!loading() && vehicles().length === 0) { <div class="empty-state"><strong>Aucun véhicule enregistré</strong><p>Les véhicules créés apparaîtront dans cette liste.</p></div> }
      @if (vehicles().length > 0) { <div class="table-wrap"><table><thead><tr><th>Véhicule</th><th>Catégorie</th><th>Tarif / jour</th><th>Statut</th><th>Actions</th></tr></thead><tbody>
        @for (vehicle of vehicles(); track vehicle.id) { <tr><td><strong>{{ vehicle.brand.name }} {{ vehicle.model }}</strong><small>{{ vehicle.year }} · {{ vehicle.transmission === 'AUTOMATIC' ? 'Automatique' : vehicle.transmission === 'MANUAL' ? 'Manuelle' : 'Séquentielle' }}</small></td><td>{{ vehicle.category.name }}</td><td>{{ price(vehicle.pricePerDay, vehicle.currency) }}</td><td>
          @if (canManage()) { <select class="status-select" [value]="vehicle.status" (change)="setStatus(vehicle, statusValue($event))"><option value="AVAILABLE">Disponible</option><option value="RESERVED">Réservé</option><option value="RENTED">Loué</option><option value="MAINTENANCE">Maintenance</option><option value="UNAVAILABLE">Masqué</option></select> }
          @else { <span class="status" [class.inactive]="vehicle.status !== 'AVAILABLE'">{{ statusLabel(vehicle.status) }}</span> }
        </td><td class="actions">@if (canManage()) { <button class="text-action" type="button" (click)="edit(vehicle)">Modifier</button><button class="text-action danger" type="button" (click)="remove(vehicle)">Supprimer</button> } @else { <span class="read-only">Consultation</span> }</td></tr> }
      </tbody></table></div>
      <nav class="pagination" aria-label="Pagination du parc"><button class="page-button" type="button" [disabled]="page() === 0 || loading()" (click)="changePage(page() - 1)">← Précédent</button><span>Page {{ page() + 1 }} / {{ pages() }}</span><button class="page-button" type="button" [disabled]="page() + 1 >= pages() || loading()" (click)="changePage(page() + 1)">Suivant →</button></nav> }
      @if (formOpen() && canManage()) {
        <div class="modal-backdrop" (click)="closeForm()" (keydown.escape)="closeForm()"><section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="vehicle-editor-title" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
          <header class="editor-heading"><div><p class="eyebrow">{{ editingId() ? 'MODIFICATION' : 'NOUVELLE FICHE' }}</p><h2 id="vehicle-editor-title">{{ editingId() ? 'Modifier le véhicule' : 'Ajouter un véhicule' }}</h2></div><button class="close-button" type="button" aria-label="Fermer" (click)="closeForm()">×</button></header>
          <form [formGroup]="form" (ngSubmit)="save()" novalidate>
            <div class="form-grid">
              <label class="field"><span>Marque</span><select formControlName="brandId"><option value="">Choisir</option>@for (brand of brands(); track brand.id) { <option [value]="brand.id">{{ brand.name }}</option> }</select></label>
              <label class="field"><span>Catégorie</span><select formControlName="categoryId"><option value="">Choisir</option>@for (category of categories(); track category.id) { <option [value]="category.id">{{ category.name }}</option> }</select></label>
              <label class="field"><span>Modèle</span><input formControlName="model" maxlength="120"></label>
              <label class="field"><span>Année</span><input type="number" formControlName="year" min="1886" max="2100"></label>
              <label class="field"><span>Places</span><input type="number" formControlName="passengers" min="1" max="100"></label>
              <label class="field"><span>Portes</span><input type="number" formControlName="doors" min="1" max="20"></label>
              <label class="field"><span>Transmission</span><select formControlName="transmission"><option value="AUTOMATIC">Automatique</option><option value="MANUAL">Manuelle</option><option value="SEMI_AUTOMATIC">Séquentielle</option></select></label>
              <label class="field"><span>Carburant</span><select formControlName="fuel"><option value="PETROL">Essence</option><option value="DIESEL">Diesel</option><option value="HYBRID">Hybride</option><option value="ELECTRIC">Électrique</option><option value="PLUG_IN_HYBRID">Hybride rechargeable</option></select></label>
              <label class="field"><span>Prix / jour</span><input type="number" formControlName="pricePerDay" min="0.001" step="0.001"></label>
              <label class="field"><span>Prix / semaine</span><input type="number" formControlName="pricePerWeek" min="0.001" step="0.001"></label>
              <label class="field"><span>Devise ISO</span><input formControlName="currency" maxlength="3"></label>
              <label class="field"><span>Statut flotte</span><select formControlName="status"><option value="AVAILABLE">Disponible</option><option value="RESERVED">Réservé</option><option value="RENTED">Loué</option><option value="MAINTENANCE">Maintenance</option><option value="UNAVAILABLE">Masqué</option></select></label>
            </div>
            <label class="field driver-toggle"><input type="checkbox" formControlName="withDriverAvailable"><span>Service chauffeur proposé</span></label>
            @if (form.controls.withDriverAvailable.value) { <label class="field"><span>Tarif avec chauffeur / jour</span><input type="number" formControlName="priceWithDriver" min="0.001" step="0.001"></label> }
            <label class="field"><span>Description</span><textarea formControlName="description" rows="4" maxlength="5000"></textarea></label>
            <label class="field"><span>Équipements (séparés par des virgules)</span><input formControlName="features" placeholder="Climatisation, GPS, Bluetooth"></label>
            <fieldset class="image-manager"><legend>Photographies</legend><label class="upload-button"><span>{{ uploading() ? 'Chargement…' : 'Ajouter une image' }}</span><input type="file" accept="image/jpeg,image/png,image/webp" (change)="uploadImage($event)" [disabled]="uploading()"></label>
              @if (images().length === 0) { <p class="image-empty">Aucune image associée.</p> }
              @for (image of images(); track $index) { <div class="image-row"><img [src]="image.url" [alt]="image.altText"><span>{{ image.altText }}</span><label class="primary-toggle"><input type="radio" name="primaryImage" [checked]="image.isPrimary" (change)="markPrimary($index)"> Principale</label><button class="remove-image" type="button" [attr.aria-label]="'Supprimer ' + image.altText" (click)="removeImage($index)">×</button></div> }
            </fieldset>
            <div class="form-actions"><button class="button button-primary" type="submit" [disabled]="saving() || uploading()">{{ saving() ? 'Enregistrement…' : editingId() ? 'Enregistrer' : 'Créer le véhicule' }}</button><button class="button button-secondary" type="button" (click)="closeForm()">Annuler</button></div>
          </form>
        </section></div>
      }
    </section>
  `,
  styles: `
    .admin-page { max-width:1500px; margin:0 auto; padding:clamp(1.5rem,4vw,3.5rem); }
    .page-heading { display:flex; align-items:end; justify-content:space-between; gap:1rem; margin-bottom:1.7rem; }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; }.eyebrow span { margin:0 .4rem; color:var(--color-metal); }
    h1 { margin:.7rem 0 0; font:500 clamp(2rem,4vw,3.5rem)/1.05 var(--font-display); }h1 em { color:var(--color-olive); }
    .count-label { padding:.55rem .75rem; border:1px solid var(--color-line); color:var(--color-muted); font-size:.72rem; white-space:nowrap; }
    .toolbar { display:flex; align-items:end; flex-wrap:wrap; gap:.65rem; margin-bottom:1rem; }
    .search { display:grid; gap:.3rem; min-width:min(100%,260px); }.search span { color:var(--color-muted); font-size:.68rem; }.search input { min-height:40px; padding:.5rem .65rem; border:1px solid var(--color-line); background:white; }
    .toolbar .button { min-height:40px; font-size:.73rem; }.toolbar .button-primary { margin-left:auto; }
    .table-wrap td strong { font-size:.82rem; }.table-wrap td small { display:block; margin-top:.2rem; color:var(--color-muted); font-size:.68rem; }
    .status-select { min-height:34px; max-width:150px; padding:0 .4rem; border:1px solid var(--color-line); background:#fff; font-size:.68rem; }
    .status { color:var(--color-olive); font-size:.7rem; }
    .actions { display:flex; gap:.7rem; }.text-action { padding:0; border:0; background:none; color:var(--color-olive); cursor:pointer; font-size:.7rem; text-decoration:underline; text-underline-offset:3px; }.text-action.danger { color:#a2443d; }
    .pagination { display:flex; justify-content:center; align-items:center; gap:1.5rem; margin:1.4rem 0; color:var(--color-muted); font-size:.73rem; }.page-button { min-height:38px; padding:0 .75rem; border:1px solid var(--color-line); background:transparent; cursor:pointer; font-size:.7rem; }.page-button:disabled { opacity:.45; cursor:not-allowed; }
    .editor-heading { display:flex; align-items:start; justify-content:space-between; margin-bottom:1.2rem; }.editor-heading h2 { margin:.5rem 0 0; font:500 1.8rem var(--font-display); }
    .close-button { width:36px; aspect-ratio:1; border:1px solid var(--color-line); background:transparent; cursor:pointer; font-size:1.25rem; }
    .modal-panel { width:min(100%,860px); }.modal-panel form { display:grid; gap:.9rem; }.form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:.75rem; }
    .field>span { font-size:.72rem; font-weight:600; }
    .driver-toggle { display:flex; align-items:center; gap:.55rem; }.driver-toggle input,.primary-toggle input { accent-color:var(--color-olive); }
    .driver-toggle input { width:auto; min-height:0; }
    .image-manager { display:grid; gap:.6rem; min-width:0; padding:.8rem; border:1px solid var(--color-line); }.image-manager legend { padding:0 .35rem; font-size:.74rem; font-weight:600; }.upload-button { position:relative; justify-self:start; display:grid; min-height:36px; place-items:center; padding:0 .7rem; border:1px solid var(--color-line); cursor:pointer; font-size:.7rem; }.upload-button input { position:absolute; width:1px; height:1px; opacity:0; }
    .image-empty { margin:0; color:var(--color-muted); font-size:.7rem; }.image-row { display:grid; grid-template-columns:54px minmax(0,1fr) auto 28px; align-items:center; gap:.6rem; min-width:0; padding:.4rem 0; border-top:1px solid var(--color-line); font-size:.68rem; }.image-row img { width:54px; aspect-ratio:1.4; object-fit:cover; }.image-row>span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.primary-toggle { display:flex; align-items:center; gap:.3rem; white-space:nowrap; }.remove-image { border:0; background:none; color:#a2443d; cursor:pointer; font-size:1rem; }
    .form-actions { display:flex; flex-wrap:wrap; gap:.6rem; justify-content:flex-end; }.form-actions .button { min-height:40px; font-size:.73rem; }
    @media(max-width:800px) { .toolbar .button-primary { margin-left:0; }.table-wrap table { min-width:780px; } }
    @media(max-width:560px) { .admin-page { padding:1.15rem; }.page-heading { align-items:start; flex-direction:column; }.form-grid { grid-template-columns:1fr; }.modal-panel { padding:1.1rem; }.image-row { grid-template-columns:44px minmax(0,1fr) 28px; }.image-row img { width:44px; }.primary-toggle { grid-column:2; grid-row:2; } }
  `,
})
export class AdminVehiclesComponent implements OnInit {
  private readonly api = inject(CatalogueApiService);
  private readonly builder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER']));
  protected readonly vehicles = signal<VehicleSummary[]>([]);
  protected readonly brands = signal<BrandSummary[]>([]);
  protected readonly categories = signal<CategorySummary[]>([]);
  protected readonly images = signal<VehicleImage[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly formOpen = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly pages = signal(0);
  protected readonly total = signal(0);
  protected readonly searchText = signal('');
  private readonly pageSize = 25;
  protected readonly form = this.builder.nonNullable.group({
    brandId: ['', Validators.required], categoryId: ['', Validators.required], model: ['', [Validators.required, Validators.maxLength(120)]],
    year: [String(new Date().getFullYear()), [Validators.required, Validators.min(1886), Validators.max(2100)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]], pricePerDay: ['', [Validators.required, Validators.min(.001)]],
    pricePerWeek: ['', [Validators.required, Validators.min(.001)]], priceWithDriver: [''], currency: ['TND', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    passengers: ['5', [Validators.required, Validators.min(1), Validators.max(100)]], doors: ['4', [Validators.required, Validators.min(1), Validators.max(20)]],
    transmission: ['AUTOMATIC' as VehicleSummary['transmission']], fuel: ['PETROL' as VehicleSummary['fuel']],
    withDriverAvailable: [false], status: ['AVAILABLE' as VehicleSummary['status']], features: [''],
  });

  ngOnInit(): void {
    this.api.brands(true).subscribe({ next: (result) => this.brands.set(result.content), error: () => this.error.set('Impossible de charger les marques.') });
    this.api.categories(true).subscribe({ next: (result) => this.categories.set(result.content), error: () => this.error.set('Impossible de charger les catégories.') });
    this.load();
  }

  protected search(): void { this.page.set(0); this.load(); }
  protected changePage(page: number): void { this.page.set(page); this.load(); }
  protected price(value: number, currency: string): string {
    try { return new Intl.NumberFormat('fr-TN', { style: 'currency', currency, maximumFractionDigits: 3 }).format(value); }
    catch { return `${value} ${currency}`; }
  }
  protected statusLabel(status: VehicleSummary['status']): string {
    return ({ AVAILABLE: 'Disponible', RESERVED: 'Réservé', RENTED: 'Loué', MAINTENANCE: 'Maintenance', UNAVAILABLE: 'Masqué' })[status];
  }
  protected statusValue(event: Event): VehicleSummary['status'] { return (event.target as HTMLSelectElement).value as VehicleSummary['status']; }

  protected newVehicle(): void {
    this.editingId.set(null);
    this.images.set([]);
    this.form.reset({ brandId: '', categoryId: '', model: '', year: String(new Date().getFullYear()), description: '', pricePerDay: '', pricePerWeek: '', priceWithDriver: '', currency: 'TND', passengers: '5', doors: '4', transmission: 'AUTOMATIC', fuel: 'PETROL', withDriverAvailable: false, status: 'AVAILABLE', features: '' });
    this.formOpen.set(true);
    this.error.set('');
  }

  protected edit(vehicle: VehicleSummary): void {
    this.editingId.set(vehicle.id);
    this.images.set(vehicle.images.map((image) => ({ ...image })));
    this.form.patchValue({
      brandId: vehicle.brand.id, categoryId: vehicle.category.id, model: vehicle.model, year: String(vehicle.year), description: vehicle.description,
      pricePerDay: String(vehicle.pricePerDay), pricePerWeek: String(vehicle.pricePerWeek), priceWithDriver: vehicle.priceWithDriver == null ? '' : String(vehicle.priceWithDriver),
      currency: vehicle.currency, passengers: String(vehicle.passengers), doors: String(vehicle.doors), transmission: vehicle.transmission, fuel: vehicle.fuel,
      withDriverAvailable: vehicle.withDriverAvailable, status: vehicle.status, features: vehicle.features.join(', '),
    });
    this.formOpen.set(true);
    this.error.set('');
  }

  protected closeForm(): void { this.formOpen.set(false); this.editingId.set(null); }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving() || this.uploading()) return;
    const value = this.form.getRawValue();
    if (value.withDriverAvailable && Number(value.priceWithDriver) <= 0) { this.error.set('Saisissez un tarif chauffeur positif.'); return; }
    const input: VehicleInput = {
      brandId: value.brandId, categoryId: value.categoryId, model: value.model.trim(), year: Number(value.year), description: value.description.trim(),
      pricePerDay: Number(value.pricePerDay), pricePerWeek: Number(value.pricePerWeek), priceWithDriver: value.priceWithDriver ? Number(value.priceWithDriver) : null,
      currency: value.currency.toUpperCase(), passengers: Number(value.passengers), doors: Number(value.doors), transmission: value.transmission, fuel: value.fuel,
      withDriverAvailable: value.withDriverAvailable, status: value.status,
      features: value.features.split(',').map((feature) => feature.trim()).filter(Boolean), images: this.images(),
    };
    const id = this.editingId();
    this.saving.set(true);
    this.error.set('');
    const request = id ? this.api.updateVehicle(id, input) : this.api.createVehicle(input);
    request.subscribe({
      next: () => { this.success.set(id ? 'Le véhicule a été modifié.' : 'Le véhicule a été créé.'); this.saving.set(false); this.closeForm(); this.load(); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.saving.set(false); },
    });
  }

  protected setStatus(vehicle: VehicleSummary, status: VehicleSummary['status']): void {
    this.api.updateVehicleStatus(vehicle.id, status).subscribe({
      next: () => { this.success.set('Le statut du véhicule a été mis à jour.'); this.error.set(''); this.load(); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.load(); },
    });
  }

  protected remove(vehicle: VehicleSummary): void {
    if (!window.confirm(`Supprimer ${vehicle.brand.name} ${vehicle.model} ?`)) return;
    this.api.deleteVehicle(vehicle.id).subscribe({
      next: () => { this.success.set('Le véhicule a été supprimé.'); this.error.set(''); this.load(); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.success.set(''); },
    });
  }

  protected uploadImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { this.error.set('Chaque image doit peser au maximum 10 Mo.'); return; }
    this.uploading.set(true);
    this.api.uploadImage(file).subscribe({
      next: (result) => {
        const current = this.images();
        this.images.set([...current, { url: result.url, altText: file.name.slice(0, 200), position: current.length, isPrimary: current.length === 0 }]);
        this.uploading.set(false);
      },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.uploading.set(false); },
    });
  }

  protected removeImage(index: number): void {
    const image = this.images()[index];
    if (image && !image.id && image.url.startsWith('/api/files/')) {
      this.api.deleteUploadedImage(image.url).subscribe({
        next: () => this.removeImageFromForm(index),
        error: (error: unknown) => this.error.set(this.errorText(error)),
      });
      return;
    }
    this.removeImageFromForm(index);
  }

  private removeImageFromForm(index: number): void {
    const remaining = this.images().filter((_, itemIndex) => itemIndex !== index).map((image, position) => ({ ...image, position }));
    if (remaining.length > 0 && !remaining.some((image) => image.isPrimary)) remaining[0] = { ...remaining[0], isPrimary: true };
    this.images.set(remaining);
  }

  protected markPrimary(index: number): void { this.images.update((images) => images.map((image, imageIndex) => ({ ...image, isPrimary: imageIndex === index }))); }

  private load(): void {
    const filters: VehicleFilters = { search: this.searchText().trim(), sort: 'newest', page: this.page(), size: this.pageSize };
    this.loading.set(true);
    this.api.vehicles(filters, true).subscribe({
      next: (result) => { this.vehicles.set(result.content); this.page.set(result.page); this.pages.set(result.totalPages); this.total.set(result.totalElements); this.loading.set(false); },
      error: (error: unknown) => { this.vehicles.set([]); this.error.set(this.errorText(error)); this.loading.set(false); },
    });
  }

  private errorText(error: unknown): string {
    return error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? 'La demande a échoué.' : 'La demande a échoué.';
  }
}
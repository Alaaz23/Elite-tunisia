import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiHttpError } from '../../core/models/api-http-error';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogueApiService, BrandInput, BrandSummary, CategoryInput, CategorySummary } from '../../core/services/catalogue-api.service';

@Component({
  selector: 'app-admin-taxonomy',
  imports: [ReactiveFormsModule],
  template: `
    <section class="admin-page" aria-labelledby="admin-taxonomy-title">
      <header class="page-heading"><div><p class="eyebrow">ADMINISTRATION <span>/</span> CATALOGUE</p><h1 id="admin-taxonomy-title">{{ isBrands() ? 'Marques' : 'Catégories' }} <em>du catalogue.</em></h1></div><span class="count-label">{{ entries().length }} éléments</span></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      <div class="content-grid"><section class="records" aria-label="Éléments enregistrés">
        @if (loading()) { <p class="loading-state" role="status">Chargement…</p> }
        @if (!loading() && entries().length === 0) { <div class="empty-state"><strong>Aucun élément</strong><p>Créez la première entrée avec le formulaire.</p></div> }
        @if (entries().length > 0) { <div class="table-wrap"><table><thead><tr><th>Nom</th><th>Identifiant</th><th>Statut</th><th>Actions</th></tr></thead><tbody>
          @for (entry of entries(); track entry.id) { <tr><td><strong>{{ entry.name }}</strong><small>{{ entry.description }}</small></td><td>{{ entry.slug }}</td><td><span class="status" [class.inactive]="!entry.active"><i></i>{{ entry.active ? 'Actif' : 'Inactif' }}</span></td><td class="actions">@if (canManage()) { <button class="text-action" type="button" (click)="edit(entry)">Modifier</button><button class="text-action danger" type="button" (click)="remove(entry)">Supprimer</button> } @else { <span class="read-only">Consultation</span> }</td></tr> }
        </tbody></table></div> }
      </section>
      @if (canManage()) { <section class="editor"><p class="eyebrow">{{ editingId() ? 'MODIFICATION' : 'NOUVELLE ENTRÉE' }}</p><h2>{{ editingId() ? 'Modifier' : 'Créer' }} {{ isBrands() ? 'une marque' : 'une catégorie' }}</h2>
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <label class="field"><span>Nom</span><input formControlName="name" maxlength="100"></label>
          <label class="field"><span>Description</span><textarea formControlName="description" maxlength="2000" rows="5"></textarea></label>
          @if (isBrands()) { <label class="field"><span>Logo (URL ou image chargée)</span><input formControlName="logoUrl" maxlength="1000"></label><label class="field upload-field"><span>Charger un logo</span><input type="file" accept="image/jpeg,image/png,image/webp" (change)="uploadLogo($event)" [disabled]="uploading()"></label> }
          <label class="toggle"><input type="checkbox" formControlName="active"><span>Publié dans le catalogue public</span></label>
          <div class="form-actions"><button class="button button-primary" type="submit" [disabled]="saving() || uploading()">{{ saving() ? 'Enregistrement…' : editingId() ? 'Enregistrer' : 'Créer' }}</button>@if (editingId()) { <button class="button button-secondary" type="button" (click)="cancel()">Annuler</button> }</div>
        </form>
      </section> } @else { <p class="read-only-note">Votre rôle autorise la consultation de ces données.</p> }</div>
    </section>
  `,
  styles: `
    .admin-page { max-width:1440px; margin:0 auto; padding:clamp(1.5rem,4vw,3.5rem); }
    .page-heading { display:flex; align-items:end; justify-content:space-between; gap:1rem; margin-bottom:2rem; }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; }
    .eyebrow span { margin:0 .4rem; color:var(--color-metal); }
    h1 { margin:.7rem 0 0; font:500 clamp(2rem,4vw,3.5rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .count-label { padding:.55rem .75rem; border:1px solid var(--color-line); color:var(--color-muted); font-size:.72rem; white-space:nowrap; }
    .content-grid { display:grid; grid-template-columns:minmax(0,1.6fr) minmax(280px,.8fr); gap:1.5rem; align-items:start; }
    .table-wrap { max-height:70vh; }
    td strong { font:500 .84rem var(--font-body); }
    td small { display:block; max-width:36ch; overflow:hidden; margin-top:.25rem; color:var(--color-muted); font-size:.68rem; text-overflow:ellipsis; }
    .status { display:inline-flex; align-items:center; gap:.4rem; color:var(--color-olive); font-size:.7rem; }
    .status i { width:7px; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status.inactive { color:var(--color-muted); }.status.inactive i { background:#aaa; }
    .actions { display:flex; gap:.7rem; }
    .text-action { padding:0; border:0; background:none; color:var(--color-olive); cursor:pointer; font-size:.7rem; text-decoration:underline; text-underline-offset:3px; }
    .text-action.danger { color:#a2443d; }
    .read-only { color:var(--color-muted); font-size:.7rem; }.read-only-note { padding:1rem; border-left:3px solid var(--color-metal); background:#f5f0e5; color:var(--color-muted); font-size:.78rem; }
    .editor { padding:1.3rem; border:1px solid var(--color-line); background:#f1f1ec; }
    .editor h2 { margin:.55rem 0 1rem; font:500 1.45rem var(--font-display); }
    .editor form { display:grid; gap:.8rem; }
    .field { display:grid; gap:.35rem; }.field span { font-size:.72rem; font-weight:600; }
    .field input,.field textarea { width:100%; padding:.6rem; border:1px solid var(--color-line); background:white; color:var(--color-ink); font-size:.76rem; }
    .field input { min-height:40px; }.field textarea { resize:vertical; }
    .upload-field input { padding:.45rem; }
    .toggle { display:flex; align-items:center; gap:.55rem; padding:.3rem 0; font-size:.73rem; }
    .toggle input { accent-color:var(--color-olive); }
    .form-actions { display:flex; flex-wrap:wrap; gap:.55rem; margin-top:.35rem; }.form-actions .button { min-height:40px; font-size:.74rem; }
    @media(max-width:980px) { .content-grid { grid-template-columns:1fr; }.editor { max-width:620px; } }
    @media(max-width:620px) { .admin-page { padding:1.2rem; }.page-heading { align-items:start; flex-direction:column; }.table-wrap table { min-width:700px; } }
  `,
})
export class AdminTaxonomyComponent implements OnInit {
  private readonly api = inject(CatalogueApiService);
  private readonly builder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER']));
  protected readonly isBrands = signal(false);
  protected readonly entries = signal<(BrandSummary | CategorySummary)[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = this.builder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    logoUrl: ['', Validators.maxLength(1000)],
    active: [true],
  });

  ngOnInit(): void {
    this.isBrands.set(this.route.snapshot.data['catalogueType'] === 'brands');
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    const request = this.isBrands() ? this.api.brands(true) : this.api.categories(true);
    request.subscribe({
      next: (result) => { this.entries.set(result.content); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger la liste.'); this.loading.set(false); },
    });
  }

  protected edit(entry: BrandSummary | CategorySummary): void {
    this.editingId.set(entry.id);
    this.form.patchValue({ name: entry.name, description: entry.description, logoUrl: 'logoUrl' in entry ? entry.logoUrl ?? '' : '', active: entry.active });
    this.success.set('');
    this.error.set('');
  }

  protected cancel(): void { this.editingId.set(null); this.form.reset({ name: '', description: '', logoUrl: '', active: true }); }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    const id = this.editingId();
    this.saving.set(true);
    this.error.set('');
    const request = this.isBrands()
      ? (id ? this.api.updateBrand(id, this.brandInput(value)) : this.api.createBrand(this.brandInput(value)))
      : (id ? this.api.updateCategory(id, this.categoryInput(value)) : this.api.createCategory(this.categoryInput(value)));
    request.subscribe({
      next: () => { this.success.set(id ? 'Les modifications sont enregistrées.' : 'L’entrée a été créée.'); this.saving.set(false); this.cancel(); this.load(); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.saving.set(false); },
    });
  }

  protected remove(entry: BrandSummary | CategorySummary): void {
    if (!window.confirm(`Supprimer « ${entry.name} » ?`)) return;
    const request = this.isBrands() ? this.api.deleteBrand(entry.id) : this.api.deleteCategory(entry.id);
    request.subscribe({
      next: () => { this.success.set('L’entrée a été supprimée.'); this.error.set(''); this.load(); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.success.set(''); },
    });
  }

  protected uploadLogo(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.api.uploadImage(file).subscribe({
      next: (image) => { this.form.controls.logoUrl.setValue(image.url); this.uploading.set(false); },
      error: (error: unknown) => { this.error.set(this.errorText(error)); this.uploading.set(false); },
    });
  }

  private brandInput(value: ReturnType<typeof this.form.getRawValue>): BrandInput {
    return { name: value.name, description: value.description, logoUrl: value.logoUrl || null, active: value.active };
  }

  private categoryInput(value: ReturnType<typeof this.form.getRawValue>): CategoryInput {
    return { name: value.name, description: value.description, active: value.active };
  }

  private errorText(error: unknown): string {
    return error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? 'La demande a échoué.' : 'La demande a échoué.';
  }
}
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CatalogueApiService, BrandSummary, CategorySummary, VehicleFilters, VehicleSummary } from '../../core/services/catalogue-api.service';
import { ApiHttpError } from '../../core/models/api-http-error';
import { BookingApiService, toBookingInstant } from '../../core/services/booking-api.service';

@Component({
  selector: 'app-vehicle-catalogue',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="catalogue" aria-labelledby="catalogue-title">
      <header class="catalogue-heading">
        <div><p class="eyebrow">ELITE TUNISIA <span>/</span> LA FLOTTE</p><h1 id="catalogue-title">Une flotte <em>choisie.</em></h1></div>
        <p class="result-count">{{ total() }} véhicules</p>
      </header>
      <form class="filters" [formGroup]="form" (ngSubmit)="search()">
        <label class="filter search-filter"><span>Recherche</span><input formControlName="search" placeholder="Marque ou modèle" autocomplete="off"></label>
        <label class="filter"><span>Marque</span><select formControlName="brand"><option value="">Toutes</option>@for (brand of brands(); track brand.id) { <option [value]="brand.slug">{{ brand.name }}</option> }</select></label>
        <label class="filter"><span>Catégorie</span><select formControlName="category"><option value="">Toutes</option>@for (category of categories(); track category.id) { <option [value]="category.slug">{{ category.name }}</option> }</select></label>
        <label class="filter"><span>Prix / jour min.</span><input type="number" min="0" step="1" formControlName="minPrice"></label>
        <label class="filter"><span>Prix / jour max.</span><input type="number" min="0" step="1" formControlName="maxPrice"></label>
        <label class="filter"><span>Places min.</span><select formControlName="passengers"><option value="">Indifférent</option><option value="2">2+</option><option value="4">4+</option><option value="5">5+</option><option value="7">7+</option></select></label>
        <label class="filter"><span>Transmission</span><select formControlName="transmission"><option value="">Toutes</option><option value="AUTOMATIC">Automatique</option><option value="MANUAL">Manuelle</option><option value="SEMI_AUTOMATIC">Séquentielle</option></select></label>
        <label class="filter"><span>Chauffeur</span><select formControlName="withDriver"><option value="">Indifférent</option><option value="true">Disponible</option><option value="false">Sans chauffeur</option></select></label>
        <label class="filter"><span>Trier par</span><select formControlName="sort"><option value="newest">Nouveautés</option><option value="price_asc">Prix croissant</option><option value="price_desc">Prix décroissant</option><option value="year_desc">Année récente</option><option value="name_asc">Modèle A à Z</option></select></label>
        <label class="filter"><span>Début de période</span><input type="datetime-local" formControlName="start"></label>
        <label class="filter"><span>Fin de période</span><input type="datetime-local" formControlName="end"></label>
        <div class="filter-actions"><button class="button button-primary" type="submit" [disabled]="loading()">Afficher</button><button class="reset" type="button" (click)="reset()">Effacer</button></div>
      </form>
      @if (error()) { <div class="error-state" role="alert"><strong>Le catalogue est indisponible.</strong><p>{{ error() }}</p><button class="button button-secondary" type="button" (click)="load()">Réessayer</button></div> }
      @if (loading()) { <p class="loading-state" role="status">Chargement de la flotte…</p> }
      @if (!loading() && !error() && vehicles().length === 0) { <div class="empty-state"><strong>Aucun véhicule ne correspond</strong><p>Modifiez un ou plusieurs filtres pour élargir votre recherche.</p></div> }
      @if (!loading() && !error() && vehicles().length > 0) {
        <div class="vehicle-grid">
          @for (vehicle of vehicles(); track vehicle.id) {
            <article class="vehicle-card">
              <a class="vehicle-image" [routerLink]="['/vehicles', vehicle.id]" [attr.aria-label]="'Voir ' + vehicle.brand.name + ' ' + vehicle.model">
                @if (primaryImage(vehicle); as image) { <img [src]="image.url" [alt]="image.altText" loading="lazy"> }
                @else { <div class="image-fallback"><span>{{ vehicle.brand.name }}</span><strong>{{ vehicle.model }}</strong><small>{{ vehicle.year }}</small></div> }
                <span class="category-mark">{{ vehicle.category.name }}</span>
              </a>
              <div class="vehicle-info">
                <div class="vehicle-name"><div><p>{{ vehicle.brand.name }} · {{ vehicle.year }}</p><h2>{{ vehicle.model }}</h2></div><span class="fuel-mark">{{ fuelLabel(vehicle.fuel) }}</span></div>
                <div class="specs"><span>{{ vehicle.passengers }} places</span><span>{{ transmissionLabel(vehicle.transmission) }}</span><span>{{ vehicle.doors }} portes</span></div>
                <div class="price-line"><p><strong>{{ price(vehicle.pricePerDay, vehicle.currency) }}</strong><span> / jour</span></p><a [routerLink]="['/vehicles', vehicle.id]">Découvrir <span aria-hidden="true">↗</span></a></div>
              </div>
            </article>
          }
        </div>
        <nav class="pagination" aria-label="Pagination du catalogue"><button class="page-button" type="button" [disabled]="page() === 0 || loading()" (click)="changePage(page() - 1)">← Précédent</button><span>Page {{ page() + 1 }} / {{ pages() }}</span><button class="page-button" type="button" [disabled]="page() + 1 >= pages() || loading()" (click)="changePage(page() + 1)">Suivant →</button></nav>
      }
    </section>
  `,
  styles: `
    .catalogue { max-width:1500px; margin:0 auto; padding:clamp(1.5rem,4vw,4rem); }
    .catalogue-heading { display:flex; align-items:end; justify-content:space-between; gap:1rem; margin-bottom:1.7rem; }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); }
    h1 { margin:.75rem 0 0; font:500 clamp(2.5rem,5vw,4.3rem)/1 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .result-count { margin:0; color:var(--color-muted); font-size:.78rem; }
    .filters { display:grid; grid-template-columns:repeat(6,minmax(0,1fr)); gap:.75rem; padding:1rem 0 1.4rem; margin-bottom:1.5rem; border-top:1px solid var(--color-line); border-bottom:1px solid var(--color-line); }
    .filter { display:grid; gap:.35rem; min-width:0; }
    .filter span { color:var(--color-muted); font-size:.67rem; font-weight:600; }
    .filter input,.filter select { width:100%; min-width:0; height:40px; padding:0 .6rem; border:1px solid var(--color-line); border-radius:0; background:white; color:var(--color-ink); font-size:.76rem; }
    .search-filter { grid-column:span 2; }
    .filter-actions { display:flex; align-items:end; gap:.75rem; grid-column:span 2; }
    .filter-actions .button { min-height:40px; font-size:.75rem; }
    .reset { min-height:40px; border:0; background:none; color:var(--color-muted); cursor:pointer; font-size:.75rem; text-decoration:underline; text-underline-offset:3px; }
    .vehicle-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1.3rem; }
    .vehicle-card { min-width:0; border:1px solid var(--color-line); background:#fff; animation:rise-in .35s ease both; }
    .vehicle-image { position:relative; display:grid; aspect-ratio:1.55; overflow:hidden; background:#e4e7e1; color:var(--color-ink); text-decoration:none; }
    .vehicle-image img { width:100%; height:100%; object-fit:cover; }
    .image-fallback { display:flex; flex-direction:column; align-items:center; justify-content:center; background:linear-gradient(140deg,#e6e9e3 0%,#cfd8ce 52%,#b8c6b6 100%); }
    .image-fallback span { color:#52684e; font-size:.68rem; font-weight:700; }
    .image-fallback strong { margin:.15rem 0; font:500 clamp(1.4rem,2vw,2.2rem) var(--font-display); text-align:center; }
    .category-mark { position:absolute; top:.75rem; left:.75rem; padding:.4rem .55rem; background:rgba(255,255,255,.92); font-size:.65rem; }
    .vehicle-info { padding:1rem 1rem .85rem; }
    .vehicle-name { display:flex; align-items:start; justify-content:space-between; gap:.5rem; }
    .vehicle-name p { margin:0 0 .2rem; color:var(--color-muted); font-size:.68rem; }
    .vehicle-name h2 { margin:0; font:500 1.55rem/1.1 var(--font-display); }
    .fuel-mark { padding:.32rem .4rem; background:#eef0e9; color:var(--color-olive); font-size:.6rem; white-space:nowrap; }
    .specs { display:flex; flex-wrap:wrap; gap:.4rem 1rem; padding:.85rem 0; border-bottom:1px solid var(--color-line); color:var(--color-muted); font-size:.67rem; }
    .price-line { display:flex; align-items:center; justify-content:space-between; gap:.6rem; padding-top:.8rem; }
    .price-line p { margin:0; }
    .price-line strong { font:500 1.15rem var(--font-display); }
    .price-line p span { color:var(--color-muted); font-size:.68rem; }
    .price-line a { color:var(--color-olive); font-size:.7rem; font-weight:600; text-decoration:none; }
    .pagination { display:flex; align-items:center; justify-content:center; gap:1.5rem; margin:2rem 0 .5rem; color:var(--color-muted); font-size:.75rem; }
    .page-button { min-height:40px; padding:0 .8rem; border:1px solid var(--color-line); background:transparent; cursor:pointer; font-size:.74rem; }
    .page-button:disabled { opacity:.45; cursor:not-allowed; }
    @keyframes rise-in { from { opacity:0; transform:translateY(8px); } to { opacity:1; transform:translateY(0); } }
    @media(max-width:1000px) { .filters { grid-template-columns:repeat(3,minmax(0,1fr)); } .vehicle-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    @media(max-width:620px) { .catalogue { padding:1.5rem 1rem; } .filters { grid-template-columns:repeat(2,minmax(0,1fr)); } .search-filter,.filter-actions { grid-column:span 2; } .vehicle-grid { grid-template-columns:1fr; } .catalogue-heading { align-items:flex-start; flex-direction:column; } .result-count { align-self:flex-end; margin-top:-.4rem; } .pagination { gap:.7rem; } }
    @media(prefers-reduced-motion:reduce) { .vehicle-card { animation:none; } }
  `,
})
export class VehicleCatalogueComponent implements OnInit {
  private readonly api = inject(CatalogueApiService);
  private readonly builder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookingApi = inject(BookingApiService);
  private zone = '';
  protected readonly vehicles = signal<VehicleSummary[]>([]);
  protected readonly brands = signal<BrandSummary[]>([]);
  protected readonly categories = signal<CategorySummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly page = signal(0);
  protected readonly pages = signal(0);
  protected readonly total = signal(0);
  private readonly pageSize = 9;
  protected readonly form = this.builder.nonNullable.group({
    search: '', brand: '', category: '', minPrice: '', maxPrice: '', passengers: '',
    transmission: '', withDriver: '', sort: 'newest', start: '', end: '',
  });

  ngOnInit(): void {
    const query = this.route.snapshot.queryParamMap;
    this.form.patchValue({
      search: query.get('search') ?? '', brand: query.get('brand') ?? '', category: query.get('category') ?? '',
      minPrice: query.get('minPrice') ?? '', maxPrice: query.get('maxPrice') ?? '', passengers: query.get('passengers') ?? '',
      transmission: query.get('transmission') ?? '', withDriver: query.get('withDriver') ?? '', sort: query.get('sort') ?? 'newest',
      start: query.get('start') ?? '', end: query.get('end') ?? '',
    });
    this.page.set(Math.max(Number(query.get('page') ?? 0), 0));
    this.api.brands().subscribe({ next: (result) => this.brands.set(result.content), error: () => this.brands.set([]) });
    this.api.categories().subscribe({ next: (result) => this.categories.set(result.content), error: () => this.categories.set([]) });
    this.bookingApi.policy().subscribe({ next: (policy) => { this.zone = policy.businessTimeZone; this.load(); }, error: () => this.load() });
  }

  protected search(): void {
    this.page.set(0);
    const values = this.form.getRawValue();
    void this.router.navigate([], { relativeTo: this.route, queryParams: { ...values, page: 0 }, queryParamsHandling: 'merge', replaceUrl: true }).then(() => this.load());
  }
  protected changePage(page: number): void {
    this.page.set(page);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page }, queryParamsHandling: 'merge', replaceUrl: true }).then(() => this.load());
  }
  protected reset(): void {
    this.form.reset({ search: '', brand: '', category: '', minPrice: '', maxPrice: '', passengers: '', transmission: '', withDriver: '', sort: 'newest', start: '', end: '' });
    this.search();
  }

  protected primaryImage(vehicle: VehicleSummary) { return vehicle.images.find((image) => image.isPrimary) ?? vehicle.images[0] ?? null; }
  protected price(value: number, currency: string): string {
    try { return new Intl.NumberFormat('fr-TN', { style: 'currency', currency, maximumFractionDigits: 3 }).format(value); }
    catch { return `${value} ${currency}`; }
  }
  protected transmissionLabel(value: VehicleSummary['transmission']): string {
    return value === 'AUTOMATIC' ? 'Automatique' : value === 'MANUAL' ? 'Manuelle' : 'Séquentielle';
  }
  protected fuelLabel(value: VehicleSummary['fuel']): string {
    return ({ PETROL: 'Essence', DIESEL: 'Diesel', HYBRID: 'Hybride', ELECTRIC: 'Électrique', PLUG_IN_HYBRID: 'Hybride rechargeable' })[value];
  }

  protected load(): void {
    const values = this.form.getRawValue();
    let startDateTime: string | undefined; let endDateTime: string | undefined;
    if (values.start || values.end) {
      try {
        startDateTime = toBookingInstant(values.start, this.zone); endDateTime = toBookingInstant(values.end, this.zone);
        if (new Date(endDateTime) <= new Date(startDateTime) || new Date(startDateTime).getTime() <= Date.now()) throw new Error();
      } catch { this.loading.set(false); this.error.set('Choisissez un début et une fin valides dans le futur.'); return; }
    }
    const filters: VehicleFilters = {
      startDateTime, endDateTime,
      search: values.search.trim(), brand: values.brand, category: values.category,
      minPrice: values.minPrice === '' ? null : Number(values.minPrice),
      maxPrice: values.maxPrice === '' ? null : Number(values.maxPrice),
      passengers: values.passengers === '' ? null : Number(values.passengers),
      transmission: values.transmission, withDriver: values.withDriver === '' ? null : values.withDriver === 'true',
      sort: values.sort, page: this.page(), size: this.pageSize,
    };
    this.loading.set(true);
    this.error.set('');
    this.api.vehicles(filters).subscribe({
      next: (result) => {
        this.vehicles.set(result.content);
        this.page.set(result.page);
        this.pages.set(result.totalPages);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.vehicles.set([]);
        this.error.set(error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? 'Impossible de joindre le service catalogue.' : 'Impossible de joindre le service catalogue.');
        this.loading.set(false);
      },
    });
  }
}
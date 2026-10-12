import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogueApiService, VehicleSummary } from '../../core/services/catalogue-api.service';
import { ApiHttpError } from '../../core/models/api-http-error';
import { BookingFormComponent } from '../bookings/booking-form.component';

@Component({
  selector: 'app-vehicle-detail',
  imports: [RouterLink, BookingFormComponent],
  template: `
    <main class="detail-page">
      @if (loading()) { <p class="loading-state" role="status">Chargement du véhicule…</p> }
      @if (error()) { <div class="error-state" role="alert"><strong>Cette fiche n’est pas disponible.</strong><p>{{ error() }}</p><a class="button button-secondary" routerLink="/vehicles">Retour à la flotte</a></div> }
      @if (vehicle(); as item) {
        <p class="breadcrumb"><a routerLink="/vehicles">La flotte</a><span>/</span>{{ item.brand.name }}</p>
        <header class="detail-heading"><div><p class="eyebrow">{{ item.category.name }} <span>·</span> {{ item.year }}</p><h1>{{ item.brand.name }} <em>{{ item.model }}</em></h1></div><span class="status-mark">{{ statusLabel(item.status) }}</span></header>
        <div class="detail-grid">
          <section class="visuals" aria-label="Photographies du véhicule">
            @if (primaryImage(item); as image) { <img class="hero-image" [src]="image.url" [alt]="image.altText"> }
            @else { <div class="hero-fallback"><span>{{ item.brand.name }}</span><strong>{{ item.model }}</strong><small>{{ item.year }}</small></div> }
            @if (item.images.length > 1) { <div class="thumb-strip">@for (image of item.images; track image.id ?? image.url) { <img [src]="image.url" [alt]="image.altText" loading="lazy"> }</div> }
          </section>
          <aside class="summary-panel">
            <p class="eyebrow">TARIFS AFFICHÉS</p><div class="rate"><span>À la journée</span><strong>{{ price(item.pricePerDay, item.currency) }}</strong></div><div class="rate"><span>À la semaine</span><strong>{{ price(item.pricePerWeek, item.currency) }}</strong></div>
            @if (item.withDriverAvailable && item.priceWithDriver !== null) { <div class="rate"><span>Avec chauffeur</span><strong>{{ price(item.priceWithDriver, item.currency) }}</strong></div> }
            <div class="summary-specs"><span>{{ item.passengers }} places</span><span>{{ item.doors }} portes</span><span>{{ transmissionLabel(item.transmission) }}</span><span>{{ fuelLabel(item.fuel) }}</span></div>
          </aside>
        </div>
        <app-booking-form [vehicle]="item" />
        <div class="content-grid"><section class="description"><p class="eyebrow">LE VÉHICULE</p><h2>Conçu pour votre <em>prochain trajet.</em></h2><p>{{ item.description }}</p></section>
          @if (item.features.length > 0) { <section class="features"><p class="eyebrow">ÉQUIPEMENTS</p><ul>@for (feature of item.features; track feature) { <li>{{ feature }}</li> }</ul></section> }
        </div>
      }
    </main>
  `,
  styles: `
    .detail-page { max-width:1360px; min-height:65vh; margin:0 auto; padding:clamp(1.5rem,4vw,4rem); }
    .breadcrumb { display:flex; gap:.65rem; margin:0 0 1.25rem; color:var(--color-muted); font-size:.72rem; }
    .breadcrumb a { color:var(--color-olive); text-decoration:none; }
    .breadcrumb span { color:var(--color-metal); }
    .detail-heading { display:flex; align-items:end; justify-content:space-between; gap:1rem; margin-bottom:1.5rem; }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; }
    .eyebrow span { color:var(--color-metal); }
    h1 { margin:.6rem 0 0; font:500 clamp(2.4rem,5.5vw,4.8rem)/1 var(--font-display); }
    h1 em,.description h2 em { color:var(--color-olive); }
    .status-mark { padding:.45rem .65rem; background:var(--color-sage); color:var(--color-olive); font-size:.69rem; white-space:nowrap; }
    .detail-grid { display:grid; grid-template-columns:minmax(0,1.65fr) minmax(260px,.75fr); gap:1.5rem; align-items:start; }
    .visuals { min-width:0; }
    .hero-image,.hero-fallback { display:flex; width:100%; aspect-ratio:1.62; object-fit:cover; }
    .hero-fallback { flex-direction:column; align-items:center; justify-content:center; background:linear-gradient(140deg,#e6e9e3,#c4d0c1); }
    .hero-fallback span { color:var(--color-olive); font-size:.8rem; font-weight:700; }
    .hero-fallback strong { font:500 clamp(2rem,5vw,4rem) var(--font-display); }
    .hero-fallback small { color:var(--color-muted); }
    .thumb-strip { display:flex; gap:.6rem; overflow:auto; padding-top:.6rem; }
    .thumb-strip img { width:100px; aspect-ratio:1.5; object-fit:cover; }
    .summary-panel { padding:1.2rem; border:1px solid var(--color-line); background:white; }
    .rate { display:flex; justify-content:space-between; gap:.7rem; padding:.9rem 0; border-bottom:1px solid var(--color-line); color:var(--color-muted); font-size:.76rem; }
    .rate strong { color:var(--color-ink); font:500 1.1rem var(--font-display); white-space:nowrap; }
    .summary-specs { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; padding-top:1rem; color:var(--color-ink); font-size:.72rem; }
    .content-grid { display:grid; grid-template-columns:minmax(0,1.3fr) minmax(240px,.7fr); gap:2rem; margin:2.5rem 0; }
    .description h2 { margin:.6rem 0; font:500 2rem/1.15 var(--font-display); }
    .description>p:last-child { max-width:68ch; color:var(--color-muted); font-size:.88rem; line-height:1.8; white-space:pre-line; }
    .features { padding-top:.2rem; }
    .features ul { display:grid; grid-template-columns:1fr 1fr; gap:.6rem 1.2rem; padding:0; list-style:none; }
    .features li { padding:.55rem 0; border-bottom:1px solid var(--color-line); font-size:.75rem; }
    @media(max-width:760px) { .detail-grid,.content-grid { grid-template-columns:1fr; } .detail-heading { align-items:start; flex-direction:column; } .summary-panel { max-width:none; } .hero-image,.hero-fallback { aspect-ratio:1.2; } }
  `,
})
export class VehicleDetailComponent implements OnInit {
  private readonly api = inject(CatalogueApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly vehicle = signal<VehicleSummary | null>(null);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (!id) { this.loading.set(false); this.error.set('Identifiant de véhicule absent.'); return; }
      this.loading.set(true);
      this.error.set('');
      this.vehicle.set(null);
      this.api.vehicle(id).subscribe({
        next: (item) => { this.vehicle.set(item); this.loading.set(false); },
        error: (error: unknown) => {
          this.error.set(error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? 'Impossible de charger la fiche.' : 'Impossible de charger la fiche.');
          this.loading.set(false);
        },
      });
    });
  }

  protected primaryImage(item: VehicleSummary) { return item.images.find((image) => image.isPrimary) ?? item.images[0] ?? null; }
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
  protected statusLabel(value: VehicleSummary['status']): string { return value === 'AVAILABLE' ? 'Disponible' : 'Indisponible'; }
}
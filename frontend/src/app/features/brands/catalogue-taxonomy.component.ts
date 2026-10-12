import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogueApiService, BrandSummary, CategorySummary } from '../../core/services/catalogue-api.service';

@Component({
  selector: 'app-catalogue-taxonomy',
  imports: [DecimalPipe, RouterLink],
  template: `
    <section class="taxonomy-page" aria-labelledby="taxonomy-title">
      <p class="eyebrow">ELITE TUNISIA <span>/</span> {{ kind() === 'brands' ? 'MOTORISTES' : 'CLASSES DE VÉHICULES' }}</p>
      <header><h1 id="taxonomy-title">{{ kind() === 'brands' ? 'Les marques.' : 'Les catégories.' }}</h1><p>{{ entries().length }} références</p></header>
      @if (loading()) { <p class="loading-state" role="status">Chargement…</p> }
      @if (error()) { <div class="error-state" role="alert"><strong>La liste est indisponible.</strong><p>{{ error() }}</p></div> }
      @if (!loading() && !error() && entries().length === 0) { <div class="empty-state"><strong>Aucune référence publiée</strong><p>Les marques et catégories activées apparaîtront ici.</p></div> }
      @if (!loading() && !error() && entries().length > 0) {
        <div class="entry-list">@for (entry of entries(); track entry.id) {
          <a class="entry" [routerLink]="['/vehicles']" [queryParams]="kind() === 'brands' ? { brand: entry.slug } : { category: entry.slug }">
            <span class="entry-index">{{ $index + 1 | number:'2.0' }}</span><div><h2>{{ entry.name }}</h2><p>{{ entry.description }}</p></div><span class="arrow" aria-hidden="true">↗</span>
          </a>
        }</div>
      }
    </section>
  `,
  styles: `
    .taxonomy-page { max-width:1180px; min-height:68vh; margin:0 auto; padding:clamp(2rem,5vw,5rem); }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; }
    .eyebrow span { margin:0 .4rem; color:var(--color-metal); }
    header { display:flex; align-items:end; justify-content:space-between; gap:1rem; padding:1rem 0 1.7rem; border-bottom:1px solid var(--color-line); }
    h1 { margin:0; font:500 clamp(2.5rem,5vw,4.5rem)/1 var(--font-display); }
    header p { margin:0; color:var(--color-muted); font-size:.75rem; }
    .entry-list { display:grid; }
    .entry { display:grid; grid-template-columns:50px 1fr 30px; align-items:center; gap:1rem; padding:1.45rem .4rem; border-bottom:1px solid var(--color-line); text-decoration:none; transition:background .2s ease,padding .2s ease; }
    .entry:hover { padding-right:1rem; padding-left:1rem; background:#edf0e9; }
    .entry-index { color:var(--color-metal); font:500 .8rem var(--font-display); }
    .entry h2 { margin:0; font:500 1.6rem var(--font-display); }
    .entry p { max-width:72ch; margin:.3rem 0 0; color:var(--color-muted); font-size:.75rem; line-height:1.6; }
    .arrow { justify-self:end; color:var(--color-olive); }
    @media(max-width:540px) { .taxonomy-page { padding:2rem 1.1rem; } header { align-items:start; flex-direction:column; } .entry { grid-template-columns:28px 1fr 20px; gap:.55rem; padding:1.1rem 0; } .entry h2 { font-size:1.35rem; } }
    @media(prefers-reduced-motion:reduce) { .entry { transition:none; } }
  `,
})
export class CatalogueTaxonomyComponent implements OnInit {
  private readonly api = inject(CatalogueApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly kind = signal<'brands' | 'categories'>('brands');
  protected readonly entries = signal<(BrandSummary | CategorySummary)[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    const kind = this.route.snapshot.data['catalogueType'];
    this.kind.set(kind === 'categories' ? 'categories' : 'brands');
    const request = this.kind() === 'brands' ? this.api.brands() : this.api.categories();
    request.subscribe({
      next: (result) => { this.entries.set(result.content); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger les références. Réessayez plus tard.'); this.loading.set(false); },
    });
  }
}
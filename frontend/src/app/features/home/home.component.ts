import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogueApiService, CategorySummary } from '../../core/services/catalogue-api.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, ReactiveFormsModule],
  template: `
    <section class="hero">
      <img class="hero-photo" src="https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=2400&q=88" alt="Voiture de grand tourisme, hero Elite Tunisia" fetchpriority="high">
      <div class="hero-wash"></div>
      <div class="hero-content">
        <p class="eyebrow">ELITE TUNISIA · LOCATION DE PRESTIGE</p>
        <h1>Une mobilité<br><em>d’exception.</em></h1>
                <p class="hero-copy">Une sélection de véhicules premium pour découvrir la Tunisie, avec une disponibilité vérifiée pour vos dates.</p>
        <div class="hero-actions"><a class="button button-light" routerLink="/vehicles">Voir la flotte <span aria-hidden="true">↗</span></a><a class="text-link" routerLink="/contact">Demander un devis <span aria-hidden="true">↗</span></a></div>
      </div>
    </section>
    <section class="search-section" aria-labelledby="search-title">
      <div><p class="eyebrow">RECHERCHE</p><h2 id="search-title">Trouver un véhicule</h2></div>
      <form class="search-form" [formGroup]="form" (ngSubmit)="search()">
        <label><span>Modèle ou marque</span><input formControlName="search" placeholder="Ex. Porsche" autocomplete="off"></label>
        <label><span>Catégorie</span><select formControlName="category"><option value="">Toutes les catégories</option>@for (category of categories(); track category.id) { <option [value]="category.slug">{{ category.name }}</option> }</select></label>
        <button class="button button-dark" type="submit">Rechercher</button>
      </form>
    </section>
    <section class="categories-section" aria-labelledby="categories-title">
      <div class="section-heading"><div><p class="eyebrow">CATÉGORIES</p><h2 id="categories-title">Choisir une <em>catégorie.</em></h2></div><a class="text-link dark-link" routerLink="/vehicles">Toute la flotte <span aria-hidden="true">↗</span></a></div>
      @if (categoriesState() === 'loading') { <p class="notice" role="status">Chargement des catégories…</p> }
      @if (categoriesState() === 'error') { <p class="notice" role="status">Les catégories ne sont pas disponibles pour le moment. Vous pouvez consulter la flotte complète.</p> }
      @if (categoriesState() === 'ready' && categories().length === 0) { <p class="notice">Aucune catégorie n’est publiée pour le moment.</p> }
      <div class="category-grid">
        @for (category of categories(); track category.id) {
          <a class="category-item" routerLink="/vehicles" [queryParams]="{ category: category.slug }"><span>{{ category.name }}</span>@if (category.description) { <small>{{ category.description }}</small> }<span aria-hidden="true">↗</span></a>
        }
      </div>
    </section>
    <section class="intro-section">
      <div class="intro-index">01 <span>/</span> L’AGENCE</div>
      <div class="intro-copy"><p class="eyebrow">UNE LOCATION PENSÉE AVEC SOIN</p><h2>Chaque trajet,<br><em>préparé.</em></h2><p>Elite Tunisia propose une flotte de véhicules premium pour vos déplacements en Tunisie. Chaque véhicule est présenté avec ses caractéristiques réelles, et sa disponibilité est vérifiée auprès de notre système pour les dates que vous choisissez.</p></div>
      <div class="intro-mark" aria-hidden="true">ET</div>
    </section>
    <section class="services-section">
      <div class="section-heading"><div><p class="eyebrow">NOS SERVICES</p><h2>Choisissez <em>votre formule.</em></h2></div><a class="text-link dark-link" routerLink="/services">Tous nos services <span aria-hidden="true">↗</span></a></div>
      <div class="service-grid">
        <a class="service-item" routerLink="/vehicles"><span class="service-number">01</span><div><h3>Location libre-service</h3><p>Conduisez le véhicule de votre choix pendant toute la durée de votre séjour.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
        <a class="service-item" routerLink="/chauffeur"><span class="service-number">02</span><div><h3>Chauffeur privé</h3><p>Un service discret, organisé autour de votre programme.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
        <a class="service-item" routerLink="/airport-transfer"><span class="service-number">03</span><div><h3>Transfert aéroport</h3><p>Une prise en charge à l’arrivée, jusqu’à votre destination.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
      </div>
    </section>
    <section class="closing-section"><p class="eyebrow">VOTRE PROCHAIN TRAJET COMMENCE ICI</p><h2>Prenez la route<br><em>autrement.</em></h2><a class="button button-dark" routerLink="/vehicles">Explorer la flotte <span aria-hidden="true">↗</span></a></section>
  `,
  styles: `
    :host { display:block; }
    .hero { position:relative; display:flex; min-height:min(700px,calc(100vh - 86px)); align-items:center; overflow:hidden; background:#1d2925; color:#fff; }
    .hero-photo, .hero-wash { position:absolute; inset:0; width:100%; height:100%; }
    .hero-photo { object-fit:cover; object-position:center 58%; animation:hero-arrive 1.2s ease-out both; }
    .hero-wash { background:linear-gradient(90deg,rgba(15,24,21,.82) 0%,rgba(15,24,21,.49) 48%,rgba(15,24,21,.08) 100%),linear-gradient(0deg,rgba(15,24,21,.42),transparent 24%); }
    .hero-content { position:relative; z-index:1; width:min(100%,1200px); margin:0 auto; padding:5rem clamp(1.5rem,8vw,8rem); animation:copy-arrive .8s .1s ease-out both; }
    .eyebrow { margin:0; color:#d1bc8d; font-size:.7rem; font-weight:700; letter-spacing:.16em; }
    h1, h2, h3, p { margin-top:0; }
    h1 { margin:1.3rem 0 1.2rem; font:500 clamp(3.7rem,8.2vw,7.2rem)/.98 var(--font-display); }
    h1 em, h2 em { color:#d1bc8d; font-weight:500; }
    .hero-copy { color:rgba(255,255,255,.82); font-size:1rem; line-height:1.8; }
    .hero-actions { display:flex; align-items:center; gap:2rem; margin-top:2rem; }
    .button { display:inline-flex; align-items:center; justify-content:center; gap:2rem; min-height:50px; padding:0 1.15rem; font-size:.8rem; font-weight:600; text-decoration:none; transition:background .2s,color .2s,transform .2s; }
    .button:hover { transform:translateY(-2px); }
    .button-light { background:#f6f5f1; color:#202720; }
    .text-link { color:#fff; font-size:.82rem; text-decoration:none; }
    .text-link span, .button span { color:#b5975d; }
    .intro-section { position:relative; display:grid; max-width:1200px; min-height:460px; grid-template-columns:1fr 2fr 1fr; align-items:center; margin:0 auto; padding:6rem clamp(1.5rem,7vw,7rem); }
    .intro-index { align-self:start; color:var(--color-muted); font-size:.68rem; letter-spacing:.1em; }
    .intro-index span { margin:0 .5rem; color:var(--color-metal); }
    .intro-copy { max-width:560px; }
    .intro-copy .eyebrow, .section-heading .eyebrow, .closing-section .eyebrow { color:var(--color-olive); }
    h2 { margin:1rem 0; font:500 clamp(2.7rem,5.5vw,4.8rem)/1.03 var(--font-display); }
    .intro-copy h2 em, .section-heading h2 em, .closing-section h2 em { color:var(--color-olive); }
    .intro-copy > p:last-child { max-width:49ch; color:var(--color-muted); font-size:.9rem; line-height:1.9; }
    .intro-mark { justify-self:end; color:rgba(181,151,93,.24); font:500 5rem var(--font-display); }
    .services-section { padding:5.5rem clamp(1.5rem,7vw,7rem); background:#e9ece5; }
    .section-heading { display:flex; max-width:1060px; align-items:end; justify-content:space-between; margin:0 auto 2.5rem; }
    .section-heading h2 { margin-bottom:0; }
    .dark-link { color:var(--color-ink); }
    .service-grid { display:grid; max-width:1060px; grid-template-columns:repeat(3,minmax(0,1fr)); margin:0 auto; border-top:1px solid #c9cec5; }
    .service-item { display:grid; min-height:205px; grid-template-columns:30px 1fr 18px; align-items:start; gap:1rem; padding:1.6rem 1.2rem 1.6rem 0; border-bottom:1px solid #c9cec5; color:inherit; text-decoration:none; transition:background .2s; }
    .service-item + .service-item { padding-left:1.2rem; border-left:1px solid #c9cec5; }
    .service-item:hover { background:rgba(255,255,255,.34); }
    .service-number { color:var(--color-metal); font-size:.68rem; }
    .service-item h3 { margin:.2rem 0 .7rem; font:500 1.4rem var(--font-display); }
    .service-item p { max-width:25ch; margin:0; color:var(--color-muted); font-size:.8rem; line-height:1.7; }
    .service-arrow { color:var(--color-olive); }
    .closing-section { display:flex; min-height:400px; flex-direction:column; align-items:center; justify-content:center; padding:5rem 1.5rem; text-align:center; }
    .closing-section h2 { margin:1rem 0 1.6rem; }
    .button-dark { background:var(--color-ink); color:#fff; }
    .button-dark:hover { background:var(--color-olive); }
    .search-section { position:relative; z-index:2; display:flex; max-width:1060px; flex-wrap:wrap; align-items:end; justify-content:space-between; gap:1.5rem; margin:-3rem auto 0; padding:1.8rem; background:#fff; box-shadow:0 18px 40px rgba(31,39,33,.12); }
    .search-section h2 { margin:.4rem 0 0; font:500 1.6rem var(--font-display); }
    .search-form { display:grid; flex:1 1 560px; grid-template-columns:1fr 1fr auto; gap:.8rem; align-items:end; }
    .search-form label { display:grid; gap:.4rem; color:var(--color-muted); font-size:.7rem; letter-spacing:.06em; }
    .search-form input, .search-form select { min-height:46px; padding:0 .8rem; border:1px solid #c9cec5; background:#f8f8f5; color:var(--color-ink); font:inherit; }
    .search-form .button { min-height:46px; padding:0 1.4rem; }
    .categories-section { max-width:1060px; margin:0 auto; padding:5rem clamp(1.5rem,7vw,7rem) 1rem; }
    .categories-section .section-heading { margin-bottom:0; }
    .category-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:1px; margin-top:2rem; border:1px solid #c9cec5; background:#c9cec5; }
    .category-item { display:grid; gap:.5rem; min-height:150px; padding:1.4rem; background:var(--color-paper); color:var(--color-ink); text-decoration:none; transition:background .2s; }
    .category-item:hover { background:#e9ece5; }
    .category-item > span:first-child { font:500 1.4rem var(--font-display); }
    .category-item > span:last-child { color:var(--color-olive); }
    .category-item small { color:var(--color-muted); font-size:.8rem; line-height:1.6; }
    .notice { color:var(--color-muted); font-size:.85rem; }
    .services-section .section-heading { margin-top:0; }
    @keyframes hero-arrive { from { transform:scale(1.035); opacity:.72; } to { transform:scale(1); opacity:1; } }
    @keyframes copy-arrive { from { transform:translateY(16px); opacity:0; } to { transform:translateY(0); opacity:1; } }
    @media(max-width:760px) {
      .hero { min-height:600px; min-height:calc(86svh - 72px); }
      .hero-photo { object-position:58% center; }
      .hero-wash { background:linear-gradient(90deg,rgba(15,24,21,.72),rgba(15,24,21,.2)),linear-gradient(0deg,rgba(15,24,21,.45),transparent 45%); }
      .hero-content { padding:4rem 1.5rem 6rem; }
      h1 { font-size:clamp(3.2rem,15vw,5.5rem); }
      .hero-copy { max-width:31ch; }
      .desktop-break { display:none; }
      .hero-actions { align-items:flex-start; flex-direction:column; gap:1.2rem; }
      .search-form { grid-template-columns:1fr; }
      .search-section { margin-top:-2rem; padding:1.4rem; }
      .category-item { min-height:120px; }
      .intro-section { min-height:0; grid-template-columns:1fr; gap:2rem; padding-top:4.5rem; padding-bottom:4.5rem; }
      .intro-mark { display:none; }
      .services-section { padding-top:4rem; padding-bottom:4rem; }
      .section-heading { align-items:flex-start; flex-direction:column; gap:1rem; }
      .service-grid { grid-template-columns:1fr; }
      .service-item, .service-item + .service-item { min-height:138px; padding:1.25rem 0; border-left:0; }
      .closing-section { min-height:350px; }
    }
    @media(prefers-reduced-motion:reduce) { *,*::before,*::after { animation-duration:.01ms!important; animation-iteration-count:1!important; scroll-behavior:auto!important; } }
  `,
})
export class HomeComponent {
  private readonly builder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly catalogue = inject(CatalogueApiService);

  protected readonly form = this.builder.nonNullable.group({ search: '', category: '' });
  protected readonly categories = signal<CategorySummary[]>([]);
  protected readonly categoriesState = signal<'loading' | 'ready' | 'error'>('loading');

  constructor() {
    this.catalogue.categories().subscribe({
      next: (page) => {
        this.categories.set(page.content);
        this.categoriesState.set('ready');
      },
      error: () => this.categoriesState.set('error'),
    });
  }

  protected search(): void {
    const { search, category } = this.form.getRawValue();
    void this.router.navigate(['/vehicles'], {
      queryParams: { search: search.trim() || null, category: category || null },
    });
  }
}
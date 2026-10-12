import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-public-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header class="site-header">
      <a class="wordmark" routerLink="/" aria-label="Elite Tunisia, accueil"><span class="wordmark-symbol">E</span><span>ELITE <b>TUNISIA</b></span></a>
      <button class="menu-toggle icon-button" type="button" aria-label="Ouvrir le menu" [attr.aria-expanded]="menuOpen()" (click)="menuOpen.update(value => !value)"><span></span><span></span></button>
      <nav class="main-nav" [class.is-open]="menuOpen()" aria-label="Navigation principale">
        <a routerLink="/vehicles" routerLinkActive="active" (click)="menuOpen.set(false)">La flotte</a>
        <a routerLink="/chauffeur" routerLinkActive="active" (click)="menuOpen.set(false)">Chauffeur</a>
        <a routerLink="/airport-transfer" routerLinkActive="active" (click)="menuOpen.set(false)">Transferts</a>
        <a routerLink="/about" routerLinkActive="active" (click)="menuOpen.set(false)">La maison</a>
      </nav>
      <a class="header-link" routerLink="/auth/login">Espace client <span aria-hidden="true">↗</span></a>
    </header>
    <main><router-outlet /></main>
    <footer class="site-footer">
      <a class="wordmark footer-wordmark" routerLink="/"><span class="wordmark-symbol">E</span><span>ELITE <b>TUNISIA</b></span></a>
      <p>Luxury Mobility &amp; Premium Car Rental in Tunisia</p>
      <a routerLink="/contact">Contact <span aria-hidden="true">↗</span></a>
    </footer>
  `,
  styles: `
    .site-header { position: relative; z-index: 5; display: flex; align-items: center; justify-content: space-between; min-height: 86px; padding: 0 clamp(1.25rem, 5.8vw, 6.5rem); border-bottom: 1px solid rgba(31, 39, 33, .12); background: var(--color-paper); }
    .wordmark { display: inline-flex; align-items: center; gap: .7rem; color: var(--color-ink); font-size: .73rem; font-weight: 700; letter-spacing: .09em; text-decoration: none; }
    .wordmark b { color: var(--color-olive); font-weight: 500; }
    .wordmark-symbol { display: grid; width: 2.35rem; aspect-ratio: 1; place-items: center; border: 1px solid var(--color-metal); color: var(--color-metal); font: 600 1.45rem/1 var(--font-display); }
    .main-nav { display: flex; align-items: center; gap: clamp(1.1rem, 2.5vw, 2.8rem); }
    .main-nav a, .header-link, .site-footer > a:last-child { color: var(--color-ink); font-size: .8rem; text-decoration: none; }
    .main-nav a { padding: .5rem 0; color: var(--color-muted); }
    .main-nav a.active, .main-nav a:hover { color: var(--color-ink); }
    .header-link { padding: .8rem 0 .8rem 1rem; border-left: 1px solid var(--color-line); }
    .header-link span, .site-footer > a:last-child span { margin-left: .5rem; color: var(--color-metal); }
    .menu-toggle { display: none; }
    .site-footer { display: flex; align-items: center; justify-content: space-between; gap: 1rem; min-height: 100px; padding: 1.5rem clamp(1.25rem, 5.8vw, 6.5rem); border-top: 1px solid var(--color-line); }
    .site-footer p { color: var(--color-muted); font-size: .78rem; }
    @media (max-width: 800px) {
      .site-header { min-height: 72px; }
      .menu-toggle { display: grid; gap: 5px; width: 2.7rem; aspect-ratio: 1; place-content: center; border: 1px solid var(--color-line); background: transparent; }
      .menu-toggle span { display: block; width: 1.1rem; height: 1px; background: var(--color-ink); }
      .main-nav { position: absolute; top: 100%; right: 0; left: 0; display: none; align-items: stretch; gap: 0; padding: .5rem 1.25rem 1.25rem; border-bottom: 1px solid var(--color-line); background: var(--color-paper); }
      .main-nav.is-open { display: flex; flex-direction: column; }
      .main-nav a { padding: .9rem .2rem; border-bottom: 1px solid var(--color-line); }
      .header-link { display: none; }
      .site-footer { align-items: flex-start; flex-direction: column; padding-top: 2rem; padding-bottom: 2rem; }
      .site-footer p { margin: 0; }
    }
  `,
})
export class PublicLayoutComponent {
  protected readonly menuOpen = signal(false);
}
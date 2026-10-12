import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-customer-layout',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="workspace-header"><a class="wordmark" routerLink="/"><span class="wordmark-symbol">E</span><span>ELITE <b>TUNISIA</b></span></a><div class="header-actions"><a routerLink="/">Retour au site <span aria-hidden="true">↗</span></a><button type="button" (click)="logout()">Déconnexion</button></div></header>
    <div class="workspace"><aside class="workspace-nav"><p class="eyebrow">ESPACE CLIENT</p><a routerLink="/customer/dashboard">Vue d’ensemble</a><a routerLink="/customer/bookings">Réservations</a><a routerLink="/customer/quotes">Devis</a><a routerLink="/customer/invoices">Factures</a><a routerLink="/customer/profile">Profil</a><a routerLink="/customer/notifications">Notifications</a></aside><main class="workspace-main"><router-outlet /></main></div>
  `,
  styles: `
    .workspace-header { display:flex; align-items:center; justify-content:space-between; min-height:76px; padding:0 clamp(1rem,4vw,4rem); border-bottom:1px solid var(--color-line); background:var(--color-paper); }
    .wordmark { display:inline-flex; align-items:center; gap:.7rem; color:var(--color-ink); font-size:.73rem; font-weight:700; letter-spacing:.09em; text-decoration:none; }
    .wordmark-symbol { display:grid; width:2.25rem; aspect-ratio:1; place-items:center; border:1px solid var(--color-metal); color:var(--color-metal); font:600 1.4rem var(--font-display); }
    .wordmark b { color:var(--color-olive); font-weight:500; }
    .workspace-header > a:last-child { color:var(--color-muted); font-size:.8rem; text-decoration:none; }
    .header-actions { display:flex; align-items:center; gap:1rem; }
    .header-actions button { padding:.55rem .75rem; border:1px solid var(--color-line); background:transparent; color:var(--color-ink); cursor:pointer; font-size:.74rem; }
    .workspace { display:grid; min-height:calc(100vh - 77px); grid-template-columns:230px minmax(0,1fr); }
    .workspace-nav { display:flex; flex-direction:column; gap:.2rem; padding:2rem 1rem; border-right:1px solid var(--color-line); background:#f0f0eb; }
    .eyebrow { margin:0 0 1rem .75rem; color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .workspace-nav a { padding:.75rem; color:var(--color-muted); font-size:.82rem; text-decoration:none; }
    .workspace-nav a:hover { background:rgba(47,67,52,.08); color:var(--color-ink); }
    .workspace-main { min-width:0; }
    @media(max-width:760px) { .workspace { display:block; } .workspace-nav { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); padding:1rem; border-right:0; border-bottom:1px solid var(--color-line); } .eyebrow { grid-column:1/-1; } }
  `,
})
export class CustomerLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigate(['/']) });
  }
}
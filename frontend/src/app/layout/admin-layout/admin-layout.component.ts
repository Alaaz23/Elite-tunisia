import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="admin-header"><a class="wordmark" routerLink="/"><span class="wordmark-symbol">E</span><span>ELITE <b>TUNISIA</b></span></a><span class="workspace-label">OPÉRATIONS</span><div class="header-actions"><a routerLink="/">Site public <span aria-hidden="true">↗</span></a><button type="button" (click)="logout()">Déconnexion</button></div></header>
    <div class="admin-shell"><aside class="admin-nav"><p class="eyebrow">GESTION</p><a routerLink="/admin">Vue d’ensemble</a><a routerLink="/admin/users">Utilisateurs</a><a routerLink="/admin/vehicles">Véhicules</a><a routerLink="/admin/brands">Marques</a><a routerLink="/admin/categories">Catégories</a><a routerLink="/admin/bookings">Réservations</a><a routerLink="/admin/customers">Clients</a><a routerLink="/admin/drivers">Chauffeurs</a><a routerLink="/admin/quotes">Devis</a><a routerLink="/admin/services">Services</a><a routerLink="/admin/invoices">Factures</a><a routerLink="/admin/reports">Rapports</a></aside><main class="admin-main"><router-outlet /></main></div>
  `,
  styles: `
    .admin-header { display:flex; align-items:center; gap:1.5rem; min-height:72px; padding:0 clamp(1rem,3vw,3rem); border-bottom:1px solid var(--color-line); background:var(--color-paper); }
    .wordmark { display:inline-flex; align-items:center; gap:.7rem; color:var(--color-ink); font-size:.73rem; font-weight:700; letter-spacing:.09em; text-decoration:none; }
    .wordmark-symbol { display:grid; width:2.2rem; aspect-ratio:1; place-items:center; border:1px solid var(--color-metal); color:var(--color-metal); font:600 1.4rem var(--font-display); }
    .wordmark b { color:var(--color-olive); font-weight:500; }
    .workspace-label { padding:.4rem .6rem; border-left:1px solid var(--color-line); color:var(--color-olive); font-size:.67rem; font-weight:700; letter-spacing:.11em; }
    .header-actions { display:flex; align-items:center; gap:1rem; margin-left:auto; }
    .header-actions a { color:var(--color-muted); font-size:.78rem; text-decoration:none; }
    .header-actions button { padding:.55rem .75rem; border:1px solid var(--color-line); background:transparent; color:var(--color-ink); cursor:pointer; font-size:.74rem; }
    .admin-shell { display:grid; min-height:calc(100vh - 73px); grid-template-columns:220px minmax(0,1fr); }
    .admin-nav { display:flex; flex-direction:column; gap:.15rem; padding:1.75rem .85rem; border-right:1px solid var(--color-line); background:#eeefea; }
    .eyebrow { margin:0 0 .8rem .7rem; color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .admin-nav a { padding:.72rem .7rem; color:var(--color-muted); font-size:.8rem; text-decoration:none; }
    .admin-nav a:hover { background:rgba(47,67,52,.08); color:var(--color-ink); }
    .admin-main { min-width:0; }
    @media(max-width:760px) { .admin-shell { display:block; } .admin-nav { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); padding:.8rem; border-right:0; border-bottom:1px solid var(--color-line); } .eyebrow { grid-column:1/-1; } }
    @media(max-width:560px) { .admin-header { flex-wrap:wrap; gap:.75rem; padding:.8rem 1rem; } .header-actions { width:100%; justify-content:flex-end; } }
  `,
})
export class AdminLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigate(['/']) });
  }
}
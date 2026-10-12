import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-customer-dashboard',
  imports: [RouterLink],
  template: `
    <section class="customer-dashboard" aria-labelledby="dashboard-title">
      <p class="eyebrow">ESPACE CLIENT <span>/</span> VUE D’ENSEMBLE</p>
      <h1 id="dashboard-title">Bonjour, <em>{{ auth.session()?.firstName }}</em></h1>
      <p class="intro">Votre espace personnel Elite Tunisia.</p>
      <div class="account-strip"><span class="status-dot" aria-hidden="true"></span><span>Compte {{ auth.session()?.status === 'ACTIVE' ? 'actif' : 'désactivé' }}</span><span class="strip-divider"></span><span>{{ auth.session()?.email }}</span></div>
      <div class="account-links">
        <a routerLink="/customer/profile"><span class="link-index">01</span><span><strong>Mon profil</strong><small>Vos coordonnées et informations personnelles</small></span><span class="link-arrow" aria-hidden="true">↗</span></a>
        <a routerLink="/customer/bookings"><span class="link-index">02</span><span><strong>Mes réservations</strong><small>Retrouvez vos demandes et prochains départs</small></span><span class="link-arrow" aria-hidden="true">↗</span></a>
        <a routerLink="/customer/quotes"><span class="link-index">03</span><span><strong>Mes devis</strong><small>Suivez les propositions préparées pour vous</small></span><span class="link-arrow" aria-hidden="true">↗</span></a>
      </div>
    </section>
  `,
  styles: `
    .customer-dashboard { max-width:1000px; margin:0 auto; padding:clamp(2rem,6vw,5rem); }
    .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.12em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); }
    h1 { margin:1.2rem 0 .55rem; font:500 clamp(2.7rem,6vw,5rem)/1.02 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .intro { color:var(--color-muted); font-size:.9rem; }
    .account-strip { display:flex; align-items:center; gap:.65rem; margin:2.3rem 0 2rem; padding:1rem 0; border-top:1px solid var(--color-line); border-bottom:1px solid var(--color-line); color:var(--color-muted); font-size:.78rem; }
    .status-dot { width:.48rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .strip-divider { height:1rem; margin:0 .4rem; border-left:1px solid var(--color-line); }
    .account-links { border-top:1px solid var(--color-line); }
    .account-links a { display:grid; min-height:100px; grid-template-columns:40px 1fr 24px; align-items:center; gap:1rem; border-bottom:1px solid var(--color-line); color:var(--color-ink); text-decoration:none; }
    .link-index { align-self:start; padding-top:1.7rem; color:var(--color-metal); font-size:.68rem; }
    strong, small { display:block; }
    strong { font:500 1.25rem var(--font-display); }
    small { margin-top:.3rem; color:var(--color-muted); font-size:.76rem; }
    .link-arrow { color:var(--color-olive); }
    @media(max-width:520px) { .customer-dashboard { padding:2rem 1.25rem; } .account-strip { align-items:flex-start; flex-wrap:wrap; } }
  `,
})
export class CustomerDashboardComponent {
  protected readonly auth = inject(AuthService);
}

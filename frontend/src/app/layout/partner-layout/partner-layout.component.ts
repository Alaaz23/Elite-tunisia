import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-partner-layout',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="workspace-header"><a class="wordmark" routerLink="/"><span class="wordmark-symbol">E</span><span>ELITE <b>TUNISIA</b></span></a><span class="workspace-label">PARTENAIRE</span><div class="header-actions"><button type="button" (click)="logout()">Déconnexion</button></div></header>
    <div class="partner-shell"><main class="partner-main"><router-outlet /></main></div>
  `,
  styles: `
    .workspace-header { display:flex; align-items:center; gap:1.5rem; min-height:72px; padding:0 clamp(1rem,3vw,3rem); border-bottom:1px solid var(--color-line); background:var(--color-paper); }
    .wordmark { display:inline-flex; align-items:center; gap:.7rem; color:var(--color-ink); font-size:.73rem; font-weight:700; letter-spacing:.09em; text-decoration:none; }
    .wordmark-symbol { display:grid; width:2.2rem; aspect-ratio:1; place-items:center; border:1px solid var(--color-metal); color:var(--color-metal); font:600 1.4rem var(--font-display); }
    .wordmark b { color:var(--color-olive); font-weight:500; }
    .workspace-label { padding:.4rem .6rem; border-left:1px solid var(--color-line); color:var(--color-olive); font-size:.67rem; font-weight:700; letter-spacing:.11em; }
    .header-actions { display:flex; align-items:center; gap:1rem; margin-left:auto; }
    .header-actions button { padding:.55rem .75rem; border:1px solid var(--color-line); background:transparent; color:var(--color-ink); cursor:pointer; font-size:.74rem; }
    .partner-shell { min-height:calc(100vh - 73px); }
    .partner-main { min-width:0; }
    @media(max-width:560px) { .workspace-header { flex-wrap:wrap; gap:.75rem; padding:.8rem 1rem; } .header-actions { width:100%; justify-content:flex-end; } }
  `,
})
export class PartnerLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigate(['/']) });
  }
}

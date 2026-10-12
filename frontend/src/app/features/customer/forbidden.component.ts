import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink],
  template: `<section class="forbidden-page"><p class="eyebrow">ACCÈS RESTREINT</p><h1>Cette page n’est<br><em>pas pour vous.</em></h1><p>Votre compte ne dispose pas des droits nécessaires.</p><a class="button button-dark" routerLink="/">Retour à l’accueil <span aria-hidden="true">↗</span></a>@if (auth.session()) { <button class="logout-button" type="button" (click)="logout()">Déconnexion</button> }</section>`,
  styles: `
    .forbidden-page { max-width:800px; min-height:55vh; margin:0 auto; padding:clamp(4rem,12vw,8rem) 1.5rem; }
    .eyebrow { color:var(--color-olive); font-size:.7rem; font-weight:700; letter-spacing:.13em; }
    h1 { margin:1rem 0; font:500 clamp(3rem,8vw,6rem)/1 var(--font-display); }
    h1 em { color:var(--color-olive); }
    p:not(.eyebrow) { margin-bottom:1.7rem; color:var(--color-muted); }
    .button { display:inline-flex; min-height:48px; align-items:center; gap:1.5rem; padding:0 1rem; background:var(--color-ink); color:#fff; text-decoration:none; }
    .logout-button { margin-left:1rem; min-height:48px; padding:0 1rem; border:1px solid var(--color-line); background:transparent; color:var(--color-ink); cursor:pointer; }
  `,
})
export class ForbiddenComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigate(['/']) });
  }
}

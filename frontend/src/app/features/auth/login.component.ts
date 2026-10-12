import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiHttpError } from '../../core/models/api-http-error';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-page" aria-labelledby="login-title">
      <div class="auth-aside"><p class="eyebrow">ELITE TUNISIA</p><p class="aside-copy">A more personal<br><em>way to travel.</em></p><span class="aside-mark" aria-hidden="true">ET</span></div>
      <div class="auth-content">
        <p class="eyebrow">{{ adminMode() ? 'ACCÈS AGENCE' : 'ESPACE CLIENT' }}</p>
        <h1 id="login-title">{{ adminMode() ? 'Connexion équipe.' : 'Ravi de vous' }}<br><em>{{ adminMode() ? 'Elite Tunisia.' : 'retrouver.' }}</em></h1>
        <p class="intro">{{ adminMode() ? 'Connectez-vous avec votre compte interne.' : 'Connectez-vous à votre espace sécurisé.' }}</p>
        @if (errorMessage()) { <p class="alert alert-error" role="alert">{{ errorMessage() }}</p> }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field"><label for="login-email">Adresse e-mail</label><input id="login-email" type="email" formControlName="email" autocomplete="email" required [attr.aria-invalid]="form.controls.email.invalid && form.controls.email.touched"><span class="field-error">{{ fieldError('email') }}</span></div>
          <div class="field"><label for="login-password">Mot de passe</label><input id="login-password" type="password" formControlName="password" autocomplete="current-password" required [attr.aria-invalid]="form.controls.password.invalid && form.controls.password.touched"><span class="field-error">{{ fieldError('password') }}</span></div>
          <div class="form-meta"><a routerLink="/auth/forgot-password">Mot de passe oublié ?</a></div>
          <button class="button button-dark submit-button" type="submit" [disabled]="submitting()">{{ submitting() ? 'Connexion…' : 'Se connecter' }} <span aria-hidden="true">↗</span></button>
        </form>
        @if (!adminMode()) { <p class="auth-switch">Vous découvrez Elite Tunisia ? <a routerLink="/auth/register">Créer un compte</a></p><p class="admin-entry"><a routerLink="/auth/admin-login">Accès équipe agence</a></p> }
      </div>
    </section>
  `,
  styles: `
    .eyebrow { margin:0; color:var(--color-olive); font-size:.7rem; font-weight:700; letter-spacing:.13em; }
    .auth-aside .eyebrow { color:#d1bc8d; }
    .aside-copy { position:relative; z-index:1; margin:0; font:500 clamp(2.5rem,4vw,4.4rem)/1.08 var(--font-display); }
    .aside-copy em { color:#d1bc8d; }
    .aside-mark { position:relative; z-index:1; color:rgba(213,192,146,.62); font:500 1.4rem var(--font-display); }
    .auth-content { width:min(100%,530px); align-self:center; justify-self:center; padding:4rem clamp(1.5rem,6vw,5rem); }
    h1 { margin:1rem 0 .8rem; font:500 clamp(2.8rem,5vw,4.6rem)/1.03 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .intro { margin-bottom:2rem; color:var(--color-muted); font-size:.9rem; }
    form { display:grid; gap:1.1rem; }
    .field { display:grid; gap:.4rem; }
    .field label { font-size:.77rem; font-weight:600; }
    .field input { width:100%; min-height:48px; padding:.7rem .8rem; border:1px solid var(--color-line); background:white; }
    .field input[aria-invalid='true'] { border-color:#a2443d; }
    .field-error { min-height:1rem; color:#a2443d; font-size:.72rem; }
    .form-meta { display:flex; justify-content:flex-end; margin-top:-.3rem; }
    .form-meta a, .auth-switch a, .admin-entry a { color:var(--color-olive); font-size:.78rem; font-weight:600; text-decoration:none; }
    .submit-button { width:100%; min-height:52px; margin-top:.25rem; }
    .auth-switch { margin:1.7rem 0 0; color:var(--color-muted); font-size:.8rem; text-align:center; }
    .auth-switch a { margin-left:.3rem; }
    .admin-entry { margin:1rem 0 0; text-align:center; }
    .admin-entry a { color:var(--color-muted); font-weight:500; }
    @media(max-width:760px) { .auth-page { grid-template-columns:1fr; } .auth-aside { min-height:180px; padding:1.5rem; } .aside-copy { font-size:2rem; } .aside-mark { display:none; } .auth-content { width:100%; padding:3rem 1.5rem 4rem; } }
  `,
})
export class LoginComponent {
  private readonly builder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly adminMode = signal(this.route.snapshot.data['adminMode'] === true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly form = this.builder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    this.errorMessage.set('');
    this.submitting.set(true);
    this.auth.login(this.form.controls.email.value, this.form.controls.password.value).subscribe({
      next: (session) => {
        if (this.adminMode() && session.roles.includes('CUSTOMER')) {
          this.errorMessage.set('Ce compte ne dispose pas des droits nécessaires pour accéder au back-office.');
          void this.router.navigate(['/forbidden']);
          return;
        }
        const target = this.route.snapshot.queryParamMap.get('returnUrl');
        const defaultTarget = session.roles.includes('CUSTOMER') ? '/customer/dashboard' : '/admin';
        void this.router.navigateByUrl(target?.startsWith('/') ? target : defaultTarget);
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof ApiHttpError && error.status === 401
          ? error.message.includes('désactivé') ? 'Votre compte a été désactivé.' : 'Email ou mot de passe incorrect.'
          : error instanceof Error ? error.message : 'Une erreur est survenue. Veuillez réessayer.');
        this.submitting.set(false);
      },
    });
  }

  protected fieldError(field: 'email' | 'password'): string {
    const control = this.form.controls[field];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'Ce champ est obligatoire.';
    if (control.hasError('email')) return 'Saisissez une adresse e-mail valide.';
    if (control.hasError('maxlength')) return 'Cette adresse e-mail est trop longue.';
    return '';
  }
}
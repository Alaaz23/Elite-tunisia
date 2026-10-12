import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiHttpError } from '../../core/models/api-http-error';

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return password === confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="register-page" aria-labelledby="register-title">
      <div class="register-heading"><p class="eyebrow">REJOINDRE ELITE TUNISIA</p><h1 id="register-title">Le voyage commence<br><em>avec vous.</em></h1><p>Créez votre espace personnel pour préparer vos prochaines escapades.</p></div>
      <div class="register-form-wrap">
        @if (successMessage()) { <p class="alert alert-success" role="status">{{ successMessage() }}</p> }
        @if (errorMessage()) { <p class="alert alert-error" role="alert">{{ errorMessage() }}</p> }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="name-grid">
            <div class="field"><label for="first-name">Prénom</label><input id="first-name" formControlName="firstName" autocomplete="given-name" required><span class="field-error">{{ errorFor('firstName') }}</span></div>
            <div class="field"><label for="last-name">Nom</label><input id="last-name" formControlName="lastName" autocomplete="family-name" required><span class="field-error">{{ errorFor('lastName') }}</span></div>
          </div>
          <div class="field"><label for="register-email">Adresse e-mail</label><input id="register-email" type="email" formControlName="email" autocomplete="email" required><span class="field-error">{{ errorFor('email') }}</span></div>
          <div class="field"><label for="register-phone">Téléphone</label><input id="register-phone" type="tel" formControlName="phone" autocomplete="tel" placeholder="+216 20 123 456" required><span class="field-error">{{ errorFor('phone') }}</span></div>
          <div class="field"><label for="register-password">Mot de passe</label><input id="register-password" type="password" formControlName="password" autocomplete="new-password" required><span class="field-hint">12 caractères minimum, avec majuscule, minuscule, chiffre et symbole.</span><span class="field-error">{{ errorFor('password') }}</span></div>
          <div class="field"><label for="confirm-password">Confirmer le mot de passe</label><input id="confirm-password" type="password" formControlName="confirmPassword" autocomplete="new-password" required><span class="field-error">{{ errorFor('confirmPassword') }}</span></div>
          <button class="button button-dark submit-button" type="submit" [disabled]="submitting()">{{ submitting() ? 'Création…' : 'Créer mon compte' }} <span aria-hidden="true">↗</span></button>
        </form>
        <p class="auth-switch">Vous avez déjà un compte ? <a routerLink="/auth/login">Se connecter</a></p>
      </div>
    </section>
  `,
  styles: `
    .register-page { display:grid; max-width:1120px; min-height:calc(100vh - 186px); grid-template-columns:.85fr 1.15fr; gap:clamp(2rem,8vw,8rem); margin:0 auto; padding:clamp(3rem,7vw,6rem) clamp(1.5rem,5vw,4rem); }
    .register-heading { position:sticky; top:3rem; align-self:start; padding-top:1rem; }
    .eyebrow { color:var(--color-olive); font-size:.7rem; font-weight:700; letter-spacing:.13em; }
    h1 { margin:1rem 0; font:500 clamp(2.8rem,5.5vw,5rem)/1.02 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .register-heading > p:last-child { max-width:38ch; color:var(--color-muted); font-size:.9rem; line-height:1.8; }
    .register-form-wrap { width:100%; max-width:560px; }
    form { display:grid; gap:.85rem; }
    .name-grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    .field { display:grid; gap:.4rem; }
    .field label { font-size:.77rem; font-weight:600; }
    .field input { width:100%; min-height:46px; padding:.65rem .75rem; border:1px solid var(--color-line); background:white; }
    .field input.ng-invalid.ng-touched { border-color:#a2443d; }
    .field-hint { color:var(--color-muted); font-size:.68rem; line-height:1.5; }
    .field-error { min-height:.8rem; color:#a2443d; font-size:.7rem; }
    .submit-button { min-height:50px; margin-top:.35rem; }
    .auth-switch { color:var(--color-muted); font-size:.8rem; text-align:center; }
    .auth-switch a { color:var(--color-olive); font-weight:600; text-decoration:none; }
    @media(max-width:760px) { .register-page { grid-template-columns:1fr; gap:1.5rem; padding-top:3rem; } .register-heading { position:static; } }
    @media(max-width:480px) { .name-grid { grid-template-columns:1fr; gap:.85rem; } }
  `,
})
export class RegisterComponent {
  private readonly builder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly form = this.builder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.required, Validators.pattern(/^\+?[0-9][0-9 ()-]{7,38}$/)]],
    password: ['', [Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$/)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    this.errorMessage.set('');
    this.submitting.set(true);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.successMessage.set('Votre compte a été créé.');
        void this.router.navigate(['/customer/dashboard']);
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof ApiHttpError && error.status === 409
          ? 'Cette adresse e-mail est déjà utilisée.'
          : error instanceof Error ? error.message : 'Une erreur est survenue. Veuillez réessayer.');
        this.submitting.set(false);
      },
    });
  }

  protected errorFor(field: 'firstName' | 'lastName' | 'email' | 'phone' | 'password' | 'confirmPassword'): string {
    const control = this.form.controls[field];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'Ce champ est obligatoire.';
    if (control.hasError('email')) return 'Saisissez une adresse e-mail valide.';
    if (control.hasError('pattern')) return field === 'phone' ? 'Saisissez un numéro valide.' : 'Le mot de passe ne respecte pas les critères requis.';
    if (field === 'confirmPassword' && this.form.hasError('passwordMismatch')) return 'La confirmation ne correspond pas.';
    return '';
  }
}
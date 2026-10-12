import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { API_BASE_URL } from '../../core/config/api.config';
import { ApiHttpError } from '../../core/models/api-http-error';

interface CustomerProfile {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  roles: string[];
  status: 'ACTIVE' | 'DISABLED';
}

@Component({
  selector: 'app-customer-profile',
  imports: [ReactiveFormsModule],
  template: `
    <section class="profile-page" aria-labelledby="profile-title">
      <p class="eyebrow">ESPACE CLIENT <span>/</span> MES INFORMATIONS</p>
      <h1 id="profile-title">Mon <em>profil.</em></h1>
      <p class="intro">Modifiez les coordonnées associées à votre compte.</p>
      @if (loading()) { <p class="loading-state" role="status">Chargement du profil…</p> }
      @if (errorMessage()) { <p class="alert alert-error" role="alert">{{ errorMessage() }}</p> }
      @if (successMessage()) { <p class="alert alert-success" role="status">{{ successMessage() }}</p> }
      @if (!loading()) {
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="field"><label for="profile-email">Adresse e-mail</label><input id="profile-email" type="email" [value]="email()" disabled><small>L’adresse e-mail de connexion ne peut pas être modifiée ici.</small></div>
          <div class="name-grid"><div class="field"><label for="profile-first">Prénom</label><input id="profile-first" formControlName="firstName" autocomplete="given-name"><span class="field-error">{{ fieldError('firstName') }}</span></div><div class="field"><label for="profile-last">Nom</label><input id="profile-last" formControlName="lastName" autocomplete="family-name"><span class="field-error">{{ fieldError('lastName') }}</span></div></div>
          <div class="field"><label for="profile-phone">Téléphone</label><input id="profile-phone" type="tel" formControlName="phone" autocomplete="tel"><span class="field-error">{{ fieldError('phone') }}</span></div>
          <button class="button button-dark" type="submit" [disabled]="saving()">{{ saving() ? 'Enregistrement…' : 'Enregistrer les modifications' }} <span aria-hidden="true">↗</span></button>
        </form>
      }
    </section>
  `,
  styles: `
    .profile-page { max-width:760px; margin:0 auto; padding:clamp(2rem,6vw,5rem); }
    .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.12em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); }
    h1 { margin:1.1rem 0 .5rem; font:500 clamp(2.8rem,6vw,4.8rem)/1.02 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .intro { margin-bottom:2rem; color:var(--color-muted); font-size:.88rem; }
    form { display:grid; gap:1.15rem; }
    .field { display:grid; gap:.4rem; }
    label { font-size:.77rem; font-weight:600; }
    input { min-height:46px; padding:.7rem .8rem; border:1px solid var(--color-line); background:#fff; }
    input:disabled { background:#eeefea; color:var(--color-muted); }
    small, .field-error { min-height:.8rem; color:var(--color-muted); font-size:.7rem; }
    .field-error { color:#a2443d; }
    .name-grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    button { justify-self:start; min-width:220px; margin-top:.5rem; }
    @media(max-width:520px) { .profile-page { padding:2rem 1.25rem; } .name-grid { grid-template-columns:1fr; } button { width:100%; } }
  `,
})
export class CustomerProfileComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly builder = inject(FormBuilder);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly email = signal('');
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly form = this.builder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    phone: ['', [Validators.required, Validators.pattern(/^\+?[0-9][0-9 ()-]{7,38}$/)]],
  });

  ngOnInit(): void {
    this.http.get<CustomerProfile>(`${this.baseUrl}/api/customer/profile`).subscribe({
      next: (profile) => {
        this.email.set(profile.email);
        this.form.patchValue({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone });
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof Error ? error.message : 'Impossible de charger le profil.');
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.http.put<CustomerProfile>(`${this.baseUrl}/api/customer/profile`, this.form.getRawValue()).subscribe({
      next: (profile) => {
        this.form.patchValue({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone });
        this.successMessage.set('Votre profil a été mis à jour.');
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof ApiHttpError ? error.message : 'Impossible d’enregistrer le profil.');
        this.saving.set(false);
      },
    });
  }

  protected fieldError(field: 'firstName' | 'lastName' | 'phone'): string {
    const control = this.form.controls[field];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'Ce champ est obligatoire.';
    if (control.hasError('pattern')) return 'Saisissez un numéro de téléphone valide.';
    if (control.hasError('maxlength')) return 'Cette valeur est trop longue.';
    return '';
  }
}

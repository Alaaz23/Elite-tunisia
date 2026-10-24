import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Driver, DriverApiService } from '../../core/services/driver-api.service';
import { ApiHttpError } from '../../core/models/api-http-error';

@Component({
  selector: 'app-admin-drivers',
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <section class="drivers">
      <header><p class="eyebrow">FLOTTE & ÉQUIPE</p><h1>Chauffeurs</h1><p>Gérez les chauffeurs disponibles pour les réservations avec chauffeur.</p></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      @if (loading()) { <p role="status">Chargement des chauffeurs…</p> }
      <form class="editor" [formGroup]="form" (ngSubmit)="create()">
        <h2>Ajouter un chauffeur</h2>
        <div class="fields">
          <label class="field"><span>Prénom</span><input formControlName="firstName" maxlength="80" required></label>
          <label class="field"><span>Nom</span><input formControlName="lastName" maxlength="80" required></label>
          <label class="field"><span>Téléphone</span><input type="tel" formControlName="phone" maxlength="40" required></label>
          <label class="field"><span>Numéro de permis</span><input formControlName="licenseNumber" maxlength="80" required></label>
          <label class="field"><span>Expiration du permis</span><input type="date" formControlName="licenseExpiry"></label>
          <label class="check"><input type="checkbox" formControlName="certified"><span>Certifié pour les prestations avec chauffeur</span></label>
          <label class="field notes"><span>Notes internes</span><textarea formControlName="notes" maxlength="2000"></textarea></label>
        </div>
        <button class="button button-primary" [disabled]="busy()">Enregistrer le chauffeur</button>
      </form>
      <section class="list"><h2>Chauffeurs actifs</h2>
        @if (!loading() && drivers().length === 0) { <p>Aucun chauffeur actif.</p> }
        @for (driver of drivers(); track driver.id) {
          <article class="driver">
            <div><h3>{{ driver.firstName }} {{ driver.lastName }}</h3><p>{{ driver.phone }} · Permis {{ driver.licenseNumber }}</p>
              <p>Expiration : {{ driver.licenseExpiry ? (driver.licenseExpiry | date:'mediumDate') : 'Non renseignée' }} · {{ driver.certified ? 'Certifié' : 'Non certifié' }}</p>
              @if (driver.notes) { <p>{{ driver.notes }}</p> }
            </div>
            <button class="button button-secondary" type="button" (click)="deactivate(driver)" [disabled]="busy()">Désactiver</button>
          </article>
        }
      </section>
    </section>
  `,
  styles: `
    .drivers { max-width:1100px; margin:auto; padding:2rem; } header { margin-bottom:2rem; } .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.1em; }
    h1,h2 { font-family:var(--font-display); font-weight:500; } h1 { margin:.4rem 0; font-size:2.5rem; } h2 { font-size:1.7rem; } header>p:last-child { color:var(--color-muted); }
    .editor { padding:1.5rem 0; border-top:1px solid var(--color-line); border-bottom:1px solid var(--color-line); } .fields { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; margin-bottom:1rem; }
    .field { display:grid; gap:.4rem; } .field span,.check { font-size:.8rem; } .field input,.field textarea { width:100%; padding:.7rem; border:1px solid var(--color-line); background:white; color:var(--color-ink); }
    .check { display:flex; align-items:center; gap:.6rem; } .notes { grid-column:1/-1; } .notes textarea { min-height:5rem; } .driver { display:flex; justify-content:space-between; align-items:center; gap:1rem; padding:1rem 0; border-bottom:1px solid var(--color-line); } .driver h3 { margin:.2rem 0; }
    .driver p { margin:.3rem 0; color:var(--color-muted); font-size:.8rem; } .button { padding:.7rem 1rem; } .list { padding-top:1rem; }
    @media(max-width:650px) { .drivers { padding:1.2rem; } .fields { grid-template-columns:1fr; } .notes { grid-column:auto; } .driver { align-items:start; flex-direction:column; } }
  `,
})
export class AdminDriversComponent implements OnInit {
  private readonly api = inject(DriverApiService);
  private readonly builder = inject(FormBuilder).nonNullable;
  protected readonly drivers = signal<Driver[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly form = this.builder.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    phone: ['', [Validators.required, Validators.maxLength(40)]],
    licenseNumber: ['', [Validators.required, Validators.maxLength(80)]],
    licenseExpiry: [''],
    certified: [false],
    notes: ['', Validators.maxLength(2000)],
  });

  ngOnInit(): void { this.load(); }

  protected create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    const value = this.form.getRawValue();
    const licenseExpiry = value.licenseExpiry ? new Date(`${value.licenseExpiry}T23:59:59Z`).toISOString() : null;
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.create({ ...value, licenseExpiry, notes: value.notes.trim() || null }).subscribe({
      next: () => { this.form.reset(); this.success.set('Le chauffeur a été ajouté.'); this.load(); },
      error: (error: unknown) => this.fail(error, 'Impossible d’ajouter le chauffeur.'),
    });
  }

  protected deactivate(driver: Driver): void {
    if (this.busy() || !window.confirm(`Désactiver ${driver.firstName} ${driver.lastName} ?`)) return;
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.deactivate(driver.id).subscribe({
      next: () => { this.success.set('Le chauffeur a été désactivé.'); this.load(); },
      error: (error: unknown) => this.fail(error, 'Impossible de désactiver le chauffeur.'),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (drivers) => { this.drivers.set(drivers); this.loading.set(false); this.busy.set(false); },
      error: (error: unknown) => { this.fail(error, 'Impossible de charger les chauffeurs.'); this.loading.set(false); },
    });
  }

  private fail(error: unknown, fallback: string): void {
    this.busy.set(false);
    this.error.set(error instanceof ApiHttpError ? error.message : error instanceof HttpErrorResponse ? error.error?.detail ?? fallback : fallback);
  }
}

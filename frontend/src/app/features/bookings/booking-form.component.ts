import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { VehicleSummary } from '../../core/services/catalogue-api.service';
import { BookingApiService, BookingInput, BookingPreview, bookingMoney, toBookingInstant } from '../../core/services/booking-api.service';
@Component({
  selector: 'app-booking-form', imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="booking-form" aria-labelledby="booking-form-title">
      <header><h2 id="booking-form-title">Votre demande de réservation</h2>@if (zone()) { <span class="zone">{{ zone() }}</span> }</header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      <form [formGroup]="form" (ngSubmit)="check()" novalidate>
        <div class="form-grid">
          <label class="field"><span>Début de location</span><input type="datetime-local" formControlName="start" required></label>
          <label class="field"><span>Fin de location</span><input type="datetime-local" formControlName="end" required></label>
          <label class="field"><span>Lieu de prise en charge</span><input formControlName="pickupLocation" maxlength="300" required></label>
          <label class="field"><span>Lieu de retour</span><input formControlName="dropoffLocation" maxlength="300" required></label>
        </div>
        <label class="field note"><span>Note pour l'agence</span><textarea formControlName="customerNote" maxlength="2000" rows="2"></textarea></label>
        <button class="button button-secondary" type="submit" [disabled]="busy() || !zone()">{{ busy() ? 'Vérification…' : 'Vérifier disponibilité' }}</button>
      </form>
      @if (available() === false) { <p class="alert alert-error" role="status">Indisponible pour cette période.</p> }
      @if (preview(); as price) {
        <p class="alert alert-success" role="status">Disponible pour cette période.</p>
        <section class="recap" aria-label="Récapitulatif"><h3>Récapitulatif</h3><dl><div><dt>Durée facturée</dt><dd>{{ price.numberOfDays }} jour(s)</dd></div><div><dt>Prix de base</dt><dd>{{ money(price.basePrice, price.currency) }}</dd></div><div><dt>Frais supplémentaires</dt><dd>{{ money(price.additionalCharges, price.currency) }}</dd></div><div><dt>Remise</dt><dd>{{ money(price.discount, price.currency) }}</dd></div><div><dt>Taxe</dt><dd>{{ money(price.tax, price.currency) }}</dd></div><div class="total"><dt>Total</dt><dd>{{ money(price.totalAmount, price.currency) }}</dd></div></dl>
          @if (auth.hasAnyRole(['CUSTOMER'])) { <button class="button button-primary" type="button" (click)="submit()" [disabled]="busy()">{{ busy() ? 'Envoi…' : 'Envoyer ma demande' }}</button> }
          @else { <a class="button button-primary" routerLink="/auth/login" [queryParams]="{ returnUrl: '/vehicles/' + vehicle().id }">Connexion client</a> }
        </section>
      }
    </section>
  `,
  styles: `
    .booking-form { margin:2rem 0; padding:1.5rem 0; border-top:1px solid var(--color-line); }
    header { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:.75rem; margin-bottom:1rem; }
    h2 { margin:0; font:500 1.8rem var(--font-display); } .zone { color:var(--color-muted); font-size:.72rem; }
    form { display:grid; gap:1rem; } .form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; }
    .field>span { font-size:.77rem; font-weight:600; } .field { min-width:0; } .field input { min-width:0; }
    .note textarea { min-height:70px; } form>.button { justify-self:start; }
    .recap { max-width:620px; padding:1rem 0; } h3 { font:500 1.5rem var(--font-display); }
    dl>div { display:flex; justify-content:space-between; gap:1rem; padding:.65rem 0; border-bottom:1px solid var(--color-line); font-size:.8rem; } dd { margin:0; } .total { font-weight:700; } .recap .button { margin-top:1rem; }
    @media(max-width:600px) { .form-grid { grid-template-columns:1fr; } h2 { font-size:1.5rem; } }
  `,
})
export class BookingFormComponent implements OnInit {
  readonly vehicle = input.required<VehicleSummary>();
  protected readonly auth = inject(AuthService);
  private readonly api = inject(BookingApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly zone = signal('');
  protected readonly preview = signal<BookingPreview | null>(null);
  protected readonly available = signal<boolean | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly money = bookingMoney;
  private revision = 0;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    start: ['', Validators.required], end: ['', Validators.required], pickupLocation: ['', [Validators.required, Validators.maxLength(300)]],
    dropoffLocation: ['', [Validators.required, Validators.maxLength(300)]], customerNote: ['', Validators.maxLength(2000)],
  });
  ngOnInit(): void {
    if (!this.auth.accessToken()) this.auth.restoreSession().subscribe();
    this.api.policy().subscribe({ next: (policy) => this.zone.set(policy.businessTimeZone), error: () => this.error.set('Le service de réservation est indisponible.') });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => { this.revision++; this.preview.set(null); this.available.set(null); });
  }
  private request(): BookingInput | null {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.error.set('Renseignez les dates et les deux lieux.'); return null; }
    try {
      const value = this.form.getRawValue();
      const startDateTime = toBookingInstant(value.start, this.zone()); const endDateTime = toBookingInstant(value.end, this.zone());
      if (new Date(startDateTime).getTime() <= Date.now() || new Date(endDateTime) <= new Date(startDateTime)) throw new Error();
      return { vehicleId: this.vehicle().id, startDateTime, endDateTime, pickupLocation: value.pickupLocation.trim(), dropoffLocation: value.dropoffLocation.trim(), customerNote: value.customerNote || null };
    } catch { this.error.set('Choisissez une période future dont la fin suit le début.'); return null; }
  }
  protected check(): void {
    if (this.busy()) return;
    this.error.set(''); this.preview.set(null); this.available.set(null);
    const request = this.request(); if (!request) return;
    const revision = this.revision; this.busy.set(true);
    this.api.availability(request.vehicleId, request.startDateTime, request.endDateTime).subscribe({
      next: (result) => {
        if (revision !== this.revision) { this.busy.set(false); return; }
        this.available.set(result.available);
        if (!result.available) { this.busy.set(false); return; }
        this.api.preview(request).subscribe({
          next: (price) => { if (revision === this.revision) this.preview.set(price); this.busy.set(false); },
          error: (error: unknown) => this.fail(error),
        });
      }, error: (error: unknown) => this.fail(error),
    });
  }
  protected submit(): void {
    if (!this.preview() || this.busy() || !this.auth.hasAnyRole(['CUSTOMER'])) return;
    const request = this.request(); if (!request) return;
    this.busy.set(true); this.error.set('');
    this.api.create(request).subscribe({
      next: (booking) => { this.busy.set(false); void this.router.navigate(['/customer/bookings', booking.id], { state: { created: true } }); },
      error: (error: unknown) => this.fail(error),
    });
  }
  private fail(error: unknown): void { this.busy.set(false); this.preview.set(null); this.available.set(null); this.error.set(error instanceof Error ? error.message : 'La demande a échoué.'); }
}
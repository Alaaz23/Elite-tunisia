import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Location } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Booking, BookingApiService, bookingDate, bookingMoney, bookingStatus } from '../../core/services/booking-api.service';
@Component({
  selector: 'app-booking-detail', imports: [RouterLink, ReactiveFormsModule],
  template: `
    <section class="booking-page">
      <a class="back" [routerLink]="admin() ? '/admin/bookings' : '/customer/bookings'">Retour aux réservations</a>
      @if (loading()) { <p class="loading-state" role="status">Chargement de la réservation…</p> }
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      @if (booking(); as item) {
        @if (created()) { <p class="alert alert-success" role="status">Réservation {{ item.reference }} créée.</p> }
        <header><div><p class="eyebrow">{{ admin() ? item.customerName : 'VOTRE DEMANDE' }}</p><h1>{{ item.reference }}</h1><p class="vehicle-name">{{ item.vehicleName }}</p></div><span class="badge">{{ status(item.status) }}</span></header>
        <div class="details-grid"><section><h2>Trajet</h2><dl><dt>Début</dt><dd>{{ date(item.startDateTime, item.businessTimeZone) }}</dd><dt>Fin</dt><dd>{{ date(item.endDateTime, item.businessTimeZone) }}</dd><dt>Prise en charge</dt><dd>{{ item.pickupLocation }}</dd><dt>Retour</dt><dd>{{ item.dropoffLocation }}</dd><dt>Fuseau horaire</dt><dd>{{ item.businessTimeZone }}</dd></dl></section>
          <section><h2>Montant</h2><dl><dt>Durée facturée</dt><dd>{{ item.numberOfDays }} jour(s)</dd><dt>Prix de base</dt><dd>{{ money(item.basePrice, item.currency) }}</dd><dt>Frais supplémentaires</dt><dd>{{ money(item.additionalCharges, item.currency) }}</dd><dt>Remise</dt><dd>{{ money(item.discount, item.currency) }}</dd><dt>Taxe</dt><dd>{{ money(item.tax, item.currency) }}</dd><dt>Total</dt><dd><strong>{{ money(item.totalAmount, item.currency) }}</strong></dd></dl></section></div>
        @if (item.customerNote) { <section class="notes"><h2>Note client</h2><p>{{ item.customerNote }}</p></section> }
        <div class="actions">
          <a class="button button-secondary" [routerLink]="admin()?'/admin/quotes':'/customer/quotes'" [queryParams]="{booking:item.id}">Devis de cette demande</a>
          @if (admin() && canManage()) {
            @if(item.status==='PENDING'){<a class="button button-secondary" routerLink="/admin/quotes/new" [queryParams]="{booking:item.id}">Créer un devis</a>}
            @if (item.status === 'PENDING') { <button class="button button-primary" (click)="action('confirm')" [disabled]="busy()">Confirmer</button><button class="button button-secondary" (click)="action('reject')" [disabled]="busy()">Refuser</button> }
            @if (item.status === 'CONFIRMED') { <button class="button button-primary" (click)="action('start')" [disabled]="busy()">Démarrer</button> }
            @if (item.status === 'IN_PROGRESS') { <button class="button button-primary" (click)="action('complete')" [disabled]="busy()">Terminer</button> }
          }
          @if ((!admin() || canManage()) && (item.status === 'PENDING' || item.status === 'CONFIRMED')) { <button class="button button-secondary" (click)="cancel()" [disabled]="busy()">Annuler la réservation</button> }
        </div>
        @if (admin()) {
          @if (canManage() && (item.status === 'PENDING' || item.status === 'CONFIRMED')) { <form class="editor" [formGroup]="form" (ngSubmit)="save()"><h2>Informations agence</h2><label class="field"><span>Lieu de prise en charge</span><input formControlName="pickupLocation" maxlength="300"></label><label class="field"><span>Lieu de retour</span><input formControlName="dropoffLocation" maxlength="300"></label><label class="field"><span>Note interne</span><textarea formControlName="internalNote" maxlength="2000"></textarea></label><button class="button button-primary" [disabled]="busy()">Enregistrer</button></form> }
          @else if (item.internalNote) { <section class="notes"><h2>Note interne</h2><p>{{ item.internalNote }}</p></section> }
        }
        <section class="history"><h2>Historique</h2><ol>@for (event of item.history; track $index) { <li><time>{{ date(event.timestamp, item.businessTimeZone) }}</time><span>{{ eventLabel(event.action) }} · {{ status(event.toStatus) }}</span></li> }</ol></section>
      }
    </section>
  `,
  styles: `
    .booking-page { max-width:1180px; margin:auto; padding:2rem; } .back { color:var(--color-olive); font-size:.8rem; } header { display:flex; justify-content:space-between; align-items:start; gap:1rem; margin:1.5rem 0; }
    .eyebrow { font-size:.7rem; color:var(--color-olive); } h1 { margin:.4rem 0; overflow-wrap:anywhere; font:500 2.4rem var(--font-display); } .vehicle-name { color:var(--color-muted); } h2 { font:500 1.6rem var(--font-display); }
    .details-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:2rem; border-top:1px solid var(--color-line); } dl { display:grid; grid-template-columns:1fr 1fr; gap:.8rem; font-size:.82rem; } dt { color:var(--color-muted); } dd { margin:0; overflow-wrap:anywhere; }
    .actions { display:flex; flex-wrap:wrap; gap:.75rem; padding:1.5rem 0; } .notes { padding:1rem 0; border-bottom:1px solid var(--color-line); } .notes p { white-space:pre-line; }
    .editor { display:grid; gap:1rem; max-width:650px; padding:1rem 0; } .editor>.button { justify-self:start; } .field>span { font-size:.78rem; }
    .history { border-top:1px solid var(--color-line); padding-top:1rem; } ol { list-style:none; padding:0; } li { display:flex; flex-wrap:wrap; gap:1rem; padding:.75rem 0; border-bottom:1px solid var(--color-line); font-size:.78rem; } time { color:var(--color-muted); }
    @media(max-width:650px) { .booking-page { padding:1.2rem; } .details-grid { grid-template-columns:1fr; gap:.5rem; } header { flex-direction:column; } h1 { font-size:1.9rem; } }
  `,
})
export class BookingDetailComponent implements OnInit {
  private readonly api = inject(BookingApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly location = inject(Location);
  protected readonly created = signal(false);
  protected readonly admin = signal(false);
  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER', 'AGENT']));
  protected readonly booking = signal<Booking | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly date = bookingDate;
  protected readonly money = bookingMoney;
  protected readonly status = bookingStatus;
  protected readonly form = inject(FormBuilder).nonNullable.group({ pickupLocation: ['', [Validators.required, Validators.maxLength(300)]], dropoffLocation: ['', [Validators.required, Validators.maxLength(300)]], internalNote: ['', Validators.maxLength(2000)] });
  ngOnInit(): void {
    this.created.set((this.location.getState() as { created?: boolean } | null)?.created === true);
    this.admin.set(this.route.snapshot.data['adminBookings'] === true);
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.loading.set(false); this.error.set('Identifiant absent.'); return; }
    this.api.get(id, this.admin()).subscribe({ next: (booking) => { this.receive(booking); this.loading.set(false); }, error: (error: unknown) => { this.fail(error); this.loading.set(false); } });
  }
  private receive(booking: Booking): void { this.booking.set(booking); this.form.patchValue({ pickupLocation: booking.pickupLocation, dropoffLocation: booking.dropoffLocation, internalNote: booking.internalNote ?? '' }); this.busy.set(false); }
  protected action(action: 'confirm' | 'reject' | 'cancel' | 'start' | 'complete'): void {
    const booking = this.booking(); if (!booking || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.api.action(booking.id, action).subscribe({ next: (updated) => { this.receive(updated); this.success.set('Le statut est mis à jour.'); }, error: (error: unknown) => this.fail(error) });
  }
  protected cancel(): void {
    if (!window.confirm('Annuler cette réservation ?')) return;
    if (this.admin()) { this.action('cancel'); return; }
    const booking = this.booking(); if (!booking || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.api.cancel(booking.id).subscribe({ next: (updated) => { this.receive(updated); this.success.set('La réservation est annulée.'); }, error: (error: unknown) => this.fail(error) });
  }
  protected save(): void {
    const booking = this.booking(); this.form.markAllAsTouched(); if (!booking || this.form.invalid || this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.api.update(booking.id, this.form.getRawValue()).subscribe({ next: (updated) => { this.receive(updated); this.success.set('Les informations sont enregistrées.'); }, error: (error: unknown) => this.fail(error) });
  }
  protected eventLabel(action: string): string { return ({ BOOKING_CREATED: 'Demande créée', BOOKING_CONFIRMED: 'Confirmation', BOOKING_REJECTED: 'Refus', BOOKING_CANCELLED: 'Annulation', BOOKING_UPDATED: 'Modification' } as Record<string, string>)[action] ?? action; }
  private fail(error: unknown): void { this.busy.set(false); this.error.set(error instanceof Error ? error.message : 'La demande a échoué.'); }
}
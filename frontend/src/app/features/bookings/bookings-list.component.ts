import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Booking, BookingApiService, BookingFilters, bookingDate, bookingMoney, bookingStatus, toBookingInstant } from '../../core/services/booking-api.service';
@Component({
  selector: 'app-bookings-list', imports: [RouterLink, ReactiveFormsModule],
  template: `
    <section class="bookings-page"><header><div><p class="eyebrow">{{ admin() ? 'AGENCE' : 'ESPACE CLIENT' }}</p><h1>{{ admin() ? 'Réservations' : 'Mes réservations' }}</h1></div><span>{{ total() }} demandes</span></header>
      <form class="filters" [formGroup]="form" (ngSubmit)="search()">
        <label class="field"><span>Référence</span><input formControlName="reference" placeholder="ET-"></label>
        <label class="field"><span>Statut</span><select formControlName="status"><option value="">Tous</option><option value="PENDING">En attente</option><option value="CONFIRMED">Confirmée</option><option value="REJECTED">Refusée</option><option value="CANCELLED">Annulée</option><option value="IN_PROGRESS">En cours</option><option value="COMPLETED">Terminée</option></select></label>
        @if (admin()) {
          <label class="field"><span>Identifiant client</span><input formControlName="customer"></label>
          <label class="field"><span>Identifiant véhicule</span><input formControlName="vehicle"></label>
          <label class="field"><span>Départ à partir de</span><input type="datetime-local" formControlName="startDate"></label>
          <label class="field"><span>Départ avant</span><input type="datetime-local" formControlName="endDate"></label>
        }
        <button class="button button-secondary" type="submit" [disabled]="loading()">Rechercher</button>
      </form>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (loading()) { <p class="loading-state" role="status">Chargement des réservations…</p> }
      @if (!loading() && !error() && bookings().length === 0) { <div class="empty-state"><strong>Aucune réservation</strong><p>Aucune demande ne correspond à votre recherche.</p></div> }
      @if (!loading() && bookings().length > 0) { <div class="table-wrap"><table><thead><tr><th>Référence</th><th>Véhicule</th>@if (admin()) { <th>Client</th> }<th>Début</th><th>Fin</th><th>Statut</th><th>Montant</th></tr></thead><tbody>@for (booking of bookings(); track booking.id) {
        <tr><td><a [routerLink]="[basePath(), booking.id]">{{ booking.reference }}</a></td><td>{{ booking.vehicleName }}</td>@if (admin()) { <td>{{ booking.customerName }}</td> }<td>{{ date(booking.startDateTime, booking.businessTimeZone) }}</td><td>{{ date(booking.endDateTime, booking.businessTimeZone) }}</td><td><span class="badge">{{ status(booking.status) }}</span></td><td>{{ money(booking.totalAmount, booking.currency) }}</td></tr>
      }</tbody></table></div> }
      @if (pages() > 1) { <nav class="pagination" aria-label="Pagination réservations"><button class="button button-secondary" [disabled]="page() === 0 || loading()" (click)="changePage(page()-1)">Précédent</button><span>{{ page()+1 }} / {{ pages() }}</span><button class="button button-secondary" [disabled]="page()+1 >= pages() || loading()" (click)="changePage(page()+1)">Suivant</button></nav> }
    </section>
  `,
  styles: `
    .bookings-page { max-width:1450px; margin:auto; padding:2rem; } header { display:flex; justify-content:space-between; align-items:end; gap:1rem; margin-bottom:1.5rem; } header>span { font-size:.8rem; color:var(--color-muted); }
    .eyebrow { font-size:.7rem; color:var(--color-olive); } h1 { margin:.4rem 0; font:500 2.4rem var(--font-display); }
    .filters { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); align-items:end; gap:.8rem; margin-bottom:1.5rem; } .field>span { font-size:.72rem; } .field { min-width:0; } .field input { min-width:0; }
    td a { color:var(--color-olive); } .pagination { display:flex; align-items:center; justify-content:center; gap:1rem; margin-top:1.5rem; }
    @media(max-width:700px) { .bookings-page { padding:1.2rem; } .filters { grid-template-columns:1fr; } header { align-items:start; flex-direction:column; } h1 { font-size:2rem; } }
  `,
})
export class BookingsListComponent implements OnInit {
  private readonly api = inject(BookingApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly admin = signal(false);
  protected readonly bookings = signal<Booking[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly total = signal(0);
  protected readonly pages = signal(0);
  protected readonly page = signal(0);
  protected readonly date = bookingDate;
  protected readonly money = bookingMoney;
  protected readonly status = bookingStatus;
  private zone = '';
  protected readonly form = inject(FormBuilder).nonNullable.group({ reference: '', status: '', customer: '', vehicle: '', startDate: '', endDate: '' });
  protected basePath() { return this.admin() ? '/admin/bookings' : '/customer/bookings'; }
  ngOnInit(): void {
    this.admin.set(this.route.snapshot.data['adminBookings'] === true);
    this.api.policy().subscribe({ next: (policy) => this.zone = policy.businessTimeZone, error: () => this.error.set('Le fuseau métier est indisponible.') });
    this.load();
  }
  protected search(): void { this.page.set(0); this.load(); }
  protected changePage(page: number): void { this.page.set(page); this.load(); }
  private load(): void {
    const value = this.form.getRawValue();
    let filter: BookingFilters;
    try { filter = { ...value, page: this.page(), size: 25, startDate: value.startDate ? toBookingInstant(value.startDate, this.zone) : '', endDate: value.endDate ? toBookingInstant(value.endDate, this.zone) : '' }; }
    catch { this.error.set('Les dates du filtre sont invalides.'); return; }
    this.loading.set(true); this.error.set('');
    this.api.list(filter, this.admin()).subscribe({
      next: (result) => { this.bookings.set(result.content); this.pages.set(result.totalPages); this.total.set(result.totalElements); this.loading.set(false); },
      error: (error: unknown) => { this.bookings.set([]); this.error.set(error instanceof Error ? error.message : 'La liste est indisponible.'); this.loading.set(false); },
    });
  }
}
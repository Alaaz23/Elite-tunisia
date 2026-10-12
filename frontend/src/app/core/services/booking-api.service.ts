import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Temporal } from '@js-temporal/polyfill';
import { API_BASE_URL } from '../config/api.config';
import { CataloguePage } from './catalogue-api.service';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED' | 'IN_PROGRESS' | 'COMPLETED';
export interface BookingInput { vehicleId: string; startDateTime: string; endDateTime: string; pickupLocation: string; dropoffLocation: string; customerNote: string | null; }
export interface BookingPreview { vehicleId: string; vehicleName: string; startDateTime: string; endDateTime: string; numberOfDays: number; basePrice: number; additionalCharges: number; discount: number; tax: number; totalAmount: number; currency: string; businessTimeZone: string; }
export interface Booking extends BookingPreview {
  id: string; reference: string; customerId: string; customerName: string; pickupLocation: string; dropoffLocation: string;
  status: BookingStatus; customerNote: string | null; internalNote?: string | null; createdAt: string; updatedAt: string;
  confirmedAt: string | null; cancelledAt: string | null;
  history: { action: string; fromStatus: BookingStatus | null; toStatus: BookingStatus; timestamp: string; actorId: string }[];
}
export interface BookingPolicy { businessTimeZone: string; pendingBlocks: boolean; priceRule: string; options: unknown[]; }
export interface BookingFilters { reference?: string; customer?: string; vehicle?: string; status?: string; startDate?: string; endDate?: string; page: number; size: number; }
@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);
  policy() { return this.http.get<BookingPolicy>(`${this.root}/api/bookings/policy`); }
  availability(vehicleId: string, startDateTime: string, endDateTime: string) {
    return this.http.get<{ available: boolean; reason?: string }>(`${this.root}/api/vehicles/${vehicleId}/availability`, { params: { startDateTime, endDateTime } });
  }
  preview(input: BookingInput) { return this.http.post<BookingPreview>(`${this.root}/api/bookings/quote-preview`, input); }
  create(input: BookingInput) { return this.http.post<Booking>(`${this.root}/api/bookings`, input); }
  list(filters: BookingFilters, admin = false) {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    for (const [key, value] of Object.entries(filters)) if (value !== '' && value != null) params = params.set(key, value);
    return this.http.get<CataloguePage<Booking>>(`${this.root}/api/${admin ? 'admin/' : ''}bookings`, { params });
  }
  get(id: string, admin = false) { return this.http.get<Booking>(`${this.root}/api/${admin ? 'admin/' : ''}bookings/${id}`); }
  cancel(id: string) { return this.http.post<Booking>(`${this.root}/api/bookings/${id}/cancel`, {}); }
  action(id: string, action: 'confirm' | 'reject' | 'cancel' | 'start' | 'complete') { return this.http.patch<Booking>(`${this.root}/api/admin/bookings/${id}/${action}`, {}); }
  update(id: string, input: { pickupLocation: string; dropoffLocation: string; internalNote: string | null }) {
    return this.http.put<Booking>(`${this.root}/api/admin/bookings/${id}`, input);
  }
}
export function toBookingInstant(local: string, zone: string): string {
  return Temporal.PlainDateTime.from(local).toZonedDateTime(zone, { disambiguation: 'reject' }).toInstant().toString();
}
export function bookingDate(instant: string, zone: string): string {
  return new Intl.DateTimeFormat('fr-TN', { timeZone: zone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(instant));
}
export function bookingMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat('fr-TN', { style: 'currency', currency, maximumFractionDigits: 3 }).format(amount);
}
export function bookingStatus(status: BookingStatus): string {
  return ({ PENDING: 'En attente', CONFIRMED: 'Confirmée', REJECTED: 'Refusée', CANCELLED: 'Annulée', IN_PROGRESS: 'En cours', COMPLETED: 'Terminée' })[status];
}
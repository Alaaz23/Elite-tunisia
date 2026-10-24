import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';

export interface Driver {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  licenseNumber: string;
  licenseExpiry: string | null;
  certified: boolean;
  notes: string | null;
  active: boolean;
  createdAt: string;
}

export interface DriverAssignment {
  id: string;
  driverId: string;
  bookingId: string;
}

@Injectable({ providedIn: 'root' })
export class DriverApiService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);

  list() { return this.http.get<Driver[]>(`${this.root}/api/admin/drivers`); }
  create(input: { firstName: string; lastName: string; phone: string; licenseNumber: string; licenseExpiry: string | null; certified: boolean; notes: string | null }) {
    return this.http.post<Driver>(`${this.root}/api/admin/drivers`, input);
  }
  deactivate(id: string) { return this.http.delete<void>(`${this.root}/api/admin/drivers/${id}`); }
  assignment(bookingId: string) { return this.http.get<DriverAssignment | null>(`${this.root}/api/admin/bookings/${bookingId}/driver`); }
  assign(bookingId: string, driverId: string, requireCertified: boolean) {
    return this.http.post<DriverAssignment>(`${this.root}/api/admin/bookings/${bookingId}/driver`, { driverId, requireCertified });
  }
  unassign(bookingId: string) { return this.http.delete<void>(`${this.root}/api/admin/bookings/${bookingId}/driver`); }
}

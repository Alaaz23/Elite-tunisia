import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';

export type PartnerType = 'INDIVIDUAL' | 'COMPANY';
export type GuaranteeStatus = 'ACTIVE' | 'WITHDRAWN';

export interface PartnerSummary {
  id: string;
  name: string;
  partnerType: PartnerType;
  email: string | null;
  phone: string | null;
  address: string | null;
  city: string | null;
  taxIdentifier: string | null;
  active: boolean;
  userId: string | null;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
}

export interface PartnerRequest {
  partnerType: PartnerType;
  name: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  city?: string | null;
  taxIdentifier?: string | null;
  userId?: string | null;
}

export interface PartnerVehicleSummary {
  id: string;
  brandName: string;
  model: string;
  year: number;
  status: string;
}

export interface AssignmentSummary {
  id: string;
  vehicleId: string;
  partnerId: string;
  partnerName: string;
  assignedAt: string;
  endedAt: string | null;
  reason: string;
  assignedByUserId: string;
}

export interface AssignVehicleRequest {
  partnerId: string;
  reason: string;
}

export interface GuaranteeSummary {
  id: string;
  vehicleId: string;
  partnerId: string;
  partnerName: string;
  startsAt: string;
  endsAt: string;
  status: GuaranteeStatus;
  reason: string;
  createdByUserId: string;
  createdAt: string;
  withdrawnByUserId: string | null;
  withdrawnAt: string | null;
}

export interface GuaranteeRequest {
  vehicleId: string;
  startsAt: string;
  endsAt: string;
  reason: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class PartnerApiService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);

  listPartners(page = 0, size = 25) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<PartnerSummary>>(`${this.root}/api/admin/partners`, { params });
  }

  getPartner(id: string) {
    return this.http.get<PartnerSummary>(`${this.root}/api/admin/partners/${id}`);
  }

  getPartnerVehicles(id: string) {
    return this.http.get<PartnerVehicleSummary[]>(`${this.root}/api/admin/partners/${id}/vehicles`);
  }

  createPartner(request: PartnerRequest) {
    return this.http.post<PartnerSummary>(`${this.root}/api/admin/partners`, request);
  }

  updatePartner(id: string, request: PartnerRequest) {
    return this.http.put<PartnerSummary>(`${this.root}/api/admin/partners/${id}`, request);
  }

  deactivatePartner(id: string) {
    return this.http.post<PartnerSummary>(`${this.root}/api/admin/partners/${id}/deactivate`, {});
  }

  activatePartner(id: string) {
    return this.http.post<PartnerSummary>(`${this.root}/api/admin/partners/${id}/activate`, {});
  }

  getVehicleAssignments(vehicleId: string) {
    return this.http.get<AssignmentSummary[]>(`${this.root}/api/admin/vehicles/${vehicleId}/partner-assignments`);
  }

  assignVehicle(vehicleId: string, request: AssignVehicleRequest) {
    return this.http.post<AssignmentSummary>(`${this.root}/api/admin/vehicles/${vehicleId}/partner-assignments`, request);
  }

  listGuarantees(page = 0, size = 25) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<GuaranteeSummary>>(`${this.root}/api/admin/guarantees`, { params });
  }

  getGuarantee(id: string) {
    return this.http.get<GuaranteeSummary>(`${this.root}/api/admin/guarantees/${id}`);
  }

  getVehicleGuarantees(vehicleId: string) {
    return this.http.get<GuaranteeSummary[]>(`${this.root}/api/admin/guarantees/by-vehicle/${vehicleId}`);
  }

  createGuarantee(request: GuaranteeRequest) {
    return this.http.post<GuaranteeSummary>(`${this.root}/api/admin/guarantees`, request);
  }

  withdrawGuarantee(id: string) {
    return this.http.post<GuaranteeSummary>(`${this.root}/api/admin/guarantees/${id}/withdraw`, {});
  }

  getMyPartner() {
    return this.http.get<PartnerSummary>(`${this.root}/api/partner/me`);
  }

  getMyVehicles() {
    return this.http.get<PartnerVehicleSummary[]>(`${this.root}/api/partner/vehicles`);
  }

  getMyVehicle(vehicleId: string) {
    return this.http.get<PartnerVehicleSummary>(`${this.root}/api/partner/vehicles/${vehicleId}`);
  }

  getMyGuarantees() {
    return this.http.get<GuaranteeSummary[]>(`${this.root}/api/partner/guarantees`);
  }
}

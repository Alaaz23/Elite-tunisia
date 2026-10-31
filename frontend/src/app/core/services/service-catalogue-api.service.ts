import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';

export interface CatalogueService {
  id: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  active: boolean;
}

export interface CatalogueServicePage {
  content: CatalogueService[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ServiceCreateInput { name: string; description: string; price: number; currency: string; }
export interface ServiceUpdateInput { name: string; description: string; price: number; }

@Injectable({ providedIn: 'root' })
export class ServiceCatalogueApiService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);

  list(search: string, active: boolean | null, page = 0) {
    let params = new HttpParams().set('search', search).set('page', page).set('size', 25);
    if (active !== null) params = params.set('active', active);
    return this.http.get<CatalogueServicePage>(`${this.root}/api/admin/services`, { params });
  }
  create(input: ServiceCreateInput) { return this.http.post<CatalogueService>(`${this.root}/api/admin/services`, input); }
  update(id: string, input: ServiceUpdateInput) { return this.http.put<CatalogueService>(`${this.root}/api/admin/services/${id}`, input); }
  activate(id: string) { return this.http.post<CatalogueService>(`${this.root}/api/admin/services/${id}/activate`, {}); }
  deactivate(id: string) { return this.http.post<CatalogueService>(`${this.root}/api/admin/services/${id}/deactivate`, {}); }
}

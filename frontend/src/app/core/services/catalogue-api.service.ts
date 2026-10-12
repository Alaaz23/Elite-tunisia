import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface CataloguePage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface BrandSummary {
  id: string;
  name: string;
  slug: string;
  description: string;
  logoUrl: string | null;
  active: boolean;
}

export interface CategorySummary {
  id: string;
  name: string;
  slug: string;
  description: string;
  active: boolean;
}

export interface VehicleImage {
  id?: string;
  url: string;
  altText: string;
  position: number;
  isPrimary: boolean;
}

export interface VehicleSummary {
  id: string;
  brand: BrandSummary;
  model: string;
  year: number;
  category: CategorySummary;
  description: string;
  pricePerDay: number;
  pricePerWeek: number;
  priceWithDriver: number | null;
  currency: string;
  passengers: number;
  doors: number;
  transmission: 'AUTOMATIC' | 'MANUAL' | 'SEMI_AUTOMATIC';
  fuel: 'PETROL' | 'DIESEL' | 'HYBRID' | 'ELECTRIC' | 'PLUG_IN_HYBRID';
  withDriverAvailable: boolean;
  status: 'AVAILABLE' | 'RESERVED' | 'RENTED' | 'MAINTENANCE' | 'UNAVAILABLE';
  images: VehicleImage[];
  features: string[];
}

export interface VehicleFilters {
  startDateTime?: string;
  endDateTime?: string;
  search?: string;
  brand?: string;
  category?: string;
  minPrice?: number | null;
  maxPrice?: number | null;
  passengers?: number | null;
  transmission?: string;
  withDriver?: boolean | null;
  sort: string;
  page: number;
  size: number;
}

export interface BrandInput {
  name: string;
  description: string;
  logoUrl: string | null;
  active: boolean;
}

export interface CategoryInput {
  name: string;
  description: string;
  active: boolean;
}

export interface VehicleInput {
  brandId: string;
  categoryId: string;
  model: string;
  year: number;
  description: string;
  pricePerDay: number;
  pricePerWeek: number;
  priceWithDriver: number | null;
  currency: string;
  passengers: number;
  doors: number;
  transmission: VehicleSummary['transmission'];
  fuel: VehicleSummary['fuel'];
  withDriverAvailable: boolean;
  status: VehicleSummary['status'];
  features: string[];
  images: VehicleImage[];
}

@Injectable({ providedIn: 'root' })
export class CatalogueApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  vehicles(filters: VehicleFilters, admin = false): Observable<CataloguePage<VehicleSummary>> {
    return this.http.get<CataloguePage<VehicleSummary>>(`${this.baseUrl}/api/${admin ? 'admin/' : ''}vehicles${!admin && filters.startDateTime && filters.endDateTime ? '/available' : ''}`, {
      params: this.params(filters),
    });
  }

  vehicle(id: string): Observable<VehicleSummary> {
    return this.http.get<VehicleSummary>(`${this.baseUrl}/api/vehicles/${id}`);
  }

  brands(admin = false): Observable<CataloguePage<BrandSummary>> {
    return this.http.get<CataloguePage<BrandSummary>>(`${this.baseUrl}/api/${admin ? 'admin/' : ''}brands`, {
      params: new HttpParams().set('page', 0).set('size', 100),
    });
  }

  categories(admin = false): Observable<CataloguePage<CategorySummary>> {
    return this.http.get<CataloguePage<CategorySummary>>(`${this.baseUrl}/api/${admin ? 'admin/' : ''}categories`, {
      params: new HttpParams().set('page', 0).set('size', 100),
    });
  }

  createBrand(input: BrandInput): Observable<BrandSummary> {
    return this.http.post<BrandSummary>(`${this.baseUrl}/api/admin/brands`, input);
  }

  updateBrand(id: string, input: BrandInput): Observable<BrandSummary> {
    return this.http.put<BrandSummary>(`${this.baseUrl}/api/admin/brands/${id}`, input);
  }

  deleteBrand(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/admin/brands/${id}`);
  }

  createCategory(input: CategoryInput): Observable<CategorySummary> {
    return this.http.post<CategorySummary>(`${this.baseUrl}/api/admin/categories`, input);
  }

  updateCategory(id: string, input: CategoryInput): Observable<CategorySummary> {
    return this.http.put<CategorySummary>(`${this.baseUrl}/api/admin/categories/${id}`, input);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/admin/categories/${id}`);
  }

  createVehicle(input: VehicleInput): Observable<VehicleSummary> {
    return this.http.post<VehicleSummary>(`${this.baseUrl}/api/admin/vehicles`, input);
  }

  updateVehicle(id: string, input: VehicleInput): Observable<VehicleSummary> {
    return this.http.put<VehicleSummary>(`${this.baseUrl}/api/admin/vehicles/${id}`, input);
  }

  updateVehicleStatus(id: string, status: VehicleSummary['status']): Observable<VehicleSummary> {
    return this.http.patch<VehicleSummary>(`${this.baseUrl}/api/admin/vehicles/${id}/status`, { status });
  }

  deleteVehicle(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/admin/vehicles/${id}`);
  }

  uploadImage(file: File): Observable<{ url: string }> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<{ url: string }>(`${this.baseUrl}/api/admin/uploads/images`, form);
  }

  deleteUploadedImage(url: string): Observable<void> {
    const key = url.startsWith('/api/files/') ? url.slice('/api/files/'.length) : '';
    return this.http.delete<void>(`${this.baseUrl}/api/admin/uploads/images/${encodeURIComponent(key)}`);
  }

  private params(filters: VehicleFilters): HttpParams {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .set('sort', filters.sort);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.startDateTime) params = params.set('startDateTime', filters.startDateTime);
    if (filters.endDateTime) params = params.set('endDateTime', filters.endDateTime);
    if (filters.brand) params = params.set('brand', filters.brand);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.minPrice != null) params = params.set('minPrice', filters.minPrice);
    if (filters.maxPrice != null) params = params.set('maxPrice', filters.maxPrice);
    if (filters.passengers != null) params = params.set('passengers', filters.passengers);
    if (filters.transmission) params = params.set('transmission', filters.transmission);
    if (filters.withDriver != null) params = params.set('withDriver', filters.withDriver);
    return params;
  }
}
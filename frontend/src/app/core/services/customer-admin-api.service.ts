import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api.config';

export interface AdminCustomer {
  id: string;
  firstName: string;
  lastName: string;
  phone: string;
  customerLevel: string;
  email: string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminCustomerPage {
  content: AdminCustomer[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class CustomerAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);

  list(search: string, page = 0) {
    const params = new HttpParams().set('search', search).set('page', page).set('size', 25);
    return this.http.get<AdminCustomerPage>(`${this.root}/api/admin/customers`, { params });
  }
}

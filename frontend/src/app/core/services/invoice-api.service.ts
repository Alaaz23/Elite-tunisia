import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface InvoiceSummary { id:string; reference:string; totalAmount:number; currency:string; issuedAt:string }
export interface AdminInvoiceSummary { id:string; reference:string; totalAmount:number; currency:string }
export interface InvoiceLine { id:string; type:string; description:string; quantity:number; unitPrice:number; amount:number; position:number; metadata:Record<string,string> }
export interface InvoiceDetail extends InvoiceSummary { lines: InvoiceLine[] }

@Injectable({providedIn:'root'})
export class InvoiceApiService {
  private readonly http = inject(HttpClient); private readonly root = inject(API_BASE_URL);
  listForCustomer() { return this.http.get<InvoiceSummary[]>(`${this.root}/api/invoices`); }
  listForAdmin() { return this.http.get<AdminInvoiceSummary[]>(`${this.root}/api/admin/invoices`); }
  get(id:string, admin = false) { return this.http.get<InvoiceDetail>(`${this.root}/api/${admin ? 'admin/' : ''}invoices/${id}`); }
  downloadPdf(id:string, admin = false): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.root}/api/${admin ? 'admin/' : ''}invoices/${id}/pdf`, { observe: 'response', responseType: 'blob' });
  }
}

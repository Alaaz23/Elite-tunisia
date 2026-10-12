import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../config/api.config';

export interface InvoiceSummary { id:string; reference:string; totalAmount:number; currency:string; issuedAt:string }
export interface InvoiceDetail extends InvoiceSummary { lines: { description:string; quantity:number; unitPrice:number; amount:number }[] }

@Injectable({providedIn:'root'})
export class InvoiceApiService {
  private readonly http = inject(HttpClient); private readonly root = inject(API_BASE_URL);
  listForCustomer() { return this.http.get<InvoiceSummary[]>(`${this.root}/api/invoices`); }
  get(id:string) { return this.http.get<InvoiceDetail>(`${this.root}/api/invoices/${id}`); }
  downloadPdf(id:string) { return this.http.get(`${this.root}/api/invoices/${id}/pdf`,{observe:'response', responseType:'blob'}); }
}

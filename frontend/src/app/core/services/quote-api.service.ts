import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../config/api.config';
import { CataloguePage } from './catalogue-api.service';
export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';
export interface QuoteLine { id: string; type: string; description: string; quantity: number; unitPrice: number; amount: number; metadata: Record<string,string>; position: number; }
export interface Quote {
  id: string; reference: string; bookingId: string; bookingReference: string; bookingStatus: string; customerId: string; customerName: string; vehicleId: string; vehicleName: string;
  startDateTime: string; endDateTime: string; pickupLocation: string; dropoffLocation: string; version: number; status: QuoteStatus; currency: string;
  subtotal: number; discountAmount: number; taxAmount: number; totalAmount: number; validUntil: string; customerNote: string | null; internalNote?: string | null;
  createdAt: string; updatedAt: string; sentAt: string | null; acceptedAt: string | null; rejectedAt: string | null; businessTimeZone: string;
  lines: QuoteLine[]; history: { actorId: string | null; action: string; message: string | null; timestamp: string }[];
}
export interface QuoteInput { validUntil: string; withDriver: boolean; discountAmount: number; customerNote: string | null; internalNote: string | null; services: { serviceId: string; quantity: number }[]; }
export interface QuoteOptions { withDriverAvailable: boolean; services: { id: string; name: string; price: number; currency: string }[]; }
@Injectable({ providedIn:'root' })
export class QuoteApiService {
  private readonly http = inject(HttpClient); private readonly root = inject(API_BASE_URL);
  list(filters: Record<string,string | number>, admin = false) {
    let params = new HttpParams(); for (const [key,value] of Object.entries(filters)) if (value !== '') params = params.set(key,value);
    return this.http.get<CataloguePage<Quote>>(`${this.root}/api/${admin ? 'admin' : 'customer'}/quotes`,{params});
  }
  get(id:string,admin=false) { return this.http.get<Quote>(`${this.root}/api/${admin ? 'admin' : 'customer'}/quotes/${id}`); }
  options(bookingId:string) { return this.http.get<QuoteOptions>(`${this.root}/api/admin/quotes/options/${bookingId}`); }
  create(bookingId:string,input:QuoteInput) { return this.http.post<Quote>(`${this.root}/api/admin/quotes/booking/${bookingId}`,input); }
  update(id:string,input:QuoteInput) { return this.http.put<Quote>(`${this.root}/api/admin/quotes/${id}`,input); }
  version(id:string,input:QuoteInput) { return this.http.post<Quote>(`${this.root}/api/admin/quotes/${id}/versions`,input); }
  adminAction(id:string,action:'send'|'cancel') { return this.http.post<Quote>(`${this.root}/api/admin/quotes/${id}/${action}`,{}); }
  customerAction(id:string,action:'accept'|'reject'|'request-change',message='') { return this.http.post<Quote>(`${this.root}/api/customer/quotes/${id}/${action}`,{message}); }
  createPayment(quoteId:string, body:{ amount:number, method:string, idempotencyKey?:string }) { return this.http.post<{ id:string, status:string, transactionRef:string, amount:number }>(`${this.root}/api/customer/quotes/${quoteId}/payments`, body); }
}
export function quoteStatus(status:QuoteStatus):string { return ({DRAFT:'Brouillon',SENT:'Envoyé',ACCEPTED:'Accepté',REJECTED:'Refusé',EXPIRED:'Expiré',CANCELLED:'Annulé'})[status]; }
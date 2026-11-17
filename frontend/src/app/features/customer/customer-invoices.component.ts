import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InvoiceApiService, InvoiceSummary } from '../../core/services/invoice-api.service';
import { extractFilenameFromContentDisposition } from '../../shared/utils/http';

@Component({selector:'app-customer-invoices', imports: [DatePipe, DecimalPipe, RouterLink], template:`
  <section class="invoices"><h1>Mes factures</h1>
    @if(loading()){<p role="status">Chargement…</p>}
    @if(error()){<p class="alert" role="alert">{{error()}}</p>}
    @if(!loading() && !error() && invoices().length === 0){<p>Aucune facture pour le moment.</p>}
    <ul>@for(inv of invoices();track inv.id){<li><a [routerLink]="['/customer/invoices', inv.id]"><strong>{{inv.reference}}</strong></a> — {{inv.issuedAt | date: 'dd/MM/yyyy'}} — {{inv.totalAmount | number: '1.3-3'}} {{inv.currency}} <button type="button" (click)="download(inv.id)">Télécharger</button></li>}</ul>
  </section>`})
export class CustomerInvoicesComponent implements OnInit{
  private readonly api=inject(InvoiceApiService);
  protected readonly invoices=signal<InvoiceSummary[]>([]);
  protected readonly loading=signal(true); protected readonly error=signal('');
  ngOnInit():void{this.api.listForCustomer().subscribe({next:list=>{this.invoices.set(list);this.loading.set(false);},error:e=>{this.error.set('Impossible de charger les factures.');this.loading.set(false);}})}
  protected download(id:string){
    this.loading.set(true); this.error.set('');
    this.api.downloadPdf(id).subscribe({next:resp=>{
      const blob = resp.body as Blob; const cd = resp.headers.get('content-disposition') ?? '';
      const filename = extractFilenameFromContentDisposition(cd) || 'invoice.pdf';
      const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
      this.loading.set(false);
    },error:()=>{this.error.set('Téléchargement échoué.'); this.loading.set(false);}});
  }
}

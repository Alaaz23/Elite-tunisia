import { Component, inject, OnInit, signal } from '@angular/core';
import { AdminInvoicePage, AdminInvoiceSummary, InvoiceApiService } from '../../core/services/invoice-api.service';

@Component({selector:'app-admin-invoices',template:`
  <section class="admin-invoices"><h1>Factures (admin)</h1>
    @if(loading()){<p>Chargement…</p>}@if(error()){<p class="alert">{{error()}}</p>}
    <table><thead><tr><th>Ref</th><th>Montant</th><th>Devise</th><th>Actions</th></tr></thead><tbody>@for(inv of invoices();track inv.id){<tr><td>{{inv.reference}}</td><td>{{inv.totalAmount}}</td><td>{{inv.currency}}</td><td><button (click)="download(inv.id)">PDF</button></td></tr>}</tbody></table>
    <nav><button [disabled]="first()" (click)="load(page()-1)">Précédent</button> <span>Page {{page()+1}} / {{totalPages()}}</span> <button [disabled]="last()" (click)="load(page()+1)">Suivant</button></nav>
  </section>`})
export class AdminInvoicesComponent implements OnInit{
  private readonly api=inject(InvoiceApiService);
  protected readonly invoices=signal<AdminInvoiceSummary[]>([]);
  protected readonly loading=signal(true); protected readonly error=signal('');
  protected readonly page=signal(0); protected readonly totalPages=signal(0);
  protected readonly first=signal(true); protected readonly last=signal(true);
  ngOnInit():void{this.load(0)}
  protected load(page:number):void{this.loading.set(true);this.api.listForAdmin(page).subscribe({next:(res:AdminInvoicePage)=>{this.invoices.set(res.content);this.page.set(res.page);this.totalPages.set(res.totalPages);this.first.set(res.first);this.last.set(res.last);this.error.set('');this.loading.set(false);},error:()=>{this.error.set('Impossible de charger les factures.');this.loading.set(false);}})}
  protected download(id:string){this.api.downloadPdf(id,true).subscribe({next:resp=>{const blob=resp.body as Blob; const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download='invoice-'+id+'.pdf'; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);},error:()=>this.error.set('Téléchargement échoué.')});}
}

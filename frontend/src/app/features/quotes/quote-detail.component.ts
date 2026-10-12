import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BookingApiService, bookingDate, bookingMoney } from '../../core/services/booking-api.service';
import { Quote, QuoteApiService, quoteStatus } from '../../core/services/quote-api.service';
import { QuoteEditorComponent } from './quote-editor.component';
@Component({selector:'app-quote-detail',imports:[ReactiveFormsModule,RouterLink,QuoteEditorComponent],template:`
  <section class="quote-page"><a class="back" [routerLink]="admin()?'/admin/quotes':'/customer/quotes'">Retour aux devis</a>
    @if(loading()){<p class="loading-state" role="status">Chargement du devis…</p>}@if(error()){<p class="alert alert-error" role="alert">{{error()}}</p>}@if(success()){<p class="alert alert-success" role="status">{{success()}}</p>}
    @if(quote();as item){<header><div><p class="eyebrow">PROPOSITION COMMERCIALE · VERSION {{item.version}}</p><h1>{{item.reference}}</h1><p>{{item.customerName}} · {{item.vehicleName}}</p></div><span class="badge">{{status(item.status)}}</span></header>
      <div class="summary"><p><a [routerLink]="[admin()?'/admin/bookings':'/customer/bookings',item.bookingId]">{{item.bookingReference}}</a></p><p>{{date(item.startDateTime,item.businessTimeZone)}} – {{date(item.endDateTime,item.businessTimeZone)}}</p><p>{{item.pickupLocation}} → {{item.dropoffLocation}}</p><p>Validité : {{date(item.validUntil,item.businessTimeZone)}}</p></div>
      <div class="table-wrap"><table><thead><tr><th>Ligne</th><th>Quantité</th><th>Prix unitaire</th><th>Montant</th></tr></thead><tbody>@for(line of item.lines;track line.id){<tr><td>{{line.description}}</td><td>{{line.quantity}}</td><td>{{money(line.unitPrice,item.currency)}}</td><td>{{money(line.amount,item.currency)}}</td></tr>}</tbody></table></div>
      <dl class="totals"><dt>Sous-total</dt><dd>{{money(item.subtotal,item.currency)}}</dd><dt>Remise</dt><dd>{{money(item.discountAmount,item.currency)}}</dd><dt>Taxes</dt><dd>{{money(item.taxAmount,item.currency)}}</dd><dt>Total</dt><dd class="total">{{money(item.totalAmount,item.currency)}}</dd></dl>
      @if(item.customerNote){<p class="note">{{item.customerNote}}</p>}
      <div class="actions">@if(admin()&&canManage()){
        @if(item.status==='DRAFT'){<button class="button button-primary" (click)="adminAction('send')" [disabled]="busy()">Envoyer le devis</button>}
        @if(item.status==='SENT'){<button class="button button-secondary" (click)="adminAction('cancel')" [disabled]="busy()">Annuler le devis</button>}
        @if(item.status!=='DRAFT'&&item.bookingStatus==='PENDING'){<a class="button button-secondary" routerLink="/admin/quotes/new" [queryParams]="{booking:item.bookingId,previous:item.id}">Nouvelle version</a>}
        @if(item.status==='ACCEPTED'&&item.bookingStatus==='PENDING'){<button class="button button-primary" (click)="confirmBooking()" [disabled]="busy()">Valider la réservation</button>}
      }
      @if(!admin()&&item.status==='SENT'){<button class="button button-primary" (click)="customerAction('accept')" [disabled]="busy()">Accepter le devis</button><button class="button button-secondary" (click)="customerAction('reject')" [disabled]="busy()">Refuser le devis</button><button class="button button-secondary" (click)="customerAction('request-change')" [disabled]="busy()">Demander une modification</button>}
      </div>
      @if(!admin()&&item.status==='SENT'){<label class="field"><span>Motif ou demande (facultatif)</span><textarea [formControl]="message" maxlength="2000"></textarea></label>}
      @if(!admin()&&item.status==='ACCEPTED'){<div class="payment"><p>Montant à payer : {{money(item.totalAmount,item.currency)}}</p><button class="button button-primary" (click)="pay()" [disabled]="paymentState()!=='idle'">Payer</button>
        @if(paymentState()==='processing'){<span class="status">Paiement en cours...</span>}
        @if(paymentState()==='success'){<span class="status">Paiement réussi.</span>}
        @if(paymentState()==='failed'){<span class="status">Échec du paiement.</span>}
      </div>}
      @if(admin()&&item.internalNote){<section class="note"><h2>Note interne</h2><p>{{item.internalNote}}</p></section>}
      @if(admin()&&canManage()&&item.status==='DRAFT'){<app-quote-editor [quote]="item" (saved)="receive($event)" />}
      <section class="history"><h2>Historique</h2><ol>@for(event of item.history;track $index){<li><time>{{date(event.timestamp,item.businessTimeZone)}}</time><span>{{eventLabel(event.action)}}</span>@if(event.message){<p>{{event.message}}</p>}</li>}</ol></section>
      <a class="versions" [routerLink]="admin()?'/admin/quotes':'/customer/quotes'" [queryParams]="{booking:item.bookingId}">Versions de cette demande</a>
    }
  </section>`,styles:`.quote-page{max-width:1180px;margin:auto;padding:2rem;}.back,.versions,.summary a{color:var(--color-olive);font-size:.8rem;}header{display:flex;justify-content:space-between;align-items:start;gap:1rem;margin:1.5rem 0;}h1{font:500 2.3rem var(--font-display);margin:.4rem 0;overflow-wrap:anywhere;}h2{font:500 1.5rem var(--font-display);}.eyebrow{font-size:.7rem;color:var(--color-olive);}header p:last-child{font-size:.82rem;color:var(--color-muted);}.summary{display:grid;grid-template-columns:1fr 1fr;gap:.4rem 1rem;padding:1rem 0;border-top:1px solid var(--color-line);font-size:.82rem;}.summary p{margin:.3rem 0;overflow-wrap:anywhere;}.totals{display:grid;grid-template-columns:1fr 1fr;gap:.75rem;max-width:420px;margin:1.5rem 0 1.5rem auto;font-size:.85rem;}dt{color:var(--color-muted);}dd{margin:0;text-align:right;}.total{font-weight:700;}.actions{display:flex;flex-wrap:wrap;gap:.6rem;margin:1rem 0;}.note{white-space:pre-line;font-size:.82rem;}.history{border-top:1px solid var(--color-line);padding:1rem 0;}ol{padding:0;list-style:none;}li{display:flex;flex-wrap:wrap;gap:.7rem;padding:.7rem 0;border-bottom:1px solid var(--color-line);font-size:.78rem;}li time{color:var(--color-muted);}li p{flex-basis:100%;margin:0;overflow-wrap:anywhere;}@media(max-width:650px){.quote-page{padding:1.2rem;}header{flex-direction:column;}h1{font-size:1.9rem;}.summary{grid-template-columns:1fr;}}`})
export class QuoteDetailComponent implements OnInit {
  private readonly api=inject(QuoteApiService);private readonly bookingApi=inject(BookingApiService);private readonly route=inject(ActivatedRoute);private readonly auth=inject(AuthService);
  protected readonly admin=signal(false);protected readonly canManage=computed(()=>this.auth.hasAnyRole(['SUPER_ADMIN','MANAGER','AGENT']));protected readonly quote=signal<Quote|null>(null);protected readonly loading=signal(true);protected readonly busy=signal(false);protected readonly error=signal('');protected readonly success=signal('');
  protected readonly paymentState=signal<'idle'|'processing'|'success'|'failed'>('idle');
  protected readonly date=bookingDate;protected readonly money=bookingMoney;protected readonly status=quoteStatus;protected readonly message=new FormControl('',{nonNullable:true,validators:Validators.maxLength(2000)});
  ngOnInit():void{this.admin.set(this.route.snapshot.data['adminQuotes']===true);const id=this.route.snapshot.paramMap.get('id');if(!id){this.fail(new Error('Identifiant absent.'));return;}this.api.get(id,this.admin()).subscribe({next:quote=>{this.receive(quote);this.loading.set(false);},error:error=>this.fail(error)});}
  protected receive(quote:Quote):void{this.quote.set(quote);this.busy.set(false);}
  protected adminAction(action:'send'|'cancel'):void{const quote=this.quote();if(!quote||this.busy())return;this.busy.set(true);this.error.set('');this.api.adminAction(quote.id,action).subscribe({next:result=>{this.receive(result);this.success.set('Le devis est mis à jour.');},error:error=>this.fail(error)});}
  protected customerAction(action:'accept'|'reject'|'request-change'):void{const quote=this.quote();if(!quote||this.busy()||this.message.invalid)return;this.busy.set(true);this.error.set('');this.api.customerAction(quote.id,action,this.message.value).subscribe({next:result=>{this.receive(result);this.success.set(action==='request-change'?'Votre demande de modification est enregistrée.':'Votre réponse est enregistrée.');},error:error=>{this.fail(error);this.api.get(quote.id).subscribe({next:updated=>this.quote.set(updated),error:()=>{}});}});}
  protected confirmBooking():void{const quote=this.quote();if(!quote||this.busy())return;this.busy.set(true);this.error.set('');this.bookingApi.action(quote.bookingId,'confirm').subscribe({next:booking=>{this.receive({...quote,bookingStatus:booking.status});this.success.set('Réservation confirmée.');},error:error=>this.fail(error)});}
  protected eventLabel(action:string):string{return ({QUOTE_CREATED:'Création',QUOTE_UPDATED:'Modification',QUOTE_SENT:'Envoi',QUOTE_ACCEPTED:'Acceptation',QUOTE_REJECTED:'Refus',QUOTE_EXPIRED:'Expiration',QUOTE_CANCELLED:'Annulation',QUOTE_CHANGE_REQUESTED:'Demande de modification'} as Record<string,string>)[action]??action;}
  protected pay():void{
    const quote=this.quote(); if(!quote||this.busy()||this.paymentState()!=='idle')return;
    this.paymentState.set('processing'); this.busy.set(true); this.error.set('');
    const key=(typeof crypto !== 'undefined' && typeof (crypto as any).randomUUID === 'function') ? (crypto as any).randomUUID() : Math.random().toString(36).slice(2);
    // disable button by busy/paymentState; protect against double submit
    this.api.createPayment(quote.id,{amount:quote.totalAmount,method:'CARD',idempotencyKey:key}).subscribe({next:res=>{
      this.paymentState.set('success'); this.success.set('Paiement réussi. Référence: ' + res.transactionRef);
      this.busy.set(false);
    },error:err=>{
      this.paymentState.set('failed'); this.busy.set(false);
      // try to surface server message if present
      const msg = err && (err as any).error && (err as any).error.message ? (err as any).error.message : (err instanceof Error?err.message:'Le paiement a échoué.');
      this.fail(new Error(msg));
    }});
  }
  private fail(error:unknown):void{this.loading.set(false);this.busy.set(false);this.error.set(error instanceof Error?error.message:'La demande a échoué.');}
}
import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Temporal } from '@js-temporal/polyfill';
import { Booking, BookingApiService, bookingDate, bookingMoney, toBookingInstant } from '../../core/services/booking-api.service';
import { Quote, QuoteApiService, QuoteInput, QuoteOptions } from '../../core/services/quote-api.service';
@Component({
  selector:'app-quote-editor', imports:[ReactiveFormsModule],
  template:`
    <section class="editor"><h2>{{ quote() ? 'Modifier le brouillon' : 'Créer une proposition commerciale' }}</h2>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (loading()) { <p class="loading-state" role="status">Chargement de la demande…</p> }
      @if (booking(); as item) {
        <dl class="context"><dt>Demande</dt><dd>{{ item.reference }}</dd><dt>Client</dt><dd>{{ item.customerName }}</dd><dt>Véhicule</dt><dd>{{ item.vehicleName }}</dd><dt>Période</dt><dd>{{ date(item.startDateTime,item.businessTimeZone) }} – {{ date(item.endDateTime,item.businessTimeZone) }}</dd><dt>Trajet</dt><dd>{{ item.pickupLocation }} → {{ item.dropoffLocation }}</dd></dl>
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="fields"><label class="field"><span>Validité du devis</span><input type="datetime-local" formControlName="validUntil" required></label><label class="field"><span>Remise agence</span><input type="number" min="0" step="0.001" formControlName="discountAmount"></label></div>
          @if (options()?.withDriverAvailable) { <label class="check"><input type="checkbox" formControlName="withDriver">Inclure le chauffeur</label> }
          @if (services.length > 0) { <fieldset><legend>Services configurés</legend><div formArrayName="services">@for (control of services.controls; track $index) { <div class="service" [formGroupName]="$index"><label class="check"><input type="checkbox" formControlName="selected">{{ control.controls.name.value }} <span>{{ money(control.controls.price.value, item.currency) }}</span></label><label class="quantity">Quantité<input type="number" formControlName="quantity" min="1" max="1000"></label></div> }</div></fieldset> }
          <label class="field"><span>Note visible au client</span><textarea formControlName="customerNote" maxlength="2000"></textarea></label><label class="field"><span>Note interne</span><textarea formControlName="internalNote" maxlength="2000"></textarea></label>
          <button class="button button-primary" type="submit" [disabled]="busy() || loading()">{{ busy() ? 'Enregistrement…' : 'Enregistrer le brouillon' }}</button>
        </form>
      }
    </section>`,
  styles:`.editor { max-width:950px; margin:auto; padding:1.5rem 0; } h2 { font:500 1.7rem var(--font-display); } .context { display:grid; grid-template-columns:140px 1fr; gap:.6rem; font-size:.8rem; border-bottom:1px solid var(--color-line); padding-bottom:1.2rem; } dt {color:var(--color-muted);} dd {margin:0;overflow-wrap:anywhere;} form {display:grid;gap:1rem;} .fields {display:grid;grid-template-columns:1fr 1fr;gap:1rem;} .field {min-width:0;} .field>span {font-size:.75rem;} input {min-width:0;} .check {display:flex;align-items:center;gap:.6rem;font-size:.8rem;} .check input {accent-color:var(--color-olive);} fieldset {border:1px solid var(--color-line);} legend {font-size:.8rem;} .service {display:flex;justify-content:space-between;gap:.8rem;padding:.5rem 0;flex-wrap:wrap;} .quantity {display:flex;gap:.5rem;align-items:center;font-size:.75rem;} .quantity input {width:75px;padding:.4rem;border:1px solid var(--color-line);} .service span {color:var(--color-muted);} form>.button {justify-self:start;} @media(max-width:600px){.fields{grid-template-columns:1fr;}.context{grid-template-columns:100px 1fr;}h2{font-size:1.5rem;}}`,
})
export class QuoteEditorComponent implements OnInit {
  readonly quote = input<Quote | null>(null); readonly bookingId = input(''); readonly saved = output<Quote>();
  private readonly api=inject(QuoteApiService); private readonly bookingApi=inject(BookingApiService); private readonly router=inject(Router); private readonly route=inject(ActivatedRoute); private readonly builder=inject(FormBuilder);
  protected readonly booking=signal<Booking|null>(null); protected readonly options=signal<QuoteOptions|null>(null); protected readonly loading=signal(true); protected readonly busy=signal(false); protected readonly error=signal('');
  protected readonly date=bookingDate; protected readonly money=bookingMoney;
  protected readonly services = new FormArray<ReturnType<QuoteEditorComponent['serviceControl']>>([]);
  protected readonly form=this.builder.nonNullable.group({validUntil:['',Validators.required],withDriver:false,discountAmount:[0,[Validators.min(0)]],customerNote:['',Validators.maxLength(2000)],internalNote:['',Validators.maxLength(2000)],services:this.services});
  private serviceControl(service:QuoteOptions['services'][number]) { return this.builder.nonNullable.group({serviceId:service.id,name:service.name,price:service.price,selected:false,quantity:[1,[Validators.min(1),Validators.max(1000)]]}); }
  ngOnInit():void {
    const id=this.bookingId() || this.quote()?.bookingId || this.route.snapshot.queryParamMap.get('booking');
    if(!id){this.loading.set(false);this.error.set('Choisissez une demande depuis les réservations.');return;}
    this.bookingApi.get(id,true).subscribe({next:booking=>this.booking.set(booking),error:error=>this.fail(error)});
    this.api.options(id).subscribe({next:options=>{
      this.options.set(options); for(const service of options.services){const control=this.serviceControl(service);const previous=this.quote()?.lines.find(line=>line.metadata['serviceId']===service.id);if(previous)control.patchValue({selected:true,quantity:previous.quantity,price:previous.unitPrice});this.services.push(control);}
      const quote=this.quote();if(quote)this.form.patchValue({validUntil:Temporal.Instant.from(quote.validUntil).toZonedDateTimeISO(quote.businessTimeZone).toPlainDateTime().toString({smallestUnit:'minute'}),withDriver:quote.lines.some(line=>line.type==='DRIVER'),discountAmount:quote.discountAmount,customerNote:quote.customerNote??'',internalNote:quote.internalNote??''});this.loading.set(false);
    },error:error=>this.fail(error)});
  }
  protected save():void {
    this.form.markAllAsTouched();const booking=this.booking();if(!booking || this.form.invalid || this.busy()){this.error.set('Renseignez une validité et des valeurs valides.');return;}
    let validUntil:string;try{validUntil=toBookingInstant(this.form.controls.validUntil.value,booking.businessTimeZone);if(new Date(validUntil).getTime()<=Date.now())throw new Error();}catch{this.error.set('La validité doit être dans le futur.');return;}
    const value=this.form.getRawValue();const input:QuoteInput={validUntil,withDriver:value.withDriver,discountAmount:Number(value.discountAmount),customerNote:value.customerNote||null,internalNote:value.internalNote||null,services:value.services.filter(service=>service.selected).map(service=>({serviceId:service.serviceId,quantity:Number(service.quantity)}))};
    const quote=this.quote();const previous=this.route.snapshot.queryParamMap.get('previous');const request=quote?this.api.update(quote.id,input):previous?this.api.version(previous,input):this.api.create(booking.id,input);
    this.busy.set(true);this.error.set('');request.subscribe({next:result=>{this.busy.set(false);if(quote)this.saved.emit(result);else void this.router.navigate(['/admin/quotes',result.id]);},error:error=>this.fail(error)});
  }
  private fail(error:unknown):void{this.loading.set(false);this.busy.set(false);this.error.set(error instanceof Error?error.message:'La demande a échoué.');}
}
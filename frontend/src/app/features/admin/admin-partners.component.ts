import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { AssignmentSummary, PartnerApiService, PartnerSummary } from '../../core/services/partner-api.service';
import { apiErrorMessage, partnerFormErrors, PartnerFormValue, toPartnerRequest } from '../partners/partner-validation';

@Component({
  selector: 'app-admin-partners',
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <section class="partners-page" aria-labelledby="partners-title">
      <header><p class="eyebrow">ADMINISTRATION <span>/</span> PARTENAIRES</p><h1 id="partners-title">Partenaires <em>et affectations.</em></h1><p>Gestion des partenaires, de leur compte lié et des véhicules qui leur sont confiés.</p></header>
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }
      @if (success()) { <p class="alert alert-success" role="status">{{ success() }}</p> }
      @if (loading()) { <p role="status">Chargement des partenaires…</p> }
      @if (!loading() && partners().length === 0) { <p class="empty-state">Aucun partenaire enregistré.</p> }
      @if (partners().length > 0) {
        <div class="table-wrap"><table><thead><tr><th>Partenaire</th><th>Type</th><th>Contact</th><th>Statut</th><th>Modifié</th>@if (canManage()) {<th><span class="sr-only">Actions</span></th>}</tr></thead><tbody>
          @for (partner of partners(); track partner.id) {
            <tr>
              <td><strong>{{ partner.name }}</strong>@if (partner.taxIdentifier) {<small>MF {{ partner.taxIdentifier }}</small>}</td>
              <td>{{ partner.partnerType === 'COMPANY' ? 'Société' : 'Particulier' }}</td>
              <td>{{ partner.email || '—' }}<small>{{ partner.phone || '' }}</small></td>
              <td><span class="status" [class.inactive]="!partner.active"><i></i>{{ partner.active ? 'Actif' : 'Désactivé' }}</span></td>
              <td>{{ partner.updatedAt | date:'mediumDate' }}</td>
              @if (canManage()) {
                <td class="row-actions">
                  <button type="button" class="button button-secondary" (click)="edit(partner)" [disabled]="busy()">Modifier</button>
                  @if (partner.active) {
                    <button type="button" class="button button-secondary" (click)="toggle(partner)" [disabled]="busy()">Désactiver</button>
                  } @else {
                    <button type="button" class="button button-secondary" (click)="toggle(partner)" [disabled]="busy()">Réactiver</button>
                  }
                </td>
              }
            </tr>
          }
        </tbody></table></div>
        <nav class="pagination" aria-label="Pagination des partenaires">
          <button type="button" class="button button-secondary" [disabled]="loading() || page() === 0" (click)="load(page() - 1)">Précédent</button>
          <span>Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button type="button" class="button button-secondary" [disabled]="loading() || page() + 1 >= totalPages()" (click)="load(page() + 1)">Suivant</button>
        </nav>
      }

      @if (canManage()) {
        <section class="panel" aria-labelledby="partner-form-title">
          <h2 id="partner-form-title">{{ editingId() ? 'Modifier le partenaire' : 'Nouveau partenaire' }}</h2>
          @if (formErrors().length > 0) { <ul class="alert alert-error" role="alert">@for (message of formErrors(); track message) {<li>{{ message }}</li>}</ul> }
          <form class="grid-form" [formGroup]="form" (ngSubmit)="save()">
            <label class="field"><span>Type</span><select formControlName="partnerType"><option value="INDIVIDUAL">Particulier</option><option value="COMPANY">Société</option></select></label>
            <label class="field"><span>Nom ou raison sociale *</span><input formControlName="name" maxlength="160" required></label>
            <label class="field"><span>Matricule fiscal {{ form.controls.partnerType.value === 'COMPANY' ? '*' : '(société uniquement)' }}</span><input formControlName="taxIdentifier" maxlength="40"></label>
            <label class="field"><span>E-mail</span><input formControlName="email" maxlength="254" type="email"></label>
            <label class="field"><span>Téléphone</span><input formControlName="phone" maxlength="40"></label>
            <label class="field"><span>Ville</span><input formControlName="city" maxlength="100"></label>
            <label class="field wide"><span>Adresse</span><input formControlName="address" maxlength="255"></label>
            <label class="field wide"><span>Identifiant du compte PARTNER lié (UUID, facultatif)</span><input formControlName="userId" maxlength="36"></label>
            <div class="form-actions">
              <button type="submit" class="button button-primary" [disabled]="busy()">{{ editingId() ? 'Enregistrer' : 'Créer le partenaire' }}</button>
              @if (editingId()) { <button type="button" class="button button-secondary" (click)="resetForm()">Annuler</button> }
            </div>
          </form>
        </section>
      }

      @if (canRead()) {
        <section class="panel" aria-labelledby="assignment-title">
          <h2 id="assignment-title">Affectation d’un véhicule</h2>
          <p class="hint">Aucune affectation n’est automatique. L’historique conserve toutes les anciennes affectations.</p>
          <form class="grid-form" [formGroup]="vehicleForm" (ngSubmit)="loadAssignments()">
            <label class="field wide"><span>Identifiant du véhicule</span><input formControlName="vehicleId" maxlength="36" required></label>
            <div class="form-actions"><button type="submit" class="button button-secondary" [disabled]="busy()">Afficher l’historique</button></div>
          </form>
          @if (assignmentError()) { <p class="alert alert-error" role="alert">{{ assignmentError() }}</p> }
          @if (currentVehicleId()) {
            @if (assignments().length === 0) { <p class="empty-state">Aucune affectation enregistrée pour ce véhicule.</p> }
            @if (assignments().length > 0) {
              <div class="table-wrap"><table><thead><tr><th>Partenaire</th><th>Début</th><th>Fin</th><th>Motif</th></tr></thead><tbody>
                @for (item of assignments(); track item.id) {
                  <tr><td><strong>{{ item.partnerName }}</strong>@if (!item.endedAt) {<small>Partenaire actuel</small>}</td><td>{{ item.assignedAt | date:'medium' }}</td><td>{{ item.endedAt ? (item.endedAt | date:'medium') : '—' }}</td><td>{{ item.reason }}</td></tr>
                }
              </tbody></table></div>
            }
            @if (canManage()) {
              <form class="grid-form" [formGroup]="assignForm" (ngSubmit)="assign()">
                <label class="field"><span>Nouveau partenaire actif</span><select formControlName="partnerId"><option value="">Choisir…</option>@for (partner of activePartners(); track partner.id) {<option [value]="partner.id">{{ partner.name }}</option>}</select></label>
                <label class="field"><span>Motif *</span><input formControlName="reason" maxlength="500"></label>
                <div class="form-actions"><button type="submit" class="button button-primary" [disabled]="busy()">Affecter ou transférer</button></div>
              </form>
            }
          }
        </section>
      }
    </section>
  `,
  styles: `
    .partners-page { max-width:1200px; margin:auto; padding:clamp(1.5rem,4vw,3.5rem); }
    header { margin-bottom:2rem; } .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); } h1 { margin:.8rem 0 .5rem; font:500 clamp(2.3rem,5vw,4rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); } header>p:last-child, .hint { color:var(--color-muted); font-size:.82rem; }
    .table-wrap { overflow:auto; } table { width:100%; border-collapse:collapse; text-align:left; } th,td { padding:.85rem .65rem; border-bottom:1px solid var(--color-line); font-size:.78rem; vertical-align:top; }
    th { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } td strong { font-weight:600; } td small { display:block; margin-top:.2rem; color:var(--color-muted); }
    .status { display:inline-flex; align-items:center; gap:.4rem; color:var(--color-olive); } .status i { width:.45rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status.inactive { color:var(--color-muted); } .status.inactive i { background:#999; }
    .row-actions { display:flex; gap:.5rem; flex-wrap:wrap; }
    .pagination { display:flex; align-items:center; justify-content:flex-end; gap:1rem; margin-top:1rem; font-size:.75rem; }
    .panel { margin-top:2.5rem; padding:1.5rem; border:1px solid var(--color-line); } .panel h2 { margin:0 0 .75rem; font:500 1.4rem/1.2 var(--font-display); }
    .grid-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:.9rem; margin-top:1rem; }
    .field { display:grid; gap:.35rem; font-size:.76rem; font-weight:600; } .field.wide { grid-column:1 / -1; }
    .field input, .field select { padding:.65rem; border:1px solid var(--color-line); font:inherit; font-weight:400; }
    .form-actions { grid-column:1 / -1; display:flex; gap:.75rem; flex-wrap:wrap; }
    .sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0 0 0 0); }
    @media(max-width:700px) { .partners-page { padding:1.25rem; } .grid-form { grid-template-columns:1fr; } table { min-width:640px; } }
  `,
})
export class AdminPartnersComponent implements OnInit {
  private readonly api = inject(PartnerApiService);
  private readonly auth = inject(AuthService);
  private readonly builder = inject(FormBuilder).nonNullable;

  protected readonly canManage = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER']));
  protected readonly canRead = computed(() => this.auth.hasAnyRole(['SUPER_ADMIN', 'MANAGER', 'AGENT']));
  protected readonly partners = signal<PartnerSummary[]>([]);
  protected readonly activePartners = computed(() => this.partners().filter((partner) => partner.active));
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly editingId = signal<string | null>(null);
  protected readonly formErrors = signal<string[]>([]);
  protected readonly assignments = signal<AssignmentSummary[]>([]);
  protected readonly currentVehicleId = signal<string | null>(null);
  protected readonly assignmentError = signal('');

  protected readonly form = this.builder.group({
    partnerType: ['INDIVIDUAL' as PartnerFormValue['partnerType']],
    name: [''],
    email: [''],
    phone: [''],
    address: [''],
    city: [''],
    taxIdentifier: [''],
    userId: [''],
  });
  protected readonly vehicleForm = this.builder.group({ vehicleId: [''] });
  protected readonly assignForm = this.builder.group({ partnerId: [''], reason: [''] });

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set('');
    this.api.listPartners(page).subscribe({
      next: (result) => {
        this.partners.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(apiErrorMessage(error, 'Impossible de charger les partenaires.'));
        this.loading.set(false);
      },
    });
  }

  protected edit(partner: PartnerSummary): void {
    this.editingId.set(partner.id);
    this.formErrors.set([]);
    this.form.setValue({
      partnerType: partner.partnerType,
      name: partner.name,
      email: partner.email ?? '',
      phone: partner.phone ?? '',
      address: partner.address ?? '',
      city: partner.city ?? '',
      taxIdentifier: partner.taxIdentifier ?? '',
      userId: partner.userId ?? '',
    });
  }

  protected resetForm(): void {
    this.editingId.set(null);
    this.formErrors.set([]);
    this.form.reset({ partnerType: 'INDIVIDUAL' });
  }

  protected save(): void {
    const value = this.form.getRawValue();
    const errors = partnerFormErrors(value);
    this.formErrors.set(errors);
    if (errors.length > 0 || this.busy()) return;
    const request = toPartnerRequest(value);
    const id = this.editingId();
    this.busy.set(true);
    this.success.set('');
    const call = id ? this.api.updatePartner(id, request) : this.api.createPartner(request);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set(id ? 'Partenaire mis à jour.' : 'Partenaire créé.');
        this.resetForm();
        this.load(this.page());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.formErrors.set([apiErrorMessage(error, 'L’enregistrement du partenaire a échoué.')]);
      },
    });
  }

  protected toggle(partner: PartnerSummary): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.success.set('');
    const call = partner.active ? this.api.deactivatePartner(partner.id) : this.api.activatePartner(partner.id);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set(partner.active ? 'Partenaire désactivé.' : 'Partenaire réactivé.');
        this.load(this.page());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(error, 'L’opération sur le partenaire a échoué.'));
      },
    });
  }

  protected loadAssignments(): void {
    const vehicleId = this.vehicleForm.controls.vehicleId.value.trim();
    this.assignmentError.set('');
    if (!vehicleId) {
      this.assignmentError.set('L’identifiant du véhicule est obligatoire.');
      return;
    }
    this.currentVehicleId.set(vehicleId);
    this.api.getVehicleAssignments(vehicleId).subscribe({
      next: (items) => this.assignments.set(items),
      error: (error: unknown) => {
        this.assignments.set([]);
        this.assignmentError.set(apiErrorMessage(error, 'Impossible de charger l’historique.'));
      },
    });
  }

  protected assign(): void {
    const vehicleId = this.currentVehicleId();
    const { partnerId, reason } = this.assignForm.getRawValue();
    if (!vehicleId) return;
    if (!partnerId || !reason.trim()) {
      this.assignmentError.set('Le partenaire et le motif sont obligatoires.');
      return;
    }
    this.busy.set(true);
    this.assignmentError.set('');
    this.api.assignVehicle(vehicleId, { partnerId, reason: reason.trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.assignForm.reset({ partnerId: '', reason: '' });
        this.loadAssignments();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.assignmentError.set(apiErrorMessage(error, 'L’affectation a échoué.'));
      },
    });
  }
}

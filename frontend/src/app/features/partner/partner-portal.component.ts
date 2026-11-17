import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { GuaranteeSummary, PartnerApiService, PartnerSummary, PartnerVehicleSummary } from '../../core/services/partner-api.service';
import { apiErrorMessage } from '../partners/partner-validation';

@Component({
  selector: 'app-partner-portal',
  imports: [DatePipe],
  template: `
    <section class="portal-page" aria-labelledby="portal-title">
      <header><p class="eyebrow">ESPACE PARTENAIRE</p><h1 id="portal-title">Vos véhicules <em>et garanties.</em></h1><p class="read-only-note">Accès en lecture seule. Les modifications sont effectuées par l’équipe Elite Tunisia.</p></header>
      @if (loading()) { <p role="status">Chargement de votre espace…</p> }
      @if (error()) { <p class="alert alert-error" role="alert">{{ error() }}</p> }

      @if (partner(); as profile) {
        <section class="panel" aria-labelledby="profile-title">
          <h2 id="profile-title">Profil</h2>
          <dl class="profile">
            <div><dt>Nom</dt><dd>{{ profile.name }}</dd></div>
            <div><dt>Type</dt><dd>{{ profile.partnerType === 'COMPANY' ? 'Société' : 'Particulier' }}</dd></div>
            <div><dt>Statut</dt><dd>{{ profile.active ? 'Actif' : 'Désactivé' }}</dd></div>
            <div><dt>E-mail</dt><dd>{{ profile.email || '—' }}</dd></div>
            <div><dt>Téléphone</dt><dd>{{ profile.phone || '—' }}</dd></div>
            <div><dt>Ville</dt><dd>{{ profile.city || '—' }}</dd></div>
          </dl>
        </section>
      }

      @if (!loading() && !error()) {
        <section class="panel" aria-labelledby="vehicles-title">
          <h2 id="vehicles-title">Véhicules confiés</h2>
          @if (vehicles().length === 0) { <p class="empty-state">Aucun véhicule ne vous est actuellement affecté.</p> }
          @if (vehicles().length > 0) {
            <div class="table-wrap"><table><thead><tr><th>Véhicule</th><th>Année</th><th>Statut</th></tr></thead><tbody>
              @for (vehicle of vehicles(); track vehicle.id) {
                <tr><td><strong>{{ vehicle.brandName }} {{ vehicle.model }}</strong><small>{{ vehicle.id }}</small></td><td>{{ vehicle.year }}</td><td>{{ vehicle.status }}</td></tr>
              }
            </tbody></table></div>
          }
        </section>

        <section class="panel" aria-labelledby="guarantees-title">
          <h2 id="guarantees-title">Garanties</h2>
          @if (guarantees().length === 0) { <p class="empty-state">Aucune garantie enregistrée.</p> }
          @if (guarantees().length > 0) {
            <div class="table-wrap"><table><thead><tr><th>Véhicule</th><th>Période</th><th>Statut</th><th>Motif</th></tr></thead><tbody>
              @for (guarantee of guarantees(); track guarantee.id) {
                <tr><td><code>{{ guarantee.vehicleId }}</code></td><td>{{ guarantee.startsAt | date:'medium' }}<small>au {{ guarantee.endsAt | date:'medium' }}</small></td><td>{{ guarantee.status === 'ACTIVE' ? 'Active' : 'Retirée' }}</td><td>{{ guarantee.reason }}</td></tr>
              }
            </tbody></table></div>
          }
        </section>
      }
    </section>
  `,
  styles: `
    .portal-page { max-width:1100px; margin:auto; padding:clamp(1.5rem,4vw,3.5rem); }
    .eyebrow { color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.11em; }
    h1 { margin:.8rem 0 .5rem; font:500 clamp(2.2rem,5vw,3.6rem)/1.05 var(--font-display); } h1 em { color:var(--color-olive); }
    .read-only-note { color:var(--color-muted); font-size:.82rem; }
    .panel { margin-top:2rem; padding:1.5rem; border:1px solid var(--color-line); } .panel h2 { margin:0 0 .9rem; font:500 1.4rem/1.2 var(--font-display); }
    .profile { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:1rem; margin:0; }
    .profile dt { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } .profile dd { margin:.25rem 0 0; font-size:.88rem; }
    .table-wrap { overflow:auto; } table { width:100%; border-collapse:collapse; text-align:left; } th,td { padding:.8rem .6rem; border-bottom:1px solid var(--color-line); font-size:.78rem; vertical-align:top; }
    th { color:var(--color-muted); font-size:.68rem; text-transform:uppercase; letter-spacing:.06em; } td small { display:block; margin-top:.2rem; color:var(--color-muted); } td code { font-size:.72rem; }
    @media(max-width:700px) { .portal-page { padding:1.25rem; } table { min-width:540px; } }
  `,
})
export class PartnerPortalComponent implements OnInit {
  private readonly api = inject(PartnerApiService);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly partner = signal<PartnerSummary | null>(null);
  protected readonly vehicles = signal<PartnerVehicleSummary[]>([]);
  protected readonly guarantees = signal<GuaranteeSummary[]>([]);

  ngOnInit(): void {
    this.api.getMyPartner().subscribe({
      next: (profile) => {
        this.partner.set(profile);
        this.api.getMyVehicles().subscribe({ next: (items) => this.vehicles.set(items), error: (error: unknown) => this.fail(error) });
        this.api.getMyGuarantees().subscribe({
          next: (items) => {
            this.guarantees.set(items);
            this.loading.set(false);
          },
          error: (error: unknown) => this.fail(error),
        });
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  private fail(error: unknown): void {
    this.error.set(apiErrorMessage(error, 'Impossible de charger votre espace partenaire.'));
    this.loading.set(false);
  }
}

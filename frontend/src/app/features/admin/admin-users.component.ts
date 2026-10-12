import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { API_BASE_URL } from '../../core/config/api.config';
import { AuthService } from '../../core/auth/auth.service';
import { ApiHttpError } from '../../core/models/api-http-error';

interface RoleSummary {
  roleKey: string;
  displayName: string;
  permissions: string[];
}

interface UserSummary {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  jobTitle: string | null;
  roles: string[];
  status: 'ACTIVE' | 'DISABLED';
}

interface UserPage {
  content: UserSummary[];
  totalElements: number;
}

@Component({
  selector: 'app-admin-users',
  imports: [ReactiveFormsModule],
  template: `
    <section class="users-page" aria-labelledby="users-title">
      <header class="page-heading"><div><p class="eyebrow">ADMINISTRATION <span>/</span> ACCÈS</p><h1 id="users-title">Utilisateurs <em>internes.</em></h1><p>Comptes d’équipe, rôles et accès à la plateforme.</p></div><span class="count-label">{{ users().length }} comptes</span></header>
      @if (errorMessage()) { <p class="alert alert-error" role="alert">{{ errorMessage() }}</p> }
      @if (successMessage()) { <p class="alert alert-success" role="status">{{ successMessage() }}</p> }
      <div class="users-layout">
        <section class="users-list" aria-label="Liste des utilisateurs">
          @if (loading()) { <p class="loading-state" role="status">Chargement des utilisateurs…</p> }
          @if (!loading() && users().length === 0) { <div class="empty-state"><strong>Aucun compte interne</strong><p>Les utilisateurs de l’équipe apparaîtront ici.</p></div> }
          @if (users().length > 0) {
            <div class="table-wrap"><table><thead><tr><th>Utilisateur</th><th>Rôle</th><th>Statut</th><th>Actions</th></tr></thead><tbody>
              @for (user of users(); track user.id) {
                <tr>
                  <td><strong>{{ user.firstName }} {{ user.lastName }}</strong><small>{{ user.email }}</small></td>
                  <td><span class="badge">{{ user.roles[0] }}</span></td>
                  <td><span class="status" [class.status-disabled]="user.status === 'DISABLED'"><i></i>{{ user.status === 'ACTIVE' ? 'Actif' : 'Désactivé' }}</span></td>
                  <td class="actions"><button class="text-action" type="button" (click)="edit(user)">Modifier</button><button class="text-action" type="button" [disabled]="user.id === currentUserId()" (click)="toggle(user)">{{ user.status === 'ACTIVE' ? 'Désactiver' : 'Réactiver' }}</button></td>
                </tr>
              }
            </tbody></table></div>
          }
        </section>
        <section class="editor" aria-labelledby="editor-title">
          <p class="eyebrow">{{ editingId() ? 'MODIFICATION' : 'NOUVEAU COMPTE' }}</p>
          <h2 id="editor-title">{{ editingId() ? 'Modifier un utilisateur' : 'Créer un utilisateur' }}</h2>
          <form [formGroup]="form" (ngSubmit)="save()" novalidate>
            <div class="field"><label for="user-first">Prénom</label><input id="user-first" formControlName="firstName" autocomplete="given-name"></div>
            <div class="field"><label for="user-last">Nom</label><input id="user-last" formControlName="lastName" autocomplete="family-name"></div>
            <div class="field"><label for="user-email">Adresse e-mail</label><input id="user-email" type="email" formControlName="email" autocomplete="email"></div>
            <div class="field"><label for="user-phone">Téléphone</label><input id="user-phone" type="tel" formControlName="phone" autocomplete="tel"></div>
            <div class="field"><label for="user-role">Rôle</label><select id="user-role" formControlName="role"><option value="" disabled>Choisir un rôle</option>@for (role of roles(); track role.roleKey) { <option [value]="role.roleKey">{{ role.displayName }}</option> }</select></div>
            <div class="field"><label for="user-job">Intitulé du poste</label><input id="user-job" formControlName="jobTitle"></div>
            @if (!editingId()) { <div class="field"><label for="user-password">Mot de passe provisoire</label><input id="user-password" type="password" formControlName="password" autocomplete="new-password"><small>12 caractères, majuscule, minuscule, chiffre et symbole.</small></div> }
            <div class="editor-actions"><button class="button button-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Enregistrement…' : editingId() ? 'Enregistrer' : 'Créer le compte' }}</button>@if (editingId()) { <button class="button button-secondary" type="button" (click)="cancelEdit()">Annuler</button> }</div>
          </form>
        </section>
      </div>
    </section>
  `,
  styles: `
    .users-page { max-width:1400px; margin:0 auto; padding:clamp(1.5rem,4vw,3.5rem); }
    .page-heading { display:flex; align-items:end; justify-content:space-between; gap:1rem; margin-bottom:2rem; }
    .eyebrow { margin:0; color:var(--color-olive); font-size:.68rem; font-weight:700; letter-spacing:.12em; }
    .eyebrow span { margin:0 .45rem; color:var(--color-metal); }
    h1 { margin:.8rem 0 .5rem; font:500 clamp(2.3rem,5vw,4rem)/1.05 var(--font-display); }
    h1 em { color:var(--color-olive); }
    .page-heading p:last-child { margin:0; color:var(--color-muted); font-size:.82rem; }
    .count-label { padding:.55rem .75rem; border:1px solid var(--color-line); color:var(--color-muted); font-size:.72rem; white-space:nowrap; }
    .users-layout { display:grid; grid-template-columns:minmax(0,1.7fr) minmax(280px,.8fr); gap:1.5rem; align-items:start; }
    .users-list { min-width:0; }
    .table-wrap { max-height:72vh; overflow:auto; }
    td strong { font:500 .9rem var(--font-body); }
    td small { display:block; margin-top:.2rem; color:var(--color-muted); font-size:.72rem; }
    .status { display:inline-flex; align-items:center; gap:.45rem; color:var(--color-olive); font-size:.74rem; }
    .status i { width:.43rem; aspect-ratio:1; border-radius:50%; background:#66865e; }
    .status-disabled { color:var(--color-muted); }
    .status-disabled i { background:#999; }
    .actions { display:flex; gap:.8rem; }
    .text-action { padding:0; border:0; background:none; color:var(--color-olive); cursor:pointer; font-size:.73rem; text-decoration:underline; text-underline-offset:3px; }
    .text-action:disabled { color:#999; cursor:not-allowed; }
    .editor { padding:1.35rem; border:1px solid var(--color-line); background:#f1f1ec; }
    .editor h2 { margin:.6rem 0 1rem; font:500 1.45rem var(--font-display); }
    .editor form { display:grid; gap:.8rem; }
    .field { display:grid; gap:.35rem; }
    .field label { font-size:.73rem; font-weight:600; }
    .field input, .field select { min-height:40px; width:100%; padding:.55rem .65rem; border:1px solid var(--color-line); background:#fff; }
    .field small { color:var(--color-muted); font-size:.65rem; line-height:1.5; }
    .editor-actions { display:flex; flex-wrap:wrap; gap:.55rem; margin-top:.35rem; }
    .editor-actions .button { min-height:40px; font-size:.73rem; }
    @media(max-width:1050px) { .users-layout { grid-template-columns:1fr; } .editor { max-width:620px; } }
    @media(max-width:600px) { .users-page { padding:1.25rem; } .page-heading { align-items:flex-start; flex-direction:column; } .table-wrap table { min-width:740px; } }
  `,
})
export class AdminUsersComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly builder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly currentUserId = computed(() => this.auth.session()?.userId ?? '');
  protected readonly form = this.builder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.required, Validators.pattern(/^\+?[0-9][0-9 ()-]{7,38}$/)]],
    role: ['', Validators.required],
    jobTitle: ['', [Validators.required, Validators.maxLength(80)]],
    password: ['', [Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$/)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.http.get<UserPage>(`${this.baseUrl}/api/admin/users`, { params: new HttpParams().set('page', 0).set('size', 100) }).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof Error ? error.message : 'Impossible de charger les utilisateurs.');
        this.loading.set(false);
      },
    });
    this.http.get<RoleSummary[]>(`${this.baseUrl}/api/admin/roles`).subscribe({
      next: (roles) => {
        this.roles.set(roles.filter((role) => role.roleKey !== 'CUSTOMER'));
        if (!this.form.controls.role.value && roles.length) this.form.controls.role.setValue(roles.find((role) => role.roleKey === 'AGENT')?.roleKey ?? '');
      },
      error: (error: unknown) => this.errorMessage.set(error instanceof Error ? error.message : 'Impossible de charger les rôles.'),
    });
  }

  protected edit(user: UserSummary): void {
    this.editingId.set(user.id);
    this.form.controls.password.clearValidators();
    this.form.controls.password.updateValueAndValidity();
    this.form.patchValue({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      phone: user.phone,
      role: user.roles[0] ?? '',
      jobTitle: user.jobTitle ?? '',
      password: '',
    });
    this.successMessage.set('');
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({ firstName: '', lastName: '', email: '', phone: '', role: 'AGENT', jobTitle: '', password: '' });
    this.form.controls.password.setValidators([Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$/)]);
    this.form.controls.password.updateValueAndValidity();
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    const { password, ...profile } = this.form.getRawValue();
    const id = this.editingId();
    const request = id
      ? this.http.put<UserSummary>(`${this.baseUrl}/api/admin/users/${id}`, profile)
      : this.http.post<UserSummary>(`${this.baseUrl}/api/admin/users`, { ...profile, password });
    request.subscribe({
      next: () => {
        this.successMessage.set(id ? 'Les informations ont été mises à jour.' : 'Le compte employé a été créé.');
        this.saving.set(false);
        this.cancelEdit();
        this.load();
      },
      error: (error: unknown) => {
        this.errorMessage.set(error instanceof ApiHttpError ? error.message : 'Impossible d’enregistrer cet utilisateur.');
        this.saving.set(false);
      },
    });
  }

  protected toggle(user: UserSummary): void {
    this.http.patch<UserSummary>(`${this.baseUrl}/api/admin/users/${user.id}/status`, { enabled: user.status !== 'ACTIVE' }).subscribe({
      next: () => {
        this.successMessage.set(user.status === 'ACTIVE' ? 'Le compte a été désactivé.' : 'Le compte a été réactivé.');
        this.load();
      },
      error: (error: unknown) => this.errorMessage.set(error instanceof Error ? error.message : 'Impossible de modifier ce statut.'),
    });
  }
}

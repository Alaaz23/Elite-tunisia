import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { AdminLayoutComponent } from './layout/admin-layout/admin-layout.component';
import { CustomerLayoutComponent } from './layout/customer-layout/customer-layout.component';
import { PublicLayoutComponent } from './layout/public-layout/public-layout.component';

const page = () => import('./shared/components/page-placeholder/page-placeholder.component').then((module) => module.PagePlaceholderComponent);
const home = () => import('./features/home/home.component').then((module) => module.HomeComponent);
const login = () => import('./features/auth/login.component').then((module) => module.LoginComponent);
const register = () => import('./features/auth/register.component').then((module) => module.RegisterComponent);
const customerDashboard = () => import('./features/customer/customer-dashboard.component').then((module) => module.CustomerDashboardComponent);
const customerProfile = () => import('./features/customer/customer-profile.component').then((module) => module.CustomerProfileComponent);
const customerInvoices = () => import('./features/customer/customer-invoices.component').then((module) => module.CustomerInvoicesComponent);
const adminUsers = () => import('./features/admin/admin-users.component').then((module) => module.AdminUsersComponent);
const vehicleCatalogue = () => import('./features/vehicles/vehicle-catalogue.component').then((module) => module.VehicleCatalogueComponent);
const vehicleDetail = () => import('./features/vehicles/vehicle-detail.component').then((module) => module.VehicleDetailComponent);
const taxonomy = () => import('./features/brands/catalogue-taxonomy.component').then((module) => module.CatalogueTaxonomyComponent);
const adminTaxonomy = () => import('./features/admin/admin-taxonomy.component').then((module) => module.AdminTaxonomyComponent);
const adminVehicles = () => import('./features/admin/admin-vehicles.component').then((module) => module.AdminVehiclesComponent);
const adminInvoices = () => import('./features/admin/admin-invoices.component').then((module) => module.AdminInvoicesComponent);
const adminDrivers = () => import('./features/admin/admin-drivers.component').then((module) => module.AdminDriversComponent);
const adminCustomers = () => import('./features/admin/admin-customers.component').then((module) => module.AdminCustomersComponent);
const forbidden = () => import('./features/customer/forbidden.component').then((module) => module.ForbiddenComponent);
const bookingsList = () => import('./features/bookings/bookings-list.component').then((module) => module.BookingsListComponent);
const bookingDetail = () => import('./features/bookings/booking-detail.component').then((module) => module.BookingDetailComponent);
const quotesList = () => import('./features/quotes/quotes-list.component').then((module) => module.QuotesListComponent);
const quoteDetail = () => import('./features/quotes/quote-detail.component').then((module) => module.QuoteDetailComponent);
const quoteEditor = () => import('./features/quotes/quote-editor.component').then((module) => module.QuoteEditorComponent);

export const routes: Routes = [
	{
		path: '',
		component: PublicLayoutComponent,
		children: [
			{ path: '', pathMatch: 'full', loadComponent: home },
			{ path: 'vehicles', loadComponent: vehicleCatalogue },
			{ path: 'vehicles/:id', loadComponent: vehicleDetail },
			{ path: 'brands', loadComponent: taxonomy, data: { catalogueType: 'brands' } },
			{ path: 'categories', loadComponent: taxonomy, data: { catalogueType: 'categories' } },
			{ path: 'chauffeur', loadComponent: page, data: { title: 'Chauffeur privé', description: 'Les demandes de chauffeur ne sont pas encore connectées.' } },
			{ path: 'airport-transfer', loadComponent: page, data: { title: 'Transfert aéroport', description: 'Les demandes de transfert ne sont pas encore connectées.' } },
			{ path: 'services', loadComponent: page, data: { title: 'Services', description: 'Les services seront configurables depuis le back-office.' } },
			{ path: 'about', loadComponent: page, data: { title: 'À propos', description: 'Le contenu éditorial sera administrable.' } },
			{ path: 'faq', loadComponent: page, data: { title: 'Questions fréquentes', description: 'La base FAQ sera ajoutée avec le contenu métier validé.' } },
			{ path: 'contact', loadComponent: page, data: { title: 'Contact', description: 'Le formulaire de contact sera connecté à l’API.' } },
			{ path: 'forbidden', loadComponent: forbidden },
		],
	},
	{
		path: 'auth',
		component: PublicLayoutComponent,
		children: [
			{ path: 'login', loadComponent: login },
			{ path: 'admin-login', loadComponent: login, data: { adminMode: true } },
			{ path: 'register', loadComponent: register },
			{ path: 'forgot-password', loadComponent: page, data: { title: 'Mot de passe oublié', description: 'La récupération de compte reste à implémenter.' } },
		],
	},
	{
		path: 'customer',
		component: CustomerLayoutComponent,
		canActivate: [authGuard, roleGuard],
		data: { roles: ['CUSTOMER'] },
		children: [
			{ path: 'dashboard', loadComponent: customerDashboard },
			{ path: 'bookings', loadComponent: bookingsList },
			{ path: 'bookings/:id', loadComponent: bookingDetail },
			{ path: 'quotes', loadComponent: quotesList },
			{ path: 'quotes/:id', loadComponent: quoteDetail },
			{ path: 'invoices', loadComponent: customerInvoices, data: { title: 'Mes factures' } },
			{ path: 'profile', loadComponent: customerProfile },
			{ path: 'notifications', loadComponent: page, data: { title: 'Notifications', description: 'Les notifications client restent à implémenter.' } },
			{ path: '', pathMatch: 'full', redirectTo: 'dashboard' },
		],
	},
	{
		path: 'admin',
		component: AdminLayoutComponent,
		canActivate: [authGuard, roleGuard],
		data: { roles: ['SUPER_ADMIN', 'MANAGER', 'AGENT', 'CONCIERGE', 'ACCOUNTANT'] },
		children: [
			{ path: '', pathMatch: 'full', loadComponent: page, data: { title: 'Vue d’ensemble', description: 'Les opérations de gestion des utilisateurs sont disponibles; le tableau de bord métier viendra plus tard.' } },
			{ path: 'vehicles', loadComponent: adminVehicles },
			{ path: 'brands', loadComponent: adminTaxonomy, data: { catalogueType: 'brands' } },
			{ path: 'categories', loadComponent: adminTaxonomy, data: { catalogueType: 'categories' } },
			{ path: 'bookings', loadComponent: bookingsList, data: { adminBookings: true } },
			{ path: 'bookings/:id', loadComponent: bookingDetail, data: { adminBookings: true } },
			{ path: 'customers', loadComponent: adminCustomers, canActivate: [roleGuard], data: { roles: ['SUPER_ADMIN', 'MANAGER'] } },
			{ path: 'drivers', loadComponent: adminDrivers, canActivate: [roleGuard], data: { roles: ['SUPER_ADMIN', 'MANAGER', 'AGENT'] } },
			{ path: 'quotes', loadComponent: quotesList, data: { adminQuotes: true } },
			{ path: 'quotes/new', loadComponent: quoteEditor, canActivate: [roleGuard], data: { adminQuotes: true, roles: ['SUPER_ADMIN','MANAGER','AGENT'] } },
			{ path: 'quotes/:id', loadComponent: quoteDetail, data: { adminQuotes: true } },
			{ path: 'invoices', loadComponent: adminInvoices, data: { title: 'Factures' } },
			{ path: 'payments', loadComponent: page, data: { title: 'Paiements', description: 'Aucun fournisseur de paiement n’est configuré.' } },
			{ path: 'services', loadComponent: page, data: { title: 'Services', description: 'Le CRUD services reste à développer.' } },
			{ path: 'partners', loadComponent: page, data: { title: 'Partenaires', description: 'La gestion des partenaires reste à développer.' } },
			{ path: 'users', loadComponent: adminUsers, canActivate: [roleGuard], data: { roles: ['SUPER_ADMIN'] } },
			{ path: 'reports', loadComponent: page, data: { title: 'Rapports', description: 'Les rapports métier restent à développer.' } },
		],
	},
	{ path: '**', redirectTo: '' },
];

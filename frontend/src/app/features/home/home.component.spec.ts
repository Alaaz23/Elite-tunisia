import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { CategorySummary } from '../../core/services/catalogue-api.service';
import { HomeComponent } from './home.component';

const categories: CategorySummary[] = [
  { id: 'cat-1', name: 'Berline', slug: 'berline', description: 'Confort et silence', active: true },
  { id: 'cat-2', name: 'SUV', slug: 'suv', description: '', active: true },
];

function page(content: CategorySummary[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, first: true, last: true };
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function flushCategories(body: object, status = 200) {
    const request = http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/api/categories'));
    if (status === 200) {
      request.flush(body);
    } else {
      request.flush(body, { status, statusText: 'Erreur' });
    }
    fixture.detectChanges();
  }

  function element<T extends Element>(selector: string): T {
    const found = fixture.nativeElement.querySelector(selector) as T | null;
    if (!found) {
      throw new Error(`Élément introuvable : ${selector}`);
    }
    return found;
  }

  it('shows a loading state while categories are requested', () => {
    expect(fixture.nativeElement.textContent).toContain('Chargement des catégories');
    flushCategories(page(categories));
    expect(fixture.nativeElement.textContent).not.toContain('Chargement des catégories');
  });

  it('lists published categories with links to the filtered catalogue', () => {
    flushCategories(page(categories));

    const items = fixture.nativeElement.querySelectorAll('.category-item') as NodeListOf<HTMLAnchorElement>;
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Berline');
    expect(items[0].textContent).toContain('Confort et silence');
    expect(items[0].getAttribute('href')).toContain('/vehicles');
    expect(items[0].getAttribute('href')).toContain('category=berline');
    expect(items[1].getAttribute('href')).toContain('category=suv');
  });

  it('explains when no category is published', () => {
    flushCategories(page([]));

    expect(fixture.nativeElement.querySelectorAll('.category-item').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Aucune catégorie n’est publiée');
  });

  it('shows an explicit error and keeps the catalogue link when categories fail', () => {
    flushCategories({ message: 'boom' }, 500);

    expect(fixture.nativeElement.textContent).toContain('Les catégories ne sont pas disponibles');
    expect(element<HTMLAnchorElement>('.categories-section .section-heading a').getAttribute('href')).toBe('/vehicles');
  });

  it('navigates to the catalogue with trimmed search and selected category', () => {
    flushCategories(page(categories));

    element<HTMLInputElement>('input[formControlName="search"]').value = '  Porsche  ';
    element<HTMLInputElement>('input[formControlName="search"]').dispatchEvent(new Event('input'));
    const select = element<HTMLSelectElement>('select[formControlName="category"]');
    select.value = 'suv';
    select.dispatchEvent(new Event('change'));
    element<HTMLFormElement>('form.search-form').dispatchEvent(new Event('submit'));

    expect(router.navigate).toHaveBeenCalledWith(['/vehicles'], {
      queryParams: { search: 'Porsche', category: 'suv' },
    });
  });

  it('navigates to the full catalogue with no filters when the search is empty', () => {
    flushCategories(page(categories));

    element<HTMLFormElement>('form.search-form').dispatchEvent(new Event('submit'));

    expect(router.navigate).toHaveBeenCalledWith(['/vehicles'], {
      queryParams: { search: null, category: null },
    });
  });

  it('offers every loaded category in the category selector', () => {
    flushCategories(page(categories));

    const options = element<HTMLSelectElement>('select[formControlName="category"]').options;
    expect(options.length).toBe(3);
    expect(options[1].value).toBe('berline');
    expect(options[2].textContent).toContain('SUV');
  });
});

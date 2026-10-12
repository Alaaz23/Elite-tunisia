import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient()],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('registers the public, customer and admin route trees', () => {
    expect(routes.some((route) => route.path === '')).toBe(true);
    expect(routes.some((route) => route.path === 'customer' && route.canActivate?.length)).toBe(true);
    expect(routes.some((route) => route.path === 'admin' && route.canActivate?.length === 2)).toBe(true);
  });
});

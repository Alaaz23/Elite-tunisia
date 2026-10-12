import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';
import { RegisterComponent } from './register.component';

const authStub = {
  login: vi.fn(() => of({ accessToken: 'token', expiresIn: 900, userId: 'client-1', email: 'client@example.test', firstName: 'Client', lastName: 'Test', phone: '+216 20 123 456', roles: ['CUSTOMER'], status: 'ACTIVE' as const })),
  register: vi.fn(() => of({ accessToken: 'token', expiresIn: 900, userId: 'client-1', email: 'client@example.test', firstName: 'Client', lastName: 'Test', phone: '+216 20 123 456', roles: ['CUSTOMER'], status: 'ACTIVE' as const })),
};

describe('Authentication forms', () => {
  beforeEach(() => {
    authStub.login.mockClear();
    authStub.register.mockClear();
  });

  it('does not submit login with invalid fields', async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authStub }],
    }).compileComponents();
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('button[type="submit"]')?.dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(authStub.login).not.toHaveBeenCalled();
  });

  it('requires strong password and matching confirmation on registration', async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authStub }],
    }).compileComponents();
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance as unknown as { form: { controls: Record<string, { setValue(value: string): void }>; valid: boolean } };
    component.form.controls['firstName'].setValue('Client');
    component.form.controls['lastName'].setValue('Test');
    component.form.controls['email'].setValue('client@example.test');
    component.form.controls['phone'].setValue('+216 20 123 456');
    component.form.controls['password'].setValue('weak');
    component.form.controls['confirmPassword'].setValue('different');
    expect(component.form.valid).toBe(false);
    expect(authStub.register).not.toHaveBeenCalled();
  });
});
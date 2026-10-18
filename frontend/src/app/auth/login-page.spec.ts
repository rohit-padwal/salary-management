import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { LoginPage } from './login-page';
import { SessionService } from './session-service';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let result: Subject<{ username: string }>;
  let login: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let restore: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    result = new Subject();
    login = vi.fn(() => result);
    restore = vi.fn(() => of(null));
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: { user: signal(null), restore, login } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ returnUrl: '/analytics?countryCode=IN' }),
            },
          },
        },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function fill(username: string, password: string): void {
    for (const [name, value] of [
      ['username', username],
      ['password', password],
    ]) {
      const input = fixture.nativeElement.querySelector(
        `input[name="${name}"]`,
      ) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  }

  async function submit(): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }

  it('labels credential fields for browsers and password managers', () => {
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('h1')?.textContent).toBe('Sign in');
    expect(page.querySelector('input[name="username"]')?.getAttribute('autocomplete')).toBe(
      'username',
    );
    expect(page.querySelector('input[name="password"]')?.getAttribute('autocomplete')).toBe(
      'current-password',
    );
    expect(page.querySelector('input[name="password"]')?.getAttribute('type')).toBe('password');
  });

  it('requires both fields without contacting the login endpoint', async () => {
    await submit();
    expect(login).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Username is required.');
    expect(fixture.nativeElement.textContent).toContain('Password is required.');
  });

  it('submits exact credentials once, clears the password, and returns to the requested feature', async () => {
    fill('hr', ' pass & word ');
    await submit();
    await submit();
    expect(login).toHaveBeenCalledExactlyOnceWith('hr', ' pass & word ');
    expect(fixture.nativeElement.querySelector('input[name="password"]').value).toBe('');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    result.next({ username: 'hr' });
    result.complete();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/analytics?countryCode=IN', { replaceUrl: true });
  });

  it.each([
    [401, 'INVALID_CREDENTIALS', 'The username or password is incorrect.'],
    [403, 'CSRF_INVALID', 'The security token was rejected. Please try again.'],
    [0, '', 'Cannot reach the server. Check your connection and try again.'],
    [503, '', 'Sign-in is unavailable. Please try again.'],
  ])('shows a safe, distinct error for HTTP %s', async (status, code, message) => {
    fill('hr', 'incorrect');
    await submit();
    result.error(
      new HttpErrorResponse({ status, error: { code, detail: 'sensitive server detail' } }),
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(fixture.nativeElement.textContent).not.toContain('sensitive server detail');
    expect(fixture.nativeElement.querySelector('input[name="username"]').value).toBe('hr');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });

  it('restores an existing server session without posting credentials', async () => {
    fixture.destroy();
    restore.mockReturnValue(of({ username: 'hr' }));
    fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/analytics?countryCode=IN', { replaceUrl: true });
    expect(login).not.toHaveBeenCalled();
  });

  it('shows a checking state until the session response arrives', async () => {
    fixture.destroy();
    const pending = new Subject<null>();
    restore.mockReturnValue(pending);
    fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Checking your session',
    );
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    pending.next(null);
    pending.complete();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
  });

  it('offers sign-in with an honest error when the initial session check fails', async () => {
    fixture.destroy();
    restore.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Cannot check your session.',
    );
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });
});

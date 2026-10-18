import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { SessionService } from '../auth/session-service';
import { AppShell } from './app-shell';

describe('Shell account controls', () => {
  let fixture: ComponentFixture<AppShell>;
  let result: Subject<void>;
  let logout: ReturnType<typeof vi.fn>;
  let redirect: ReturnType<typeof vi.spyOn>;
  const clear = vi.fn();
  beforeEach(async () => {
    result = new Subject();
    logout = vi.fn(() => result);
    clear.mockClear();
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: { user: signal({ username: 'hr' }), logout, clear } },
      ],
    }).compileComponents();
    redirect = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
  });
  async function click(): Promise<void> {
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
  }
  it('shows the server identity and signs out once before returning to login', async () => {
    expect(fixture.nativeElement.textContent).toContain('hr');
    await click();
    await click();
    expect(logout).toHaveBeenCalledOnce();
    expect(redirect).not.toHaveBeenCalled();
    result.next();
    result.complete();
    await fixture.whenStable();
    expect(redirect).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });
  it('keeps logout failures visible and permits retry', async () => {
    await click();
    result.error(new HttpErrorResponse({ status: 0 }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Sign-out could not be confirmed. Please try again.',
    );
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    expect(redirect).not.toHaveBeenCalled();
  });
  it('returns to login when the server confirms the session already expired', async () => {
    await click();
    result.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();
    expect(clear).toHaveBeenCalledOnce();
    expect(redirect).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });
});

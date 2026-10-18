import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { sessionGuard } from './session-guard';
import { SessionService } from './session-service';

describe('sessionGuard', () => {
  const restore = vi.fn();
  const user = signal<{ username: string } | null>(null);
  beforeEach(() => {
    restore.mockReset();
    user.set(null);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: { restore, user } }],
    });
  });

  async function guard(url = '/analytics?countryCode=IN'): Promise<boolean | UrlTree> {
    return firstValueFrom(
      TestBed.runInInjectionContext(() =>
        sessionGuard(new ActivatedRouteSnapshot(), { url } as RouterStateSnapshot),
      ) as Observable<boolean | UrlTree>,
    );
  }

  it('checks the server on each protected navigation rather than trusting cached identity', async () => {
    restore.mockReturnValueOnce(of({ username: 'hr' })).mockReturnValueOnce(of(null));
    expect(await guard()).toBe(true);
    expect(await guard()).toBeInstanceOf(UrlTree);
    expect(restore).toHaveBeenCalledTimes(2);
  });

  it('redirects anonymous users while preserving an internal destination', async () => {
    restore.mockReturnValue(of(null));
    const tree = (await guard()) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(tree)).toContain('/login?returnUrl=');
    expect(tree.queryParams['returnUrl']).toBe('/analytics?countryCode=IN');
  });

  it('fails closed when the session check is unavailable', async () => {
    restore.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    expect(await guard()).toBeInstanceOf(UrlTree);
  });

  it('explains expiry when a previously known session is no longer authenticated', async () => {
    user.set({ username: 'hr' });
    restore.mockReturnValue(of(null));
    expect(((await guard()) as UrlTree).queryParams['reason']).toBe('expired');
  });
});

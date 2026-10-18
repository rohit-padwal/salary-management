import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { safeReturnUrl } from './return-url';
import { SessionService } from './session-service';

export const sessionGuard: CanActivateChildFn = (_route, state) => {
  const router = inject(Router);
  const session = inject(SessionService);
  const wasSignedIn = session.user() !== null;
  return session.restore().pipe(
    map((user) =>
      user
        ? true
        : router.createUrlTree(['/login'], {
            queryParams: {
              returnUrl: safeReturnUrl(state.url),
              ...(wasSignedIn ? { reason: 'expired' } : {}),
            },
          }),
    ),
    catchError(() =>
      of(
        router.createUrlTree(['/login'], {
          queryParams: { returnUrl: safeReturnUrl(state.url) },
        }),
      ),
    ),
  );
};

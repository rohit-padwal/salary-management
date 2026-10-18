import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { safeReturnUrl } from './return-url';
import { SessionService } from './session-service';

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      // Auth requests have their own failure UI; never turn an invalid login into a redirect loop.
      if (
        request.url.startsWith('/api/v1/') &&
        !request.url.startsWith('/api/v1/auth/') &&
        error instanceof HttpErrorResponse &&
        error.status === 401
      ) {
        session.clear();
        if (!router.url.startsWith('/login')) {
          void router.navigate(['/login'], {
            queryParams: { returnUrl: safeReturnUrl(router.url), reason: 'expired' },
            replaceUrl: true,
          });
        }
      }
      // Do not retry a failed write or hide the failure from its caller.
      return throwError(() => error);
    }),
  );
};

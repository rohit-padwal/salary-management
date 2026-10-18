import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, switchMap, tap, throwError } from 'rxjs';

export interface SessionUser {
  readonly username: string;
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<SessionUser | null>(null);
  readonly user = this.currentUser.asReadonly();

  restore(): Observable<SessionUser | null> {
    return this.http.get<SessionUser>('/api/v1/auth/session').pipe(
      tap((user) => this.currentUser.set(user)),
      catchError((error: unknown) => {
        this.clear();
        return error instanceof HttpErrorResponse && error.status === 401
          ? of(null)
          : throwError(() => error);
      }),
    );
  }

  login(username: string, password: string): Observable<SessionUser> {
    const body = new HttpParams().set('username', username).set('password', password);
    return this.refreshCsrf().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/login', body)),
      // Spring rotates the CSRF token at authentication boundaries; never reuse the login token.
      switchMap(() => this.refreshCsrf()),
      switchMap(() => this.http.get<SessionUser>('/api/v1/auth/session')),
      tap((user) => this.currentUser.set(user)),
    );
  }

  logout(): Observable<void | null> {
    return this.refreshCsrf().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/logout', null)),
      tap(() => this.clear()),
      // Logout already succeeded. A fresh login obtains another token if this refresh fails.
      switchMap(() => this.refreshCsrf().pipe(catchError(() => of(null)))),
    );
  }

  clear(): void {
    this.currentUser.set(null);
  }

  private refreshCsrf(): Observable<void> {
    return this.http.get<void>('/api/v1/auth/csrf');
  }
}

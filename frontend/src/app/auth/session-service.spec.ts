import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SessionService } from './session-service';

describe('SessionService', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('restores the server identity and treats only 401 as an anonymous session', async () => {
    const restored = firstValueFrom(service.restore());
    http.expectOne('/api/v1/auth/session').flush({ username: 'hr' });
    expect(await restored).toEqual({ username: 'hr' });
    expect(service.user()).toEqual({ username: 'hr' });
    const expired = firstValueFrom(service.restore());
    http.expectOne('/api/v1/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(await expired).toBeNull();
    expect(service.user()).toBeNull();
  });

  it('does not turn a server failure into a successful anonymous session', async () => {
    const result = firstValueFrom(service.restore());
    const assertion = expect(result).rejects.toMatchObject({ status: 503 });
    http.expectOne('/api/v1/auth/session').flush({}, { status: 503, statusText: 'Unavailable' });
    await assertion;
  });

  it('obtains CSRF, encodes exact credentials, refreshes the rotated token, then reads identity', async () => {
    const result = firstValueFrom(service.login('HR +&', ' p&+= word '));
    document.cookie = 'XSRF-TOKEN=before-login; path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.method).toBe('POST');
    const credentials = new URLSearchParams(login.request.body.toString());
    expect(credentials.get('username')).toBe('HR +&');
    expect(credentials.get('password')).toBe(' p&+= word ');
    expect(login.request.headers.get('X-XSRF-TOKEN')).toBe('before-login');
    expect(login.request.detectContentTypeHeader()).toBe(
      'application/x-www-form-urlencoded;charset=UTF-8',
    );
    login.flush(null, { status: 204, statusText: 'No Content' });
    expect(service.user()).toBeNull();
    document.cookie = 'XSRF-TOKEN=after-login; path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/session').flush({ username: 'HR +&' });
    expect(await result).toEqual({ username: 'HR +&' });
    expect(service.user()?.username).toBe('HR +&');
  });

  it('never posts credentials when CSRF acquisition fails', async () => {
    const result = firstValueFrom(service.login('hr', 'password'));
    const assertion = expect(result).rejects.toMatchObject({ status: 0 });
    http.expectOne('/api/v1/auth/csrf').error(new ProgressEvent('error'));
    await assertion;
    http.expectNone('/api/v1/auth/login');
    expect(service.user()).toBeNull();
  });

  it('propagates rejected credentials without retrying the login request', async () => {
    const result = firstValueFrom(service.login('hr', 'incorrect'));
    const assertion = expect(result).rejects.toMatchObject({ status: 401 });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne('/api/v1/auth/login')
      .flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });
    await assertion;
    http.expectNone('/api/v1/auth/session');
    expect(service.user()).toBeNull();
  });

  it('does not publish an authenticated identity if token refresh fails after login', async () => {
    const result = firstValueFrom(service.login('hr', 'password'));
    const assertion = expect(result).rejects.toMatchObject({ status: 503 });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/login').flush(null);
    http.expectOne('/api/v1/auth/csrf').flush({}, { status: 503, statusText: 'Unavailable' });
    await assertion;
    expect(service.user()).toBeNull();
  });

  async function signedIn(): Promise<void> {
    const result = firstValueFrom(service.restore());
    http.expectOne('/api/v1/auth/session').flush({ username: 'hr' });
    await result;
  }

  it('obtains a current token for logout and clears identity before refreshing the anonymous token', async () => {
    await signedIn();
    const result = firstValueFrom(service.logout());
    document.cookie = 'XSRF-TOKEN=logout-token; path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.method).toBe('POST');
    expect(logout.request.headers.get('X-XSRF-TOKEN')).toBe('logout-token');
    logout.flush(null);
    expect(service.user()).toBeNull();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    await result;
  });

  it('keeps a completed logout successful if anonymous token refresh fails', async () => {
    await signedIn();
    const result = firstValueFrom(service.logout());
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/auth/logout').flush(null);
    http.expectOne('/api/v1/auth/csrf').flush({}, { status: 503, statusText: 'Unavailable' });
    await result;
    expect(service.user()).toBeNull();
  });

  it('retains identity when logout fails rather than claiming the session ended', async () => {
    await signedIn();
    const result = firstValueFrom(service.logout());
    const assertion = expect(result).rejects.toMatchObject({ status: 403 });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne('/api/v1/auth/logout')
      .flush({ code: 'CSRF_INVALID' }, { status: 403, statusText: 'Forbidden' });
    await assertion;
    expect(service.user()?.username).toBe('hr');
  });
});

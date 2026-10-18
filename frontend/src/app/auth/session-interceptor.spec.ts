import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { sessionInterceptor } from './session-interceptor';
import { SessionService } from './session-service';

describe('sessionInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let redirect: ReturnType<typeof vi.spyOn>;
  const clear = vi.fn();
  beforeEach(() => {
    clear.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: { clear } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/analytics?countryCode=IN');
    redirect = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });
  afterEach(() => http.verify());

  it('clears expired identity, redirects to login, and propagates the original write failure without retry', () => {
    const error = vi.fn();
    client.post('/api/v1/employees', {}).subscribe({ error });
    http.expectOne('/api/v1/employees').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(clear).toHaveBeenCalledOnce();
    expect(redirect).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/analytics?countryCode=IN', reason: 'expired' },
      replaceUrl: true,
    });
    expect(error).toHaveBeenCalledOnce();
    http.expectNone('/api/v1/employees');
  });

  it.each([
    ['/api/v1/auth/login', 401],
    ['/api/v1/auth/session', 401],
    ['/api/v1/employees', 403],
    ['https://example.com/api/v1/employees', 401],
  ])('does not redirect for %s with status %s', (url, status) => {
    client.get(url).subscribe({ error: () => {} });
    http.expectOne(url).flush({}, { status, statusText: 'Rejected' });
    expect(clear).not.toHaveBeenCalled();
    expect(redirect).not.toHaveBeenCalled();
  });
});

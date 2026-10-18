import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompensationApi } from './compensation-api';

describe('CompensationApi', () => {
  let api: CompensationApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CompensationApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('requests department grouping without blank filters, pagination, sorting, or CSRF', () => {
    api.get({ groupBy: 'department', q: '' }).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/analytics/compensation');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['groupBy']);
    expect(request.request.params.get('groupBy')).toBe('department');
    request.flush({ groupBy: 'department', totalHeadcount: 0, currencySummaries: [], groups: [] });
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone((req) => req.url.startsWith('/api/v1/employees'));
  });

  it('encodes combined filters once and preserves exact response amounts and group IDs', () => {
    const observed = vi.fn();
    api
      .get({
        groupBy: 'department',
        q: 'A+B & 100%_ /東京',
        departmentId: '9007199254740993',
        countryCode: 'IN',
        currencyCode: 'USD',
      })
      .subscribe(observed);
    const request = http.expectOne((req) => req.url === '/api/v1/analytics/compensation');
    const params = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
    expect([...params.keys()].sort()).toEqual([
      'countryCode',
      'currencyCode',
      'departmentId',
      'groupBy',
      'q',
    ]);
    expect(params.get('q')).toBe('A+B & 100%_ /東京');
    expect(params.get('departmentId')).toBe('9007199254740993');
    expect(params.get('countryCode')).toBe('IN');
    expect(params.get('currencyCode')).toBe('USD');
    expect(params.get('groupBy')).toBe('department');
    const result = {
      groupBy: 'department',
      totalHeadcount: 11,
      currencySummaries: [{ currencyCode: 'USD', totalSalary: '9999999999999999.99' }],
      groups: [{ groupKey: '9007199254740993', averageSalary: '999999999999999.99' }],
    };
    request.flush(result);
    expect(observed).toHaveBeenCalledWith(result);
  });

  it('propagates failures without retrying', () => {
    const error = vi.fn();
    api.get({ groupBy: 'country', q: '' }).subscribe({ error });
    http
      .expectOne((req) => req.url === '/api/v1/analytics/compensation')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(error).toHaveBeenCalledOnce();
    http.expectNone((req) => req.url === '/api/v1/analytics/compensation');
  });
});

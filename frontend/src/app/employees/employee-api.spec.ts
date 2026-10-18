import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmployeeApi } from './employee-api';

describe('EmployeeApi', () => {
  let api: EmployeeApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EmployeeApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('obtains CSRF before posting exactly six editable string fields without altering amounts', () => {
    const input = {
      employeeNumber: 'emp-1',
      fullName: ' 李  明 ',
      departmentId: '9007199254740993',
      countryCode: 'IN',
      salaryAmount: '999999999999999.9900',
      currencyCode: 'USD',
      id: '99',
      version: '4',
    };
    const observed = vi.fn();
    api.create(input).subscribe(observed);
    http.expectNone((req) => req.url === '/api/v1/employees');
    document.cookie = 'XSRF-TOKEN=create-token; path=/';
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/employees');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('create-token');
    expect(request.request.body).toEqual({
      employeeNumber: input.employeeNumber,
      fullName: input.fullName,
      departmentId: input.departmentId,
      countryCode: 'IN',
      salaryAmount: input.salaryAmount,
      currencyCode: 'USD',
    });
    request.flush(
      { ...input, employeeNumber: 'EMP-1', salaryAmount: '999999999999999.99' },
      { status: 201, statusText: 'Created' },
    );
    expect(observed).toHaveBeenCalledOnce();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('does not post when CSRF acquisition fails or automatically replay an unconfirmed write', () => {
    const input = {
      employeeNumber: 'EMP-1',
      fullName: 'Asha',
      departmentId: '1',
      countryCode: 'IN',
      salaryAmount: '100.00',
      currencyCode: 'INR',
    };
    const error = vi.fn();
    api.create(input).subscribe({ error });
    http.expectOne('/api/v1/auth/csrf').error(new ProgressEvent('error'));
    http.expectNone('/api/v1/employees');
    expect(error).toHaveBeenCalledOnce();
    api.create(input).subscribe({ error });
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/employees').error(new ProgressEvent('error'));
    expect(error).toHaveBeenCalledTimes(2);
    http.expectNone('/api/v1/employees');
  });

  it('requests a bounded server page without adding a blank search or client-side sort', () => {
    api.list({ page: 0, size: 25, q: '' }).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/employees');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    request.flush({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('encodes a literal search as one parameter and preserves exact response strings', () => {
    const observed = vi.fn();
    api.list({ page: 3, size: 50, q: 'A+B & 100%_ /東京' }).subscribe(observed);
    const request = http.expectOne((req) => req.url === '/api/v1/employees');
    const decoded = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
    expect(decoded.get('q')).toBe('A+B & 100%_ /東京');
    expect(decoded.getAll('q')).toHaveLength(1);
    expect(decoded.get('page')).toBe('3');
    expect(decoded.get('size')).toBe('50');
    const response = {
      items: [
        { id: '9007199254740993', salaryAmount: '999999999999999.9999', currencyCode: 'CLF' },
      ],
      page: 3,
      size: 50,
      totalElements: 500,
      totalPages: 10,
    };
    request.flush(response);
    expect(observed).toHaveBeenCalledWith(response);
  });

  it('propagates an error without retrying the request', () => {
    const error = vi.fn();
    api.list({ page: 0, size: 25, q: '' }).subscribe({ error });
    http
      .expectOne((req) => req.url === '/api/v1/employees')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(error).toHaveBeenCalledOnce();
    http.expectNone((req) => req.url === '/api/v1/employees');
  });

  it('combines independent catalog filters with search, paging, and an exact server sort', () => {
    api
      .list({
        page: 2,
        size: 50,
        q: 'A+B',
        departmentId: '9007199254740993',
        countryCode: 'IN',
        currencyCode: 'USD',
        sort: 'salaryAmount,desc',
      })
      .subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/employees');
    expect(request.request.params.keys().sort()).toEqual([
      'countryCode',
      'currencyCode',
      'departmentId',
      'page',
      'q',
      'size',
      'sort',
    ]);
    expect(request.request.params.get('departmentId')).toBe('9007199254740993');
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('currencyCode')).toBe('USD');
    expect(request.request.params.getAll('sort')).toEqual(['salaryAmount,desc']);
    expect(request.request.params.get('q')).toBe('A+B');
    request.flush({ items: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('omits blank filters without inferring a currency from the selected country', () => {
    api
      .list({ page: 0, size: 25, q: '', departmentId: '', countryCode: 'IN', currencyCode: '' })
      .subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/employees');
    expect(request.request.params.keys().sort()).toEqual(['countryCode', 'page', 'size']);
    request.flush({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });
});

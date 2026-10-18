import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReferenceDataApi } from './reference-data-api';

describe('ReferenceDataApi', () => {
  let api: ReferenceDataApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReferenceDataApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('reads current catalogs with exact string IDs and numeric currency precision', () => {
    const observed = vi.fn();
    api.get().subscribe(observed);
    const request = http.expectOne('/api/v1/reference-data');
    expect(request.request.method).toBe('GET');
    const response = {
      departments: [{ id: '9007199254740993', name: 'Engineering' }],
      countries: [{ code: 'IN', name: 'India' }],
      currencies: [{ code: 'JPY', name: 'Yen', minorUnits: 0 }],
    };
    request.flush(response);
    expect(observed).toHaveBeenCalledWith(response);
  });

  it('preserves empty catalogs rather than inventing filter choices', () => {
    const observed = vi.fn();
    api.get().subscribe(observed);
    const empty = { departments: [], countries: [], currencies: [] };
    http.expectOne('/api/v1/reference-data').flush(empty);
    expect(observed).toHaveBeenCalledWith(empty);
  });

  it('propagates failures and allows an explicit fresh request', () => {
    const error = vi.fn();
    api.get().subscribe({ error });
    http.expectOne('/api/v1/reference-data').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(error).toHaveBeenCalledOnce();
    http.expectNone('/api/v1/reference-data');
    const observed = vi.fn();
    api.get().subscribe(observed);
    http
      .expectOne('/api/v1/reference-data')
      .flush({ departments: [], countries: [], currencies: [] });
    expect(observed).toHaveBeenCalledOnce();
  });
});

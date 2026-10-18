import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatPaginator } from '@angular/material/paginator';
import { By } from '@angular/platform-browser';
import { EmployeesPage } from './employees-page';
import { provideRouter } from '@angular/router';

describe('Employee filters and sorting', () => {
  let fixture: ComponentFixture<EmployeesPage>;
  let http: HttpTestingController;
  const catalogs = {
    departments: [
      { id: '9007199254740993', name: 'Engineering' },
      { id: '2', name: 'HR' },
    ],
    countries: [
      { code: 'IN', name: 'India' },
      { code: 'US', name: 'United States' },
    ],
    currencies: [
      { code: 'INR', name: 'Indian rupee', minorUnits: 2 },
      { code: 'USD', name: 'US dollar', minorUnits: 2 },
    ],
  };
  const employee = {
    id: '1',
    employeeNumber: 'EMP-1',
    fullName: 'Asha Rao',
    departmentId: '9007199254740993',
    departmentName: 'Engineering',
    countryCode: 'IN',
    countryName: 'India',
    salaryAmount: '1000.00',
    currencyCode: 'USD',
    version: '0',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeesPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeesPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  function listing(): TestRequest {
    return http.expectOne((req) => req.url === '/api/v1/employees');
  }
  function rows(request: TestRequest, page = 0, size = 25): void {
    request.flush({
      items: [employee],
      page,
      size,
      totalElements: 70,
      totalPages: Math.ceil(70 / size),
    });
  }
  async function ready(): Promise<void> {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    rows(listing());
    await fixture.whenStable();
  }
  async function choose(name: string, value: string): Promise<void> {
    const select = fixture.nativeElement.querySelector(
      `select[name="${name}"]`,
    ) as HTMLSelectElement;
    expect(select).not.toBeNull();
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }
  async function apply(): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }
  async function page(index: number, size = 25): Promise<void> {
    fixture.debugElement
      .query(By.directive(MatPaginator))
      .componentInstance.page.emit({ pageIndex: index, pageSize: size, length: 70 });
    await fixture.whenStable();
  }

  it('keeps browsing available while loading catalogs and renders only server-provided choices', async () => {
    expect(fixture.nativeElement.textContent).toContain('Loading filter options');
    expect(
      fixture.nativeElement.querySelector('select[name="departmentId"]').matches(':disabled'),
    ).toBe(true);
    rows(listing());
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    const department = fixture.nativeElement.querySelector(
      'select[name="departmentId"]',
    ) as HTMLSelectElement;
    expect(department.matches(':disabled')).toBe(false);
    expect(
      Array.from(department.options).map((option) => [option.value, option.textContent?.trim()]),
    ).toEqual([
      ['', 'All departments'],
      ['9007199254740993', 'Engineering'],
      ['2', 'HR'],
    ]);
    expect(fixture.nativeElement.querySelector('select[name="sortField"]').options.length).toBe(6);
  });

  it('applies combined filters and sorting from page zero only after submission', async () => {
    await ready();
    await page(2);
    rows(listing(), 2);
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input[name="q"]');
    input.value = ' Asha ';
    input.dispatchEvent(new Event('input'));
    await choose('departmentId', '9007199254740993');
    await choose('countryCode', 'IN');
    await choose('currencyCode', 'USD');
    await choose('sortField', 'fullName');
    await choose('sortDirection', 'desc');
    http.expectNone((req) => req.url === '/api/v1/employees');
    await apply();
    const request = listing();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('q')).toBe('Asha');
    expect(request.request.params.get('departmentId')).toBe('9007199254740993');
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('currencyCode')).toBe('USD');
    expect(request.request.params.get('sort')).toBe('fullName,desc');
    rows(request);
    await fixture.whenStable();
  });

  it('requires explicit currency for salary sorting even when a country is selected', async () => {
    await ready();
    await choose('countryCode', 'IN');
    await choose('sortField', 'salaryAmount');
    await apply();
    http.expectNone((req) => req.url === '/api/v1/employees');
    expect(fixture.nativeElement.textContent).toContain(
      'Select a currency before sorting by salary.',
    );
    await choose('currencyCode', 'USD');
    await choose('sortDirection', 'desc');
    await apply();
    const request = listing();
    expect(request.request.params.get('sort')).toBe('salaryAmount,desc');
    expect(request.request.params.get('currencyCode')).toBe('USD');
    rows(request);
    await fixture.whenStable();
    await choose('currencyCode', '');
    await apply();
    http.expectNone((req) => req.url === '/api/v1/employees');
    expect(fixture.nativeElement.textContent).toContain(
      'Select a currency before sorting by salary.',
    );
    expect(fixture.nativeElement.querySelector('select[name="sortField"]').value).toBe(
      'salaryAmount',
    );
  });

  it('preserves applied filters/sort on pagination and resets page zero when size changes', async () => {
    await ready();
    await choose('currencyCode', 'USD');
    await choose('sortField', 'salaryAmount');
    await apply();
    rows(listing());
    await fixture.whenStable();
    await page(1);
    const next = listing();
    expect(next.request.params.get('currencyCode')).toBe('USD');
    expect(next.request.params.get('sort')).toBe('salaryAmount,asc');
    expect(next.request.params.get('page')).toBe('1');
    rows(next, 1);
    await fixture.whenStable();
    await page(1, 50);
    const resized = listing();
    expect(resized.request.params.get('page')).toBe('0');
    expect(resized.request.params.get('size')).toBe('50');
    expect(resized.request.params.get('currencyCode')).toBe('USD');
    rows(resized, 0, 50);
    await fixture.whenStable();
  });

  it('resets all criteria and default sort while retaining the page size', async () => {
    await ready();
    await page(1, 50);
    rows(listing(), 0, 50);
    await fixture.whenStable();
    await choose('departmentId', '2');
    await choose('countryCode', 'IN');
    await choose('currencyCode', 'USD');
    await choose('sortField', 'salaryAmount');
    await choose('sortDirection', 'desc');
    await apply();
    rows(listing(), 0, 50);
    await fixture.whenStable();
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === 'Reset')!;
    button.click();
    await fixture.whenStable();
    const reset = listing();
    expect(reset.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(reset.request.params.get('size')).toBe('50');
    rows(reset, 0, 50);
    await fixture.whenStable();
    for (const name of ['departmentId', 'countryCode', 'currencyCode'])
      expect(fixture.nativeElement.querySelector(`select[name="${name}"]`).value).toBe('');
    expect(fixture.nativeElement.querySelector('select[name="sortField"]').value).toBe(
      'employeeNumber',
    );
    expect(fixture.nativeElement.querySelector('select[name="sortDirection"]').value).toBe('asc');
  });

  it('cancels superseded filtered reads', async () => {
    await ready();
    await choose('departmentId', '9007199254740993');
    await apply();
    const obsolete = listing();
    await choose('departmentId', '2');
    await apply();
    expect(obsolete.cancelled).toBe(true);
    const latest = listing();
    expect(latest.request.params.get('departmentId')).toBe('2');
    rows(latest);
    await fixture.whenStable();
  });

  it('shows no matching employees for an empty filtered result without a search term', async () => {
    await ready();
    await choose('currencyCode', 'USD');
    await apply();
    listing().flush({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No matching employees');
    expect(fixture.nativeElement.textContent).not.toContain('No employees yet');
  });

  it('recovers failed catalog loading independently from employee reads', async () => {
    rows(listing());
    http
      .expectOne('/api/v1/reference-data')
      .flush({ detail: 'private detail' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Filter options could not be loaded.');
    expect(fixture.nativeElement.textContent).not.toContain('private detail');
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
    const input = fixture.nativeElement.querySelector('input[name="q"]');
    input.value = 'Asha';
    input.dispatchEvent(new Event('input'));
    await apply();
    rows(listing());
    await fixture.whenStable();
    const retry = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === 'Retry filters')!;
    retry.click();
    await fixture.whenStable();
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('select[name="departmentId"]').matches(':disabled'),
    ).toBe(false);
    http.expectNone((req) => req.url === '/api/v1/employees');
  });

  it('keeps empty catalogs empty and leaves an all-employee search usable', async () => {
    rows(listing());
    http
      .expectOne('/api/v1/reference-data')
      .flush({ departments: [], countries: [], currencies: [] });
    await fixture.whenStable();
    for (const name of ['departmentId', 'countryCode', 'currencyCode'])
      expect(fixture.nativeElement.querySelector(`select[name="${name}"]`).options.length).toBe(1);
    await apply();
    rows(listing());
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });

  it('cancels lookup loading on destruction', () => {
    rows(listing());
    const pending = http.expectOne('/api/v1/reference-data');
    fixture.destroy();
    expect(pending.cancelled).toBe(true);
  });

  it('paginates the applied query without silently applying draft filter changes', async () => {
    await ready();
    await choose('countryCode', 'IN');
    await apply();
    rows(listing());
    await fixture.whenStable();
    await choose('countryCode', 'US');
    await choose('sortDirection', 'desc');
    await page(1);
    const request = listing();
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('sort')).toBe('employeeNumber,asc');
    rows(request, 1);
    await fixture.whenStable();
  });

  it.each([
    [0, 'Cannot reach the server to load filter options. Please try again.'],
    [401, 'Your session has ended. Please sign in again.'],
    [403, 'You do not have permission to load filter options.'],
  ])('shows a safe lookup message for HTTP %s', async (status, message) => {
    rows(listing());
    const request = http.expectOne('/api/v1/reference-data');
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ detail: 'private response detail' }, { status, statusText: 'Rejected' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(fixture.nativeElement.textContent).not.toContain('private response detail');
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });
});

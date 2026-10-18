import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatPaginator } from '@angular/material/paginator';
import { EmployeesPage } from './employees-page';
import { provideRouter } from '@angular/router';

describe('Employee directory', () => {
  let fixture: ComponentFixture<EmployeesPage>;
  let http: HttpTestingController;
  const employee = {
    id: '9007199254740993',
    employeeNumber: 'EMP-000001',
    fullName: 'Asha Rao',
    departmentId: '9007199254740995',
    departmentName: 'Engineering',
    countryCode: 'IN',
    countryName: 'India',
    salaryAmount: '999999999999999.9999',
    currencyCode: 'CLF',
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
    http
      .expectOne('/api/v1/reference-data')
      .flush({ departments: [], countries: [], currencies: [] });
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  function request(page = 0, size = 25, q = ''): TestRequest {
    return http.expectOne(
      (req) =>
        req.url === '/api/v1/employees' &&
        req.params.get('page') === String(page) &&
        req.params.get('size') === String(size) &&
        (req.params.get('q') ?? '') === q,
    );
  }
  async function loaded(): Promise<void> {
    request().flush({ items: [employee], page: 0, size: 25, totalElements: 70, totalPages: 3 });
    await fixture.whenStable();
  }
  async function search(value: string): Promise<void> {
    const input = fixture.nativeElement.querySelector('input[name="q"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }
  async function changePage(pageIndex: number, pageSize = 25): Promise<void> {
    fixture.debugElement
      .query(By.directive(MatPaginator))
      .componentInstance.page.emit({ pageIndex, pageSize, length: 70 });
    await fixture.whenStable();
  }

  it('starts with a single loading request and renders exact salary/currency and server labels', async () => {
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Loading employees',
    );
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).not.toBeNull();
    await loaded();
    const page: HTMLElement = fixture.nativeElement;
    for (const text of [
      'Asha Rao',
      'EMP-000001',
      'Engineering',
      'India',
      '999999999999999.9999',
      'CLF',
    ])
      expect(page.textContent).toContain(text);
    expect(page.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(page.querySelector('caption')?.textContent).toContain('gross annual base');
    expect(page.querySelector('[aria-busy="false"]')).not.toBeNull();
  });

  it('loads the next server page and preserves the chosen search', async () => {
    await loaded();
    await search('Asha');
    request(0, 25, 'Asha').flush({
      items: [employee],
      page: 0,
      size: 25,
      totalElements: 70,
      totalPages: 3,
    });
    await fixture.whenStable();
    await changePage(1);
    request(1, 25, 'Asha').flush({
      items: [{ ...employee, id: '2', fullName: 'Asha Singh' }],
      page: 1,
      size: 25,
      totalElements: 70,
      totalPages: 3,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Asha Singh');
    expect(fixture.nativeElement.textContent).not.toContain('Asha Rao');
  });

  it('resets to page zero for a new search and page-size change', async () => {
    await loaded();
    await changePage(2);
    request(2).flush({ items: [employee], page: 2, size: 25, totalElements: 70, totalPages: 3 });
    await fixture.whenStable();
    await search('  Asha  ');
    request(0, 25, 'Asha').flush({
      items: [employee],
      page: 0,
      size: 25,
      totalElements: 70,
      totalPages: 3,
    });
    await fixture.whenStable();
    await changePage(1, 50);
    request(0, 50, 'Asha').flush({
      items: [employee],
      page: 0,
      size: 50,
      totalElements: 70,
      totalPages: 2,
    });
    await fixture.whenStable();
    const paginator = fixture.debugElement.query(By.directive(MatPaginator))
      .componentInstance as MatPaginator;
    expect(paginator.pageIndex).toBe(0);
    expect(paginator.pageSize).toBe(50);
    expect(paginator.length).toBe(70);
  });

  it('cancels an obsolete read and never shows rows from that search', async () => {
    await loaded();
    await search('old');
    const old = request(0, 25, 'old');
    await search('new');
    expect(old.cancelled).toBe(true);
    request(0, 25, 'new').flush({
      items: [{ ...employee, fullName: 'New result' }],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('New result');
    expect(fixture.nativeElement.textContent).not.toContain('Asha Rao');
  });

  it('does not send a request while typing until search is submitted', async () => {
    await loaded();
    const input = fixture.nativeElement.querySelector('input[name="q"]');
    input.value = 'draft';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    http.expectNone((req) => req.url === '/api/v1/employees');
  });

  it('clears search and returns to the first page', async () => {
    await loaded();
    await search('Asha');
    request(0, 25, 'Asha').flush({
      items: [employee],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Reset')!;
    button.click();
    await fixture.whenStable();
    request().flush({ items: [employee], page: 0, size: 25, totalElements: 70, totalPages: 3 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('input[name="q"]').value).toBe('');
  });

  it('distinguishes an empty directory from a search with no matches', async () => {
    request().flush({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No employees yet');
    await search('missing');
    request(0, 25, 'missing').flush({
      items: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No matching employees');
    expect(fixture.nativeElement.textContent).not.toContain('No employees yet');
  });

  it('offers recovery when a concurrent change leaves a page outside the result range', async () => {
    await loaded();
    await changePage(2);
    request(2).flush({ items: [], page: 2, size: 25, totalElements: 10, totalPages: 1 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('This page has no employees');
    expect(fixture.nativeElement.querySelector('mat-paginator').hidden).toBe(true);
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Go to first page')!;
    button.click();
    await fixture.whenStable();
    request().flush({ items: [employee], page: 0, size: 25, totalElements: 10, totalPages: 1 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });

  it('shows an error without stale rows and retries the current request', async () => {
    await loaded();
    await search('Asha');
    request(0, 25, 'Asha').flush(
      { detail: 'database password' },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Employees could not be loaded. Please try again.',
    );
    expect(fixture.nativeElement.textContent).not.toContain('database password');
    expect(fixture.nativeElement.textContent).not.toContain('Asha Rao');
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Retry')!;
    button.click();
    await fixture.whenStable();
    request(0, 25, 'Asha').flush({
      items: [employee],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });

  it.each([
    [401, 'Your session has ended. Please sign in again.'],
    [403, 'You do not have permission to view employees.'],
    [0, 'Cannot reach the server. Check your connection and try again.'],
    [400, 'The query could not be accepted. Review the search, filters, and sort, then try again.'],
  ])('uses a safe message for status %s', async (status, message) => {
    const pending = request();
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ detail: 'private detail' }, { status, statusText: 'Rejected' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(fixture.nativeElement.textContent).not.toContain('private detail');
  });

  it('validates search by Unicode code points and prevents oversized requests', async () => {
    await loaded();
    await search('😀'.repeat(201));
    http.expectNone((req) => req.url === '/api/v1/employees');
    expect(fixture.nativeElement.textContent).toContain(
      'Search must contain at most 200 characters.',
    );
    await search('😀'.repeat(200));
    request(0, 25, '😀'.repeat(200)).flush({
      items: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
    });
    await fixture.whenStable();
  });

  it('cancels an in-flight read when the employee page is destroyed', () => {
    const pending = request();
    fixture.destroy();
    expect(pending.cancelled).toBe(true);
  });

  it('rejects null characters before sending a search', async () => {
    await loaded();
    await search('invalid\0search');
    http.expectNone((req) => req.url === '/api/v1/employees');
    expect(fixture.nativeElement.textContent).toContain('Search cannot contain a null character.');
  });
});

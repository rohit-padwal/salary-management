import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnalyticsPage } from './analytics-page';

describe('Compensation analytics dashboard', () => {
  let fixture: ComponentFixture<AnalyticsPage>;
  let http: HttpTestingController;
  const catalogs = {
    departments: [{ id: '9007199254740993', name: 'Engineering' }],
    countries: [{ code: 'IN', name: 'India' }],
    currencies: [
      { code: 'USD', name: 'US dollar', minorUnits: 2 },
      { code: 'JPY', name: 'Yen', minorUnits: 0 },
    ],
  };
  const usd = {
    currencyCode: 'USD',
    headcount: 11,
    totalSalary: '9999999999999999.99',
    averageSalary: '909090909090909.09',
    minSalary: '1.00',
    maxSalary: '999999999999999.99',
  };
  const jpy = {
    currencyCode: 'JPY',
    headcount: 1,
    totalSalary: '100001',
    averageSalary: '100001',
    minSalary: '100001',
    maxSalary: '100001',
  };
  const report = {
    groupBy: 'department',
    totalHeadcount: 12,
    currencySummaries: [jpy, usd],
    groups: [
      { ...usd, groupKey: '9007199254740993', groupLabel: 'Engineering' },
      { ...jpy, groupKey: '9007199254740993', groupLabel: 'Engineering' },
    ],
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnalyticsPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });
  function request(): TestRequest {
    return http.expectOne((req) => req.url === '/api/v1/analytics/compensation');
  }
  async function ready(): Promise<void> {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    request().flush(report);
    await fixture.whenStable();
  }
  async function choose(name: string, value: string): Promise<void> {
    const control = fixture.nativeElement.querySelector(`[name="${name}"]`) as
      HTMLSelectElement | HTMLInputElement;
    control.value = value;
    control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
    await fixture.whenStable();
  }
  async function apply(): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }
  async function click(label: string): Promise<void> {
    Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === label)!
      .click();
    await fixture.whenStable();
  }

  it('loads a department report independently of filter catalogs and shows distinct exact currency summaries', async () => {
    expect(fixture.nativeElement.textContent).toContain('Loading compensation analytics');
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).not.toBeNull();
    const initial = request();
    expect(initial.request.params.get('groupBy')).toBe('department');
    initial.flush(report);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('12');
    const cards: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.currency-card'),
    );
    expect(cards).toHaveLength(2);
    expect(cards[0].textContent).toContain('JPY');
    expect(cards[0].textContent).toContain('100001');
    expect(cards[1].textContent).toContain('USD');
    expect(cards[1].textContent).toContain('9999999999999999.99');
    expect(cards[1].textContent).toContain('909090909090909.09');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('caption')?.textContent).toContain('department');
    expect(fixture.nativeElement.querySelector('table')?.textContent).toContain('Engineering');
    expect(fixture.nativeElement.querySelectorAll('table')).toHaveLength(1);
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    http.expectNone('/api/v1/employees');
  });

  it('applies grouping, search, and independent filters only on explicit submit', async () => {
    await ready();
    await choose('q', ' 李  明 ');
    await choose('groupBy', 'country');
    await choose('departmentId', '9007199254740993');
    await choose('countryCode', 'IN');
    await choose('currencyCode', 'USD');
    http.expectNone((req) => req.url === '/api/v1/analytics/compensation');
    expect(fixture.nativeElement.querySelector('caption')?.textContent).toContain('department');
    await apply();
    const applied = request();
    expect(applied.request.params.get('q')).toBe('李  明');
    expect(applied.request.params.get('groupBy')).toBe('country');
    expect(applied.request.params.get('departmentId')).toBe('9007199254740993');
    expect(applied.request.params.get('countryCode')).toBe('IN');
    expect(applied.request.params.get('currencyCode')).toBe('USD');
    expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(0);
    applied.flush({
      ...report,
      groupBy: 'country',
      currencySummaries: [usd],
      groups: [{ ...usd, groupKey: 'IN', groupLabel: 'India' }],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('caption')?.textContent).toContain('country');
    expect(fixture.nativeElement.querySelector('table')?.textContent).toContain('India');
    expect(fixture.nativeElement.querySelector('.applied-criteria')?.textContent).toContain(
      '李  明',
    );
    await choose('groupBy', 'department');
    await choose('q', 'unapplied');
    expect(fixture.nativeElement.querySelector('caption')?.textContent).toContain('country');
    expect(fixture.nativeElement.querySelector('.applied-criteria')?.textContent).not.toContain(
      'unapplied',
    );
  });

  it('allows country filters without inferring salary currency and resets all controls to department grouping', async () => {
    await ready();
    await choose('countryCode', 'IN');
    await choose('groupBy', 'country');
    await apply();
    const filtered = request();
    expect(filtered.request.params.has('currencyCode')).toBe(false);
    filtered.flush({ ...report, groupBy: 'country' });
    await fixture.whenStable();
    await click('Reset');
    const reset = request();
    expect(reset.request.params.keys()).toEqual(['groupBy']);
    expect(reset.request.params.get('groupBy')).toBe('department');
    reset.flush(report);
    await fixture.whenStable();
    for (const name of ['q', 'departmentId', 'countryCode', 'currencyCode'])
      expect(fixture.nativeElement.querySelector(`[name="${name}"]`).value).toBe('');
    expect(fixture.nativeElement.querySelector('[name="groupBy"]').value).toBe('department');
  });

  it('cancels obsolete reports and cancels all pending reads on destruction', async () => {
    const catalogsRequest = http.expectOne('/api/v1/reference-data');
    const obsolete = request();
    await choose('groupBy', 'country');
    await apply();
    expect(obsolete.cancelled).toBe(true);
    const latest = request();
    fixture.destroy();
    expect(latest.cancelled).toBe(true);
    expect(catalogsRequest.cancelled).toBe(true);
  });

  it('shows empty and filtered no-match states without invented zero-valued currency cards', async () => {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    request().flush({
      groupBy: 'department',
      totalHeadcount: 0,
      currencySummaries: [],
      groups: [],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No salary data yet');
    expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(0);
    await choose('q', 'no match');
    await apply();
    request().flush({
      groupBy: 'department',
      totalHeadcount: 0,
      currencySummaries: [],
      groups: [],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No matching employees');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it.each([
    [0, 'Cannot reach the server.'],
    [400, 'The report query could not be accepted.'],
    [401, 'Your session has ended.'],
    [403, 'You do not have permission to view compensation analytics.'],
    [503, 'Compensation analytics could not be loaded.'],
  ])(
    'shows safe HTTP %s failures, clears prior results, and retries applied criteria',
    async (status, message) => {
      await ready();
      await choose('currencyCode', 'USD');
      await apply();
      const failed = request();
      if (status === 0) failed.error(new ProgressEvent('error'));
      else failed.flush({ detail: 'private salary SQL' }, { status, statusText: 'Rejected' });
      await fixture.whenStable();
      expect(fixture.nativeElement.textContent).toContain(message);
      expect(fixture.nativeElement.textContent).not.toContain('private salary SQL');
      expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(0);
      await choose('currencyCode', 'JPY');
      await click('Retry report');
      const retried = request();
      expect(retried.request.params.get('currencyCode')).toBe('USD');
      retried.flush(report);
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(2);
    },
  );

  it('retries filter catalogs independently without losing report results', async () => {
    request().flush(report);
    http
      .expectOne('/api/v1/reference-data')
      .flush({ detail: 'private' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Filter options could not be loaded.');
    expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(2);
    await click('Retry filters');
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('select[name="departmentId"]').matches(':disabled'),
    ).toBe(false);
    expect(
      fixture.nativeElement.querySelector(
        'select[name="departmentId"] option[value="9007199254740993"]',
      )?.textContent,
    ).toContain('Engineering');
    http.expectNone((req) => req.url === '/api/v1/analytics/compensation');
  });

  it('validates Unicode search length and null characters before requesting a report', async () => {
    await ready();
    await choose('q', '界'.repeat(201));
    await apply();
    expect(fixture.nativeElement.textContent).toContain(
      'Search must contain at most 200 characters.',
    );
    http.expectNone((req) => req.url === '/api/v1/analytics/compensation');
    await choose('q', 'bad\0query');
    await apply();
    expect(fixture.nativeElement.textContent).toContain('Search cannot contain a null character.');
    http.expectNone((req) => req.url === '/api/v1/analytics/compensation');
    await choose('q', '😀'.repeat(200));
    await apply();
    const accepted = request();
    expect(accepted.request.params.get('q')).toBe('😀'.repeat(200));
    accepted.flush(report);
    await fixture.whenStable();
  });

  it('keeps empty catalogs empty while showing an unfiltered report', async () => {
    http
      .expectOne('/api/v1/reference-data')
      .flush({ departments: [], countries: [], currencies: [] });
    request().flush(report);
    await fixture.whenStable();
    for (const name of ['departmentId', 'countryCode', 'currencyCode'])
      expect(fixture.nativeElement.querySelector(`select[name="${name}"]`).options.length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.currency-card')).toHaveLength(2);
    await apply();
    request().flush(report);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
  });

  it('escapes labels returned by the server instead of treating them as HTML', async () => {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    request().flush({
      ...report,
      groups: [{ ...usd, groupKey: '1', groupLabel: '<img src=x onerror=alert(1)>' }],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('tbody')?.textContent).toContain(
      '<img src=x onerror=alert(1)>',
    );
    expect(fixture.nativeElement.querySelector('tbody img')).toBeNull();
  });
});

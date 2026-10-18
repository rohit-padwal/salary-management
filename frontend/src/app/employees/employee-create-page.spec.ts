import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EmployeeFormPage } from './employee-form-page';

describe('Employee creation', () => {
  let fixture: ComponentFixture<EmployeeFormPage>;
  let http: HttpTestingController;
  const catalogs = {
    departments: [{ id: '9007199254740993', name: 'Engineering' }],
    countries: [{ code: 'IN', name: 'India' }],
    currencies: [
      { code: 'USD', name: 'US dollar', minorUnits: 2 },
      { code: 'JPY', name: 'Yen', minorUnits: 0 },
      { code: 'KWD', name: 'Kuwaiti dinar', minorUnits: 3 },
    ],
  };
  const values = {
    employeeNumber: 'emp-123',
    fullName: ' 李  明 ',
    departmentId: '9007199254740993',
    countryCode: 'IN',
    salaryAmount: '999999999999999.9900',
    currencyCode: 'USD',
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeFormPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeFormPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });
  async function ready(): Promise<void> {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
  }
  async function fill(input = values): Promise<void> {
    for (const [name, value] of Object.entries(input)) {
      const control = fixture.nativeElement.querySelector(`[name="${name}"]`) as
        HTMLInputElement | HTMLSelectElement;
      control.value = value;
      control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
    }
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }

  it('requires catalogs before enabling creation and preserves exact choice IDs', async () => {
    expect(fixture.nativeElement.textContent).toContain('Loading employee options');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    await ready();
    expect(
      fixture.nativeElement.querySelector(
        'select[name="departmentId"] option[value="9007199254740993"]',
      )?.textContent,
    ).toContain('Engineering');
    expect(fixture.nativeElement.querySelector('input[name="salaryAmount"]').type).toBe('text');
    expect(
      fixture.nativeElement.querySelector('input[name="salaryAmount"]').getAttribute('inputmode'),
    ).toBe('decimal');
  });

  it('shows required-field errors and does not send an invalid form', async () => {
    await ready();
    await submit();
    for (const text of [
      'Employee number is required.',
      'Full name is required.',
      'Select a department.',
      'Select a country.',
      'Salary is required.',
      'Select a currency.',
    ])
      expect(fixture.nativeElement.textContent).toContain(text);
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone('/api/v1/employees');
  });

  it('prevents duplicate submissions and confirms the server-normalized employee without replay', async () => {
    await ready();
    await fill();
    await submit();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/employees');
    expect(request.request.body).toEqual(values);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    request.flush(
      {
        ...values,
        employeeNumber: 'EMP-123',
        fullName: '李  明',
        id: '9007199254740995',
        departmentName: 'Engineering',
        countryName: 'India',
        salaryAmount: '999999999999999.99',
        version: '0',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
      { status: 201, statusText: 'Created' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee added');
    expect(fixture.nativeElement.textContent).toContain('EMP-123');
    expect(fixture.nativeElement.textContent).toContain('999999999999999.99');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    http.expectNone('/api/v1/employees');
  });

  it('revalidates precision when currency changes without converting country or rounding salary', async () => {
    await ready();
    await fill({ ...values, salaryAmount: '1.001', currencyCode: 'KWD' });
    const currency = fixture.nativeElement.querySelector('select[name="currencyCode"]');
    currency.value = 'JPY';
    currency.dispatchEvent(new Event('change'));
    await submit();
    expect(fixture.nativeElement.textContent).toContain(
      'Salary has more fractional digits than the selected currency allows.',
    );
    expect(fixture.nativeElement.querySelector('input[name="salaryAmount"]').value).toBe('1.001');
    expect(fixture.nativeElement.querySelector('select[name="countryCode"]').value).toBe('IN');
    http.expectNone('/api/v1/employees');
  });

  it('keeps entries on duplicate employee numbers and clears that error only when the number changes', async () => {
    await ready();
    await fill();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne('/api/v1/employees')
      .flush(
        { code: 'EMPLOYEE_NUMBER_CONFLICT', detail: 'private SQL' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee number is already in use.');
    expect(fixture.nativeElement.textContent).not.toContain('private SQL');
    const salary = fixture.nativeElement.querySelector('input[name="salaryAmount"]');
    expect(salary.value).toBe(values.salaryAmount);
    salary.value = '1.00';
    salary.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee number is already in use.');
    const number = fixture.nativeElement.querySelector('input[name="employeeNumber"]');
    number.value = 'NEW-123';
    number.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Employee number is already in use.');
  });

  it('maps supported server field errors without rendering arbitrary response messages', async () => {
    await ready();
    await fill();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/employees').flush(
      {
        code: 'VALIDATION_ERROR',
        errors: [
          { field: 'salaryAmount', code: 'EXCESS_PRECISION', message: 'private value' },
          { field: 'departmentId', code: 'UNKNOWN_REFERENCE', message: 'SQL details' },
          { field: '__proto__', code: 'anything', message: 'untrusted' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'Salary has more fractional digits than the selected currency allows.',
    );
    expect(fixture.nativeElement.textContent).toContain('Select a supported department.');
    for (const value of ['private value', 'SQL details', 'untrusted'])
      expect(fixture.nativeElement.textContent).not.toContain(value);
    expect(fixture.nativeElement.querySelector('input[name="fullName"]').value).toBe(
      values.fullName,
    );
  });

  it.each([
    [
      403,
      'CSRF_INVALID',
      'The security token was rejected. Your entries are preserved. Save again to refresh it.',
    ],
    [403, 'ACCESS_DENIED', 'You do not have permission to add employees.'],
    [401, 'AUTHENTICATION_REQUIRED', 'Your session has ended. Please sign in again.'],
    [
      0,
      '',
      'Save could not be confirmed. Check the directory for this employee number before trying again.',
    ],
    [
      500,
      'INTERNAL_ERROR',
      'Save could not be confirmed. Check the directory for this employee number before trying again.',
    ],
  ])('preserves input and does not automatically retry HTTP %s', async (status, code, message) => {
    await ready();
    await fill();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const pending = http.expectOne('/api/v1/employees');
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ code, detail: 'private detail' }, { status, statusText: 'Rejected' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(fixture.nativeElement.querySelector('input[name="fullName"]').value).toBe(
      values.fullName,
    );
    http.expectNone('/api/v1/employees');
  });

  it('offers retry for missing catalogs and blocks creation if catalogs are incomplete', async () => {
    http.expectOne('/api/v1/reference-data').flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee options could not be loaded.');
    Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === 'Retry options')!
      .click();
    await fixture.whenStable();
    http
      .expectOne('/api/v1/reference-data')
      .flush({ departments: [], countries: [], currencies: [] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee setup data is incomplete.');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    http.expectNone('/api/v1/employees');
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { EmployeeFormPage } from './employee-form-page';

describe('Employee editing', () => {
  let fixture: ComponentFixture<EmployeeFormPage>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const id = '9007199254740993';
  const url = `/api/v1/employees/${id}`;
  const catalogs = {
    departments: [{ id: '9007199254740995', name: 'Engineering' }],
    countries: [{ code: 'IN', name: 'India' }],
    currencies: [{ code: 'USD', name: 'US dollar', minorUnits: 2 }],
  };
  const employee = {
    id,
    employeeNumber: 'EMP-1',
    fullName: '李  明',
    departmentId: '9007199254740995',
    departmentName: 'Engineering',
    countryCode: 'IN',
    countryName: 'India',
    salaryAmount: '999999999999999.99',
    currencyCode: 'USD',
    version: '9007199254740997',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  };

  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({ id }));
    await TestBed.configureTestingModule({
      imports: [EmployeeFormPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
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
    http.expectOne(url).flush(employee);
    await fixture.whenStable();
  }
  function input(name: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[name="${name}"]`);
  }
  async function change(name: string, value: string): Promise<void> {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }
  async function click(label: string): Promise<void> {
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === label);
    expect(button).toBeDefined();
    button!.click();
    await fixture.whenStable();
  }

  it('waits for both reads, fills all six exact values, and never exposes version as an editable field', async () => {
    expect(fixture.nativeElement.textContent).toContain('Loading employee');
    http.expectOne(url).flush(employee);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    for (const key of [
      'employeeNumber',
      'fullName',
      'departmentId',
      'countryCode',
      'salaryAmount',
      'currencyCode',
    ] as const)
      expect(input(key).value).toBe(employee[key]);
    expect(fixture.nativeElement.querySelector('[name="version"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Edit employee');
    http.expectNone('/api/v1/auth/csrf');
  });

  it('validates before PUT and submits the original exact version once, confirming only the server response', async () => {
    await ready();
    await change('salaryAmount', '1.001');
    await submit();
    expect(fixture.nativeElement.textContent).toContain('Salary has more fractional digits');
    http.expectNone('/api/v1/auth/csrf');
    await change('salaryAmount', '999999999999999.9900');
    await change('fullName', ' Updated  name ');
    await submit();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      employeeNumber: employee.employeeNumber,
      fullName: ' Updated  name ',
      departmentId: employee.departmentId,
      countryCode: 'IN',
      salaryAmount: '999999999999999.9900',
      currencyCode: 'USD',
      version: employee.version,
    });
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    request.flush({ ...employee, fullName: 'Updated  name', version: '9007199254740998' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee updated');
    expect(fixture.nativeElement.textContent).toContain('Updated  name');
    expect(fixture.nativeElement.textContent).toContain('999999999999999.99');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(document.activeElement?.id).toBe('confirmation-heading');
    http.expectNone(url);
  });

  it('preserves a stale draft and reloads only on explicit discard, including failed reload recovery', async () => {
    await ready();
    await change('fullName', 'My unsaved draft');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne(url)
      .flush(
        { code: 'STALE_EMPLOYEE', detail: 'private detail' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(input('fullName').value).toBe('My unsaved draft');
    expect(fixture.nativeElement.textContent).toContain('Another update changed this employee');
    expect(fixture.nativeElement.textContent).not.toContain('private detail');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    await submit();
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone(url);
    await click('Discard draft and load latest');
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(input('fullName').value).toBe('My unsaved draft');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    await click('Discard draft and load latest');
    http
      .expectOne(url)
      .flush({ ...employee, fullName: 'Someone else', version: '9007199254740998' });
    await fixture.whenStable();
    expect(input('fullName').value).toBe('Someone else');
    expect(fixture.nativeElement.textContent).not.toContain('Another update changed this employee');
    await change('fullName', 'Reviewed latest');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne(url);
    expect(request.request.body.version).toBe('9007199254740998');
    request.flush({ ...employee, fullName: 'Reviewed latest', version: '9007199254740999' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee updated');
  });

  it.each([0, 500, 409])(
    'blocks replay after an unconfirmed or unknown-conflict response %s',
    async (status) => {
      await ready();
      await change('fullName', 'Preserved draft');
      await submit();
      http.expectOne('/api/v1/auth/csrf').flush(null);
      const request = http.expectOne(url);
      if (status === 0) request.error(new ProgressEvent('error'));
      else
        request.flush(
          { code: 'UNKNOWN', detail: 'private detail' },
          { status, statusText: 'Rejected' },
        );
      await fixture.whenStable();
      expect(input('fullName').value).toBe('Preserved draft');
      expect(fixture.nativeElement.textContent).toContain('load the latest employee');
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
      await submit();
      http.expectNone('/api/v1/auth/csrf');
      http.expectNone(url);
    },
  );

  it('allows correcting a duplicate without replacing the original version', async () => {
    await ready();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne(url)
      .flush({ code: 'EMPLOYEE_NUMBER_CONFLICT' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee number is already in use.');
    await change('employeeNumber', 'REPLACEMENT');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne(url);
    expect(request.request.body.version).toBe(employee.version);
    request.flush({ ...employee, employeeNumber: 'REPLACEMENT', version: '9007199254740998' });
  });

  it('reports missing employees safely and never offers an update before a successful load', async () => {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    http
      .expectOne(url)
      .flush({ detail: 'private detail' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee was not found.');
    expect(fixture.nativeElement.textContent).not.toContain('private detail');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    http.expectNone('/api/v1/auth/csrf');
  });

  it('offers a retry after initial load failure without enabling a blank replacement', async () => {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    await click('Retry employee');
    http.expectOne(url).flush(employee);
    await fixture.whenStable();
    expect(input('fullName').value).toBe(employee.fullName);
  });

  it.each(['9223372036854775807', '00000000000000000000001'])(
    'loads valid positive Long target %s exactly',
    async (valid) => {
      http.expectOne('/api/v1/reference-data').flush(catalogs);
      const obsolete = http.expectOne(url);
      params.next(convertToParamMap({ id: valid }));
      await fixture.whenStable();
      expect(obsolete.cancelled).toBe(true);
      http.expectOne(`/api/v1/employees/${valid}`).flush(employee);
      await fixture.whenStable();
      expect(input('fullName').value).toBe(employee.fullName);
    },
  );

  it('cancels an obsolete employee read when navigating between edit IDs on the reused route', async () => {
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    const obsolete = http.expectOne(url);
    params.next(convertToParamMap({ id: '2' }));
    await fixture.whenStable();
    expect(obsolete.cancelled).toBe(true);
    http
      .expectOne('/api/v1/employees/2')
      .flush({ ...employee, id: '2', version: '0', fullName: 'Second employee' });
    await fixture.whenStable();
    expect(input('fullName').value).toBe('Second employee');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const request = http.expectOne('/api/v1/employees/2');
    expect(request.request.body.version).toBe('0');
    request.flush({ ...employee, id: '2', version: '0' });
  });

  it.each(['0', '-1', '1e3', 'abc', '9223372036854775808'])(
    'rejects invalid edit target %s before lookup',
    async (invalid) => {
      http.expectOne('/api/v1/reference-data').flush(catalogs);
      const obsolete = http.expectOne(url);
      params.next(convertToParamMap({ id: invalid }));
      await fixture.whenStable();
      expect(obsolete.cancelled).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('Employee ID is invalid.');
      expect(fixture.nativeElement.querySelector('form')).toBeNull();
      http.expectNone((request) => request.url.startsWith('/api/v1/employees/'));
    },
  );

  it.each([
    [403, 'CSRF_INVALID', 'The security token was rejected.'],
    [403, 'ACCESS_DENIED', 'You do not have permission to edit employees.'],
    [401, 'AUTHENTICATION_REQUIRED', 'Your session has ended.'],
  ])('retains draft on %s %s without silently resubmitting', async (status, code, message) => {
    await ready();
    await change('fullName', 'Preserved draft');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne(url)
      .flush({ code, detail: 'private detail' }, { status, statusText: 'Rejected' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(message);
    expect(fixture.nativeElement.textContent).not.toContain('private detail');
    expect(input('fullName').value).toBe('Preserved draft');
    http.expectNone(url);
    if (code === 'CSRF_INVALID') {
      await submit();
      http.expectOne('/api/v1/auth/csrf').flush(null);
      const retried = http.expectOne(url);
      expect(retried.request.body.version).toBe(employee.version);
      retried.flush({ ...employee, fullName: 'Preserved draft', version: '9007199254740998' });
    }
  });

  it('blocks saves after a missing update target without creating a replacement', async () => {
    await ready();
    await change('fullName', 'Draft');
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne(url)
      .flush({ code: 'EMPLOYEE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee was not found.');
    expect(input('fullName').value).toBe('Draft');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    http.expectNone('/api/v1/employees');
  });

  it('preserves loaded values when options fail and permits saving only after option retry', async () => {
    http.expectOne(url).flush(employee);
    http.expectOne('/api/v1/reference-data').flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    await submit();
    http.expectNone('/api/v1/auth/csrf');
    await click('Retry options');
    http.expectOne('/api/v1/reference-data').flush(catalogs);
    await fixture.whenStable();
    expect(input('fullName').value).toBe(employee.fullName);
    expect(input('departmentId').value).toBe(employee.departmentId);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });

  it('accepts an unchanged save using the existing server version', async () => {
    await ready();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne(url).flush(employee);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Employee updated');
    http.expectNone(url);
  });

  it('does not apply a pending update response to a different employee after route reuse', async () => {
    await ready();
    await submit();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const pending = http.expectOne(url);
    params.next(convertToParamMap({ id: '2' }));
    await fixture.whenStable();
    expect(pending.cancelled).toBe(true);
    http
      .expectOne('/api/v1/employees/2')
      .flush({ ...employee, id: '2', version: '0', fullName: 'Second employee' });
    await fixture.whenStable();
    expect(input('fullName').value).toBe('Second employee');
    expect(fixture.nativeElement.textContent).not.toContain('Employee updated');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });
});

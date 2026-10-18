import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, map, of, startWith, switchMap } from 'rxjs';
import {
  Employee,
  EmployeeListQuery,
  EmployeePage,
  EmployeeSortField,
  SortDirection,
} from './employee';
import { EmployeeApi } from './employee-api';
import { ReferenceDataApi } from '../reference-data/reference-data-api';
import { ReferenceData } from '../reference-data/reference-data';

type DirectoryState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: EmployeePage }
  | { readonly kind: 'error'; readonly message: string };

type CatalogState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly data: ReferenceData }
  | { readonly kind: 'error'; readonly message: string };

const searchValidator: ValidatorFn = (control) => {
  const value = (control.value as string).trim();
  if (Array.from(value).length > 200) return { searchTooLong: true };
  return value.includes('\0') ? { invalidSearch: true } : null;
};

const salarySortValidator: ValidatorFn = (form) =>
  form.get('sortField')?.value === 'salaryAmount' && !form.get('currencyCode')?.value
    ? { currencyRequired: true }
    : null;

@Component({
  selector: 'app-employees-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './employees-page.html',
  styleUrl: './employees-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeesPage {
  private readonly api = inject(EmployeeApi);
  private readonly referenceDataApi = inject(ReferenceDataApi);
  private readonly catalogRequests = new BehaviorSubject<void>(undefined);
  private readonly requests = new BehaviorSubject<EmployeeListQuery>({ page: 0, size: 25, q: '' });
  protected readonly query = signal(this.requests.value);
  protected readonly search = new FormControl('', {
    nonNullable: true,
    validators: searchValidator,
  });
  protected readonly form = new FormGroup(
    {
      q: this.search,
      departmentId: new FormControl('', { nonNullable: true }),
      countryCode: new FormControl('', { nonNullable: true }),
      currencyCode: new FormControl('', { nonNullable: true }),
      sortField: new FormControl<EmployeeSortField>('employeeNumber', { nonNullable: true }),
      sortDirection: new FormControl<SortDirection>('asc', { nonNullable: true }),
    },
    { validators: salarySortValidator },
  );
  protected readonly sortFields: readonly { value: EmployeeSortField; label: string }[] = [
    { value: 'employeeNumber', label: 'Employee number' },
    { value: 'fullName', label: 'Employee name' },
    { value: 'departmentName', label: 'Department' },
    { value: 'countryCode', label: 'Country code' },
    { value: 'currencyCode', label: 'Currency code' },
    { value: 'salaryAmount', label: 'Annual base salary' },
  ];
  protected readonly catalogState = toSignal(
    this.catalogRequests.pipe(
      // Lookup failure must not stop ordinary browsing or search; retry this read independently.
      switchMap(() =>
        this.referenceDataApi.get().pipe(
          map((data): CatalogState => ({ kind: 'loaded', data })),
          catchError((error: unknown) =>
            of<CatalogState>({ kind: 'error', message: this.catalogError(error) }),
          ),
          startWith<CatalogState>({ kind: 'loading' }),
        ),
      ),
    ),
    { requireSync: true },
  );
  protected readonly catalogs = computed(() => {
    const state = this.catalogState();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly columns = ['employee', 'department', 'country', 'salary'];
  protected readonly state = toSignal(
    this.requests.pipe(
      // Cancelling the previous HttpClient subscription prevents stale search/page results winning.
      switchMap((query) =>
        this.api.list(query).pipe(
          map((page): DirectoryState => ({ kind: 'loaded', page })),
          catchError((error: unknown) =>
            of<DirectoryState>({ kind: 'error', message: this.loadError(error) }),
          ),
          startWith<DirectoryState>({ kind: 'loading' }),
        ),
      ),
    ),
    { requireSync: true },
  );
  protected readonly page = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.page : null;
  });
  protected readonly rows = computed(() => this.page()?.items ?? []);
  protected readonly hasCriteria = computed(() => {
    const query = this.query();
    return !!(query.q || query.departmentId || query.countryCode || query.currencyCode);
  });

  protected applyQuery(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const {
      q: draft,
      departmentId,
      countryCode,
      currencyCode,
      sortField,
      sortDirection,
    } = this.form.getRawValue();
    const q = draft.trim();
    this.search.setValue(q);
    this.load({
      page: 0,
      size: this.query().size,
      q,
      departmentId,
      countryCode,
      currencyCode,
      sort: `${sortField},${sortDirection}`,
    });
  }

  protected resetQuery(): void {
    this.form.reset();
    this.load({ page: 0, size: this.query().size, q: '' });
  }

  protected retryCatalogs(): void {
    this.catalogRequests.next();
  }

  protected changePage(event: PageEvent): void {
    this.load({
      ...this.query(),
      page: event.pageSize === this.query().size ? event.pageIndex : 0,
      size: event.pageSize,
    });
  }

  protected firstPage(): void {
    this.load({ ...this.query(), page: 0 });
  }

  protected retry(): void {
    this.load(this.query());
  }

  protected trackEmployee(_index: number, employee: Employee): string {
    return employee.id;
  }

  private load(query: EmployeeListQuery): void {
    this.query.set(query);
    this.requests.next(query);
  }

  private loadError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has ended. Please sign in again.';
      if (error.status === 403) return 'You do not have permission to view employees.';
      if (error.status === 0)
        return 'Cannot reach the server. Check your connection and try again.';
      if (error.status === 400)
        return 'The query could not be accepted. Review the search, filters, and sort, then try again.';
    }
    return 'Employees could not be loaded. Please try again.';
  }

  private catalogError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has ended. Please sign in again.';
      if (error.status === 403) return 'You do not have permission to load filter options.';
      if (error.status === 0)
        return 'Cannot reach the server to load filter options. Please try again.';
    }
    return 'Filter options could not be loaded. You can still browse and search.';
  }
}

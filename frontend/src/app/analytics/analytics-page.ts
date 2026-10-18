import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BehaviorSubject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { employeeSearchValidator } from '../employees/employee-search-validation';
import { ReferenceData } from '../reference-data/reference-data';
import { ReferenceDataApi } from '../reference-data/reference-data-api';
import { CompensationGrouping, CompensationQuery, CompensationReport } from './compensation';
import { CompensationApi } from './compensation-api';

type ReportState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly report: CompensationReport }
  | { readonly kind: 'error'; readonly message: string };

type CatalogState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly data: ReferenceData }
  | { readonly kind: 'error'; readonly message: string };

@Component({
  selector: 'app-analytics-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './analytics-page.html',
  styleUrl: './analytics-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPage {
  private readonly api = inject(CompensationApi);
  private readonly referenceData = inject(ReferenceDataApi);
  private readonly requests = new BehaviorSubject<CompensationQuery>({
    groupBy: 'department',
    q: '',
  });
  private readonly catalogRequests = new BehaviorSubject<void>(undefined);
  protected readonly query = signal(this.requests.value);
  protected readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true, validators: employeeSearchValidator }),
    groupBy: new FormControl<CompensationGrouping>('department', { nonNullable: true }),
    departmentId: new FormControl('', { nonNullable: true }),
    countryCode: new FormControl('', { nonNullable: true }),
    currencyCode: new FormControl('', { nonNullable: true }),
  });
  protected readonly catalogState = toSignal(
    this.catalogRequests.pipe(
      switchMap(() =>
        this.referenceData.get().pipe(
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
  protected readonly state = toSignal(
    this.requests.pipe(
      // A new applied query cancels the obsolete read and removes its results immediately.
      switchMap((query) =>
        this.api.get(query).pipe(
          map((report): ReportState => ({ kind: 'loaded', report })),
          catchError((error: unknown) =>
            of<ReportState>({ kind: 'error', message: this.reportError(error) }),
          ),
          startWith<ReportState>({ kind: 'loading' }),
        ),
      ),
    ),
    { requireSync: true },
  );
  protected readonly hasFilters = computed(() => {
    const query = this.query();
    return !!(query.q || query.departmentId || query.countryCode || query.currencyCode);
  });
  protected readonly appliedCriteria = computed(() => {
    const query = this.query();
    const catalogs = this.catalogs();
    const parts = [`Grouped by ${query.groupBy}`];
    if (query.q) parts.push(`Search: ${query.q}`);
    if (query.departmentId)
      parts.push(
        `Department: ${catalogs?.departments.find((d) => d.id === query.departmentId)?.name ?? 'Selected department'}`,
      );
    if (query.countryCode)
      parts.push(
        `Country: ${catalogs?.countries.find((c) => c.code === query.countryCode)?.name ?? query.countryCode}`,
      );
    if (query.currencyCode) parts.push(`Currency: ${query.currencyCode}`);
    if (!this.hasFilters()) parts.push('All employees');
    return parts.join(' · ');
  });

  protected applyQuery(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    const q = values.q.trim();
    this.form.controls.q.setValue(q);
    this.load({ ...values, q });
  }

  protected resetQuery(): void {
    this.form.reset();
    this.load({ groupBy: 'department', q: '' });
  }

  protected retry(): void {
    this.load(this.query());
  }

  protected retryCatalogs(): void {
    this.catalogRequests.next();
  }

  private load(query: CompensationQuery): void {
    this.query.set(query);
    this.requests.next(query);
  }

  private reportError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has ended. Please sign in again.';
      if (error.status === 403) return 'You do not have permission to view compensation analytics.';
      if (error.status === 0)
        return 'Cannot reach the server. Check your connection and try again.';
      if (error.status === 400)
        return 'The report query could not be accepted. Review the search, filters, and grouping, then try again.';
    }
    return 'Compensation analytics could not be loaded. Please try again.';
  }

  private catalogError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has ended. Please sign in again.';
      if (error.status === 403) return 'You do not have permission to load filter options.';
      if (error.status === 0)
        return 'Cannot reach the server to load filter options. Please try again.';
    }
    return 'Filter options could not be loaded. You can still view reports and search.';
  }
}

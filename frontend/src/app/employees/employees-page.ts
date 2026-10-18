import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { BehaviorSubject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { Employee, EmployeeListQuery, EmployeePage } from './employee';
import { EmployeeApi } from './employee-api';

type DirectoryState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: EmployeePage }
  | { readonly kind: 'error'; readonly message: string };

const searchValidator: ValidatorFn = (control) => {
  const value = (control.value as string).trim();
  if (Array.from(value).length > 200) return { searchTooLong: true };
  return value.includes('\0') ? { invalidSearch: true } : null;
};

@Component({
  selector: 'app-employees-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatTableModule,
  ],
  templateUrl: './employees-page.html',
  styleUrl: './employees-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeesPage {
  private readonly api = inject(EmployeeApi);
  private readonly requests = new BehaviorSubject<EmployeeListQuery>({ page: 0, size: 25, q: '' });
  protected readonly query = signal(this.requests.value);
  protected readonly search = new FormControl('', {
    nonNullable: true,
    validators: searchValidator,
  });
  protected readonly form = new FormGroup({ q: this.search });
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

  protected submitSearch(): void {
    if (this.search.invalid) {
      this.search.markAsTouched();
      return;
    }
    const q = this.search.value.trim();
    this.search.setValue(q);
    this.load({ ...this.query(), page: 0, q });
  }

  protected clearSearch(): void {
    this.search.reset();
    this.load({ ...this.query(), page: 0, q: '' });
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
      if (error.status === 400) return 'The search could not be accepted. Review it and try again.';
    }
    return 'Employees could not be loaded. Please try again.';
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  defer,
  EMPTY,
  finalize,
  Observable,
  startWith,
  Subject,
  switchMap,
  takeUntil,
} from 'rxjs';
import { ReferenceData } from '../reference-data/reference-data';
import { ReferenceDataApi } from '../reference-data/reference-data-api';
import { Employee } from './employee';
import { EmployeeApi } from './employee-api';
import {
  EmployeeField,
  employeeFormMessages,
  employeeNumberError,
  fullNameError,
  salaryError,
} from './employee-form-validation';

function textValidator(check: (value: string) => string | null): ValidatorFn {
  return (control) => {
    const message = check(control.value as string);
    return message ? { input: message } : null;
  };
}

@Component({
  selector: 'app-employee-form-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './employee-form-page.html',
  styleUrl: './employee-form-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeFormPage {
  private readonly api = inject(EmployeeApi);
  private readonly referenceData = inject(ReferenceDataApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly reloadEmployee = new Subject<void>();
  private readonly routeChanged = new Subject<void>();
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly confirmation = viewChild<ElementRef<HTMLHeadingElement>>('confirmation');
  protected readonly catalogs = signal<ReferenceData | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly optionsError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saved = signal<Employee | null>(null);
  protected readonly employeeId = signal<string | null>(null);
  protected readonly editing = computed(() => this.employeeId() !== null);
  protected readonly original = signal<Employee | null>(null);
  protected readonly employeeLoading = signal(false);
  protected readonly employeeError = signal<string | null>(null);
  protected readonly employeeRetry = signal(false);
  protected readonly reloadRequired = signal(false);
  protected readonly optionsReady = computed(() => {
    const options = this.catalogs();
    return !!(options?.departments.length && options.countries.length && options.currencies.length);
  });
  protected readonly form = inject(FormBuilder).nonNullable.group({
    employeeNumber: ['', textValidator(employeeNumberError)],
    fullName: ['', textValidator(fullNameError)],
    departmentId: ['', Validators.required],
    countryCode: ['', Validators.required],
    salaryAmount: [''],
    currencyCode: ['', Validators.required],
  });

  constructor() {
    this.form.controls.salaryAmount.addValidators(
      textValidator((value) => {
        const currency = this.catalogs()?.currencies.find(
          (currency) => currency.code === this.form.controls.currencyCode.value,
        );
        return salaryError(value, currency?.minorUnits);
      }),
    );
    this.form.controls.salaryAmount.updateValueAndValidity();
    this.form.controls.currencyCode.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.form.controls.salaryAmount.updateValueAndValidity();
    });
    this.loadOptions();
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          this.routeChanged.next();
          this.employeeId.set(params.get('id'));
          this.original.set(null);
          this.saved.set(null);
          this.employeeError.set(null);
          this.reloadRequired.set(false);
          this.saveError.set(null);
          this.form.reset();
          return this.reloadEmployee.pipe(
            startWith(undefined),
            switchMap(() => this.readEmployee()),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((employee) => {
        // Only a successful explicit read may replace a draft and its original version.
        this.original.set(employee);
        const { employeeNumber, fullName, departmentId, countryCode, salaryAmount, currencyCode } =
          employee;
        this.form.reset({
          employeeNumber,
          fullName,
          departmentId,
          countryCode,
          salaryAmount,
          currencyCode,
        });
        this.reloadRequired.set(false);
        this.saveError.set(null);
      });
  }

  protected loadEmployee(): void {
    if (!this.employeeLoading() && !this.saving()) this.reloadEmployee.next();
  }

  private readEmployee(): Observable<Employee> {
    const id = this.employeeId();
    if (id === null) return EMPTY;
    // Bound the path to the API's positive Long range without rounding through Number.
    const significantDigits = id.replace(/^0+/, '');
    if (
      !/^[0-9]+$/.test(id) ||
      significantDigits.length > 19 ||
      BigInt(id) < 1n ||
      BigInt(id) > 9223372036854775807n
    ) {
      this.employeeError.set('Employee ID is invalid.');
      this.employeeRetry.set(false);
      return EMPTY;
    }
    return defer(() => {
      this.employeeLoading.set(true);
      this.employeeError.set(null);
      return this.api.get(id);
    }).pipe(
      catchError((error: unknown) => {
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        this.employeeRetry.set(status === 0 || status >= 500);
        this.employeeError.set(
          status === 404
            ? 'Employee was not found.'
            : status === 401
              ? 'Your session has ended. Please sign in again.'
              : status === 403
                ? 'You do not have permission to view this employee.'
                : status === 400
                  ? 'Employee ID is invalid.'
                  : 'Employee could not be loaded. Your current entries have not been replaced.',
        );
        return EMPTY;
      }),
      finalize(() => this.employeeLoading.set(false)),
    );
  }

  protected loadOptions(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.optionsError.set(null);
    this.referenceData
      .get()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (options) => {
          this.catalogs.set(options);
          this.form.controls.salaryAmount.updateValueAndValidity();
          if (!this.optionsReady())
            this.optionsError.set(
              'Employee setup data is incomplete. A department, country, and currency are required.',
            );
        },
        error: () =>
          this.optionsError.set('Employee options could not be loaded. Please try again.'),
      });
  }

  protected fieldError(field: EmployeeField): string | null {
    const control = this.form.controls[field];
    if (!control.touched) return null;
    if (control.hasError('server')) return control.getError('server') as string;
    if (control.hasError('input')) return control.getError('input') as string;
    return control.hasError('required') ? employeeFormMessages[field].REQUIRED : null;
  }

  protected submit(): void {
    if (
      this.loading() ||
      this.employeeLoading() ||
      this.saving() ||
      this.saved() ||
      this.reloadRequired() ||
      !this.optionsReady() ||
      (this.editing() && !this.original())
    )
      return;
    this.saveError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    const original = this.original();
    const request = original
      ? this.api.update(original.id, { ...this.form.getRawValue(), version: original.version })
      : this.api.create(this.form.getRawValue());
    request
      .pipe(
        takeUntil(this.routeChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (employee) => {
          this.saved.set(employee);
          afterNextRender(() => this.confirmation()?.nativeElement.focus(), {
            injector: this.injector,
          });
        },
        error: (error: unknown) => this.handleSaveError(error),
      });
  }

  protected cancel(): void {
    if (!this.saving() && !this.employeeLoading()) void this.router.navigate(['/employees']);
  }

  private handleSaveError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      if (
        this.editing() &&
        error.status === 409 &&
        error.error?.code !== 'EMPLOYEE_NUMBER_CONFLICT'
      ) {
        this.reloadRequired.set(true);
        this.saveError.set(
          error.error?.code === 'STALE_EMPLOYEE'
            ? 'Another update changed this employee. Your draft is preserved. Discard it and load the latest employee before editing again.'
            : 'The update conflicted with the current employee. Your draft is preserved. Discard it and load the latest employee before editing again.',
        );
        return;
      }
      if (this.editing() && error.status === 404) {
        this.reloadRequired.set(true);
        this.saveError.set(
          'Employee was not found. Your draft is preserved; it has not been saved.',
        );
        return;
      }
      if (error.status === 409 && error.error?.code === 'EMPLOYEE_NUMBER_CONFLICT') {
        this.form.controls.employeeNumber.setErrors({
          server: 'Employee number is already in use.',
        });
        this.saveError.set('Choose a different employee number.');
        return;
      }
      if (error.status === 400) {
        this.applyServerErrors(error.error as unknown);
        this.saveError.set('Employee could not be saved. Review the form and try again.');
        return;
      }
      if (error.status === 403) {
        this.saveError.set(
          error.error?.code === 'CSRF_INVALID'
            ? 'The security token was rejected. Your entries are preserved. Save again to refresh it.'
            : `You do not have permission to ${this.editing() ? 'edit' : 'add'} employees.`,
        );
        return;
      }
      if (error.status === 401) {
        this.saveError.set('Your session has ended. Please sign in again.');
        return;
      }
    }
    // A lost response does not prove the write failed. Never replay a mutation automatically.
    if (this.editing()) {
      this.reloadRequired.set(true);
      this.saveError.set(
        'Save could not be confirmed. Your draft is preserved. Discard it and load the latest employee to check the saved values before editing again.',
      );
      return;
    }
    this.saveError.set(
      'Save could not be confirmed. Check the directory for this employee number before trying again.',
    );
  }

  private applyServerErrors(body: unknown): void {
    if (!body || typeof body !== 'object' || !('errors' in body) || !Array.isArray(body.errors))
      return;
    for (const entry of body.errors as unknown[]) {
      if (
        !entry ||
        typeof entry !== 'object' ||
        !('field' in entry) ||
        !('code' in entry) ||
        typeof entry.field !== 'string' ||
        typeof entry.code !== 'string'
      )
        continue;
      if (!Object.hasOwn(employeeFormMessages, entry.field)) continue;
      const field = entry.field as EmployeeField;
      const messages: Record<string, string> = employeeFormMessages[field];
      const message = Object.hasOwn(messages, entry.code)
        ? messages[entry.code]
        : 'Review this value.';
      this.form.controls[field].setErrors({ server: message });
    }
  }
}

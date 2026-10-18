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
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
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
  selector: 'app-employee-create-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './employee-create-page.html',
  styleUrl: './employee-create-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeCreatePage {
  private readonly api = inject(EmployeeApi);
  private readonly referenceData = inject(ReferenceDataApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly confirmation = viewChild<ElementRef<HTMLHeadingElement>>('confirmation');
  protected readonly catalogs = signal<ReferenceData | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly optionsError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly created = signal<Employee | null>(null);
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
    if (this.loading() || this.saving() || this.created() || !this.optionsReady()) return;
    this.saveError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    this.api
      .create(this.form.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (employee) => {
          this.created.set(employee);
          afterNextRender(() => this.confirmation()?.nativeElement.focus(), {
            injector: this.injector,
          });
        },
        error: (error: unknown) => this.handleSaveError(error),
      });
  }

  protected cancel(): void {
    if (!this.saving()) void this.router.navigate(['/employees']);
  }

  private handleSaveError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
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
            : 'You do not have permission to add employees.',
        );
        return;
      }
      if (error.status === 401) {
        this.saveError.set('Your session has ended. Please sign in again.');
        return;
      }
    }
    // A lost response does not prove the write failed. Never replay a create automatically.
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

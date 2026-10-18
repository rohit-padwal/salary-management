import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { safeReturnUrl } from './return-url';
import { SessionService } from './session-service';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage implements OnInit {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  protected readonly checking = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = this.route.snapshot.queryParamMap.get('reason') === 'expired';
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  ngOnInit(): void {
    this.session
      .restore()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.checking.set(false)),
      )
      .subscribe({
        next: (user) => {
          if (user) this.openFeature();
        },
        error: () => this.error.set('Cannot check your session. You can try signing in again.'),
      });
  }

  protected submit(): void {
    if (this.checking() || this.submitting()) return;
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.form.controls.password.reset();
    this.session
      .login(username, password)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: () => this.openFeature(),
        error: (error: unknown) => this.error.set(this.loginError(error)),
      });
  }

  private openFeature(): void {
    void this.router.navigateByUrl(this.returnUrl, { replaceUrl: true });
  }

  private loginError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401 && error.error?.code === 'INVALID_CREDENTIALS') {
        return 'The username or password is incorrect.';
      }
      if (error.status === 403 && error.error?.code === 'CSRF_INVALID') {
        return 'The security token was rejected. Please try again.';
      }
      if (error.status === 0)
        return 'Cannot reach the server. Check your connection and try again.';
    }
    return 'Sign-in is unavailable. Please try again.';
  }
}

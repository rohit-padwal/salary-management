import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, finalize } from 'rxjs';
import { SessionService } from '../auth/session-service';

@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  private readonly mainContent = viewChild.required<ElementRef<HTMLElement>>('mainContent');
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly session = inject(SessionService);
  protected readonly signingOut = signal(false);
  protected readonly logoutError = signal<string | null>(null);

  constructor() {
    this.router.events
      .pipe(
        // Preserve native focus on initial load; subsequent SPA navigation needs a content target.
        filter((event) => event instanceof NavigationEnd && event.id > 1),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        // Wait for the routed view before moving keyboard focus to its content.
        afterNextRender(() => this.mainContent().nativeElement.focus(), {
          injector: this.injector,
        });
      });
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.mainContent().nativeElement.focus();
  }

  protected logout(): void {
    if (this.signingOut()) return;
    this.signingOut.set(true);
    this.logoutError.set(null);
    this.session
      .logout()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.signingOut.set(false)),
      )
      .subscribe({
        next: () => {
          void this.router.navigate(['/login'], { replaceUrl: true });
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            this.session.clear();
            void this.router.navigate(['/login'], { replaceUrl: true });
          } else {
            this.logoutError.set('Sign-out could not be confirmed. Please try again.');
          }
        },
      });
  }
}

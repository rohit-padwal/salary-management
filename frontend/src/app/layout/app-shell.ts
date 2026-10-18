import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

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
}

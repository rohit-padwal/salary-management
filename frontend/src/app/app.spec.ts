import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from './app';
import { appConfig } from './app.config';

describe('Application navigation', () => {
  let fixture: ComponentFixture<App>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: appConfig.providers,
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  async function navigate(url: string): Promise<HTMLElement> {
    await router.navigateByUrl(url);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('opens the employee directory by default inside the shared shell', async () => {
    const page = await navigate('/');
    expect(router.url).toBe('/employees');
    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('nav[aria-label="Primary"]')).not.toBeNull();
    expect(page.querySelector('main h1')?.textContent?.trim()).toBe('Employees');
    expect(page.querySelectorAll('main')).toHaveLength(1);
    expect(document.title).toBe('Employees | Salary Management');
  });

  it('navigates using the analytics link and updates the active page', async () => {
    const page = await navigate('/employees');
    const analytics = page.querySelector<HTMLAnchorElement>('nav a[href="/analytics"]');
    expect(analytics).not.toBeNull();
    analytics!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/analytics');
    expect(page.querySelector('main h1')?.textContent?.trim()).toBe('Compensation analytics');
    expect(analytics?.getAttribute('aria-current')).toBe('page');
    expect(page.querySelector('nav a[href="/employees"]')?.hasAttribute('aria-current')).toBe(
      false,
    );
    expect(document.title).toBe('Compensation analytics | Salary Management');
  });

  it('opens analytics directly and keeps its current-page indicator with query parameters', async () => {
    const page = await navigate('/analytics?countryCode=IN');
    expect(page.querySelector('nav a[aria-current="page"]')?.textContent?.trim()).toBe('Analytics');
    expect(page.querySelector('main h1')?.textContent?.trim()).toBe('Compensation analytics');
  });

  it('keeps a stable shell while switching pages and navigating back', async () => {
    const page = await navigate('/employees');
    const header = page.querySelector('header');
    await navigate('/analytics');
    await navigate('/employees');
    expect(page.querySelector('header')).toBe(header);
    expect(page.querySelectorAll('nav a[aria-current="page"]')).toHaveLength(1);
    expect(page.querySelector('nav a[aria-current="page"]')?.textContent?.trim()).toBe('Employees');
  });

  it('offers a skip link that focuses main content without changing the current route', async () => {
    const page = await navigate('/analytics');
    const skip = page.querySelector<HTMLAnchorElement>('.skip-link');
    expect(skip?.textContent?.trim()).toBe('Skip to main content');
    skip!.click();
    await fixture.whenStable();
    const main = page.querySelector('main');
    expect(main?.id).toBe('main-content');
    expect(main?.getAttribute('tabindex')).toBe('-1');
    expect(document.activeElement).toBe(main);
    expect(router.url).toBe('/analytics');
  });

  it('moves focus to main content after a route change', async () => {
    const page = await navigate('/employees');
    const analytics = page.querySelector<HTMLAnchorElement>('nav a[href="/analytics"]');
    analytics!.focus();
    analytics!.click();
    await fixture.whenStable();
    expect(document.activeElement).toBe(page.querySelector('main'));
  });

  it('retains native browser focus on the first page load', async () => {
    const page = await navigate('/employees');
    expect(document.activeElement).not.toBe(page.querySelector('main'));
  });

  it('shows a recoverable not-found page for unknown routes', async () => {
    const page = await navigate('/missing-page');
    expect(page.querySelector('main h1')?.textContent?.trim()).toBe('Page not found');
    expect(page.querySelector('nav a[aria-current="page"]')).toBeNull();
    expect(document.title).toBe('Page not found | Salary Management');
    page.querySelector<HTMLAnchorElement>('main a[href="/employees"]')!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/employees');
    expect(page.querySelector('main h1')?.textContent?.trim()).toBe('Employees');
  });
});

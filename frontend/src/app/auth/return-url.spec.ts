import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('preserves approved feature paths, query values, and fragments', () => {
    expect(safeReturnUrl('/analytics?countryCode=IN#summary')).toBe(
      '/analytics?countryCode=IN#summary',
    );
    expect(safeReturnUrl('/employees?q=A%20B')).toBe('/employees?q=A%20B');
    expect(safeReturnUrl('/employees/new')).toBe('/employees/new');
    expect(safeReturnUrl('/employees/9007199254740993/edit')).toBe(
      '/employees/9007199254740993/edit',
    );
    expect(safeReturnUrl('/employees/1/edit?view=form#salary')).toBe(
      '/employees/1/edit?view=form#salary',
    );
  });

  it.each([
    null,
    '',
    '/',
    '/login',
    '/missing',
    'https://example.com',
    '//example.com',
    '/employees/../login',
    '/employees/new/../login',
    '/employees/new/other',
    '/employees/1/edit/../login',
    '/employees/1/edit/other',
    '/employees/-1/edit',
    '/employees/1e3/edit',
    '/employees/1%2f2/edit',
    '/employees(aux:login)',
    '/employees\\evil',
    '/employees\n?x=1',
  ])('falls back safely for %s', (url) => {
    expect(safeReturnUrl(url)).toBe('/employees');
  });
});

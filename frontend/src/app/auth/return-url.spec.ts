import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('preserves approved feature paths, query values, and fragments', () => {
    expect(safeReturnUrl('/analytics?countryCode=IN#summary')).toBe(
      '/analytics?countryCode=IN#summary',
    );
    expect(safeReturnUrl('/employees?q=A%20B')).toBe('/employees?q=A%20B');
    expect(safeReturnUrl('/employees/new')).toBe('/employees/new');
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
    '/employees(aux:login)',
    '/employees\\evil',
    '/employees\n?x=1',
  ])('falls back safely for %s', (url) => {
    expect(safeReturnUrl(url)).toBe('/employees');
  });
});

import { stripOuterWhitespace } from './text-normalization';

describe('API text normalization', () => {
  it('strips mixed outer API whitespace while preserving internal text and Unicode characters', () => {
    expect(stripOuterWhitespace('\u001c\t\u2003Zoë  李 😀\u3000\n\u001f')).toBe('Zoë  李 😀');
    expect(stripOuterWhitespace('Zoë\u2003李')).toBe('Zoë\u2003李');
  });

  it.each(['\u00a0', '\u2007', '\u202f', '\ufeff'])(
    'preserves significant outer character U+%s',
    (character) => {
      expect(stripOuterWhitespace(`${character}Zoë${character}`)).toBe(
        `${character}Zoë${character}`,
      );
      expect(stripOuterWhitespace(` ${character}Zoë${character} `)).toBe(
        `${character}Zoë${character}`,
      );
    },
  );

  it('distinguishes API whitespace-only input from literal non-breaking spaces', () => {
    expect(stripOuterWhitespace('')).toBe('');
    expect(stripOuterWhitespace('\u001d\u2003\u205f\r\n')).toBe('');
    expect(stripOuterWhitespace('\u00a0')).toBe('\u00a0');
    expect(stripOuterWhitespace('\0')).toBe('\0');
  });
});

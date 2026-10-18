import { employeeNumberError, fullNameError, salaryError } from './employee-form-validation';

describe('Employee form validation', () => {
  it('uses API whitespace rules for identifiers and name length without removing significant characters', () => {
    expect(employeeNumberError('\u001cEMP-1\u2003')).toBeNull();
    expect(employeeNumberError('\u00a0EMP-1\u00a0')).not.toBeNull();
    expect(fullNameError('\u001c' + '😀'.repeat(200) + '\u001f')).toBeNull();
    expect(fullNameError('\u00a0' + '李'.repeat(199) + '\u00a0')).toBe(
      'Full name must contain at most 200 characters.',
    );
  });

  it('uses API blank semantics for salary errors but never trims salary syntax', () => {
    expect(salaryError('\u001c\u2003', 2)).toBe('Salary is required.');
    expect(salaryError('\u00a0', 2)).toBe(
      'Salary must be a plain decimal without symbols or separators.',
    );
    expect(salaryError('\u001c1.00\u001f', 2)).toBe(
      'Salary must be a plain decimal without symbols or separators.',
    );
  });
  it('accepts lowercase employee numbers, trims outer spaces for validation, and rejects non-ASCII expansion', () => {
    expect(employeeNumberError(' emp_123-ab ')).toBeNull();
    expect(employeeNumberError('A'.repeat(32))).toBeNull();
    for (const value of ['', '   ', '-ABC', 'A B', 'ß', 'A'.repeat(33)])
      expect(employeeNumberError(value)).not.toBeNull();
  });

  it('supports international full names and counts Unicode code points rather than UTF-16 units', () => {
    expect(fullNameError('  李  明  ')).toBeNull();
    expect(fullNameError('😀'.repeat(200))).toBeNull();
    for (const value of ['', '  ', 'a\0b', '😀'.repeat(201)])
      expect(fullNameError(value)).not.toBeNull();
  });

  it.each([
    ['0.01', 2],
    ['001.2300', 2],
    ['100.000', 0],
    ['1.001', 3],
    ['999999999999999.9999000', 4],
    ['0.0001', 4],
    ['0000000000000000001', 0],
  ])(
    'accepts exact positive salary %s for %s minor units without rounding',
    (value, minorUnits) => {
      expect(salaryError(value, minorUnits)).toBeNull();
    },
  );

  it.each(['', '  ', '1e3', '1,000', '$100', '+1', '.5', '1.', ' 1', '1 ', 'NaN', 'Infinity'])(
    'rejects unsupported salary syntax %s',
    (value) => {
      expect(salaryError(value, 2)).not.toBeNull();
    },
  );

  it.each(['0', '0.0000', '-0', '-1', '-0.01'])('rejects nonpositive salary %s', (value) => {
    expect(salaryError(value, 2)).toBe('Salary must be greater than zero.');
  });

  it('rejects meaningful excess precision for 0/2/3 digit currencies and rechecks when currency changes', () => {
    expect(salaryError('1.01', 0)).toBe(
      'Salary has more fractional digits than the selected currency allows.',
    );
    expect(salaryError('1.001', 2)).not.toBeNull();
    expect(salaryError('1.0001', 3)).not.toBeNull();
    expect(salaryError('1.001', 3)).toBeNull();
  });

  it('checks the NUMERIC storage boundary exactly despite leading zeros', () => {
    expect(salaryError('999999999999999.99', 2)).toBeNull();
    expect(salaryError('1000000000000000', 2)).toBe('Salary exceeds the supported storage range.');
    expect(salaryError('0001000000000000000', 2)).not.toBeNull();
  });
});

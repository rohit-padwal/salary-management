export const employeeFormMessages = {
  employeeNumber: {
    REQUIRED: 'Employee number is required.',
    INVALID_FORMAT:
      'Use 1–32 A–Z letters, digits, hyphens or underscores, starting with a letter or digit.',
  },
  fullName: {
    REQUIRED: 'Full name is required.',
    TOO_LONG: 'Full name must contain at most 200 characters.',
    INVALID_FORMAT: 'Full name cannot contain a null character.',
  },
  departmentId: {
    REQUIRED: 'Select a department.',
    UNKNOWN_REFERENCE: 'Select a supported department.',
    INVALID_FORMAT: 'Select a supported department.',
    MUST_BE_POSITIVE: 'Select a supported department.',
  },
  countryCode: {
    REQUIRED: 'Select a country.',
    UNKNOWN_REFERENCE: 'Select a supported country.',
    INVALID_FORMAT: 'Select a supported country.',
  },
  currencyCode: {
    REQUIRED: 'Select a currency.',
    UNKNOWN_REFERENCE: 'Select a supported currency.',
    INVALID_FORMAT: 'Select a supported currency.',
  },
  salaryAmount: {
    REQUIRED: 'Salary is required.',
    INVALID_FORMAT: 'Salary must be a plain decimal without symbols or separators.',
    MUST_BE_POSITIVE: 'Salary must be greater than zero.',
    EXCESS_PRECISION: 'Salary has more fractional digits than the selected currency allows.',
    OUT_OF_RANGE: 'Salary exceeds the supported storage range.',
  },
};

export type EmployeeField = keyof typeof employeeFormMessages;

export function employeeNumberError(value: string): string | null {
  const number = value.trim();
  if (!number) return employeeFormMessages.employeeNumber.REQUIRED;
  return /^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/.test(number)
    ? null
    : employeeFormMessages.employeeNumber.INVALID_FORMAT;
}

export function fullNameError(value: string): string | null {
  const name = value.trim();
  if (!name) return employeeFormMessages.fullName.REQUIRED;
  if (Array.from(name).length > 200) return employeeFormMessages.fullName.TOO_LONG;
  return name.includes('\0') ? employeeFormMessages.fullName.INVALID_FORMAT : null;
}

export function salaryError(value: string, minorUnits: number | undefined): string | null {
  const messages = employeeFormMessages.salaryAmount;
  if (!value.trim()) return messages.REQUIRED;
  if (!/^-?[0-9]+(?:\.[0-9]+)?$/.test(value)) return messages.INVALID_FORMAT;
  if (value.startsWith('-') || !/[1-9]/.test(value)) return messages.MUST_BE_POSITIVE;
  const [integer, fraction = ''] = value.split('.');
  // Compare digit structure only: converting a monetary value to Number would lose precision.
  if (minorUnits !== undefined && fraction.replace(/0+$/, '').length > minorUnits) {
    return messages.EXCESS_PRECISION;
  }
  return integer.replace(/^0+/, '').length > 15 ? messages.OUT_OF_RANGE : null;
}

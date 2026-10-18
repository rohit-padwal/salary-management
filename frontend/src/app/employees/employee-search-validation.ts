import { ValidatorFn } from '@angular/forms';
import { stripOuterWhitespace } from '../shared/text-normalization';

// Listing and analytics share the same literal employee-search contract.
export const employeeSearchValidator: ValidatorFn = (control) => {
  const value = stripOuterWhitespace(control.value as string);
  if (Array.from(value).length > 200) return { searchTooLong: true };
  return value.includes('\0') ? { invalidSearch: true } : null;
};

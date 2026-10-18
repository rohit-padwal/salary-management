import { ValidatorFn } from '@angular/forms';

// Listing and analytics share the same literal employee-search contract.
export const employeeSearchValidator: ValidatorFn = (control) => {
  const value = (control.value as string).trim();
  if (Array.from(value).length > 200) return { searchTooLong: true };
  return value.includes('\0') ? { invalidSearch: true } : null;
};

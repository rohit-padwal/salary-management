export interface DepartmentOption {
  readonly id: string;
  readonly name: string;
}

export interface CountryOption {
  readonly code: string;
  readonly name: string;
}

export interface CurrencyOption {
  readonly code: string;
  readonly name: string;
  readonly minorUnits: number;
}

export interface ReferenceData {
  readonly departments: readonly DepartmentOption[];
  readonly countries: readonly CountryOption[];
  readonly currencies: readonly CurrencyOption[];
}

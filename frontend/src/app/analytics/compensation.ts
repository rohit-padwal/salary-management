export type CompensationGrouping = 'department' | 'country';

export interface CompensationQuery {
  readonly groupBy: CompensationGrouping;
  readonly q: string;
  readonly departmentId?: string;
  readonly countryCode?: string;
  readonly currencyCode?: string;
}

export interface CurrencySummary {
  readonly currencyCode: string;
  readonly headcount: number;
  readonly totalSalary: string;
  readonly averageSalary: string;
  readonly minSalary: string;
  readonly maxSalary: string;
}

export interface CompensationGroup extends CurrencySummary {
  readonly groupKey: string;
  readonly groupLabel: string;
}

export interface CompensationReport {
  readonly groupBy: CompensationGrouping;
  readonly totalHeadcount: number;
  readonly currencySummaries: readonly CurrencySummary[];
  readonly groups: readonly CompensationGroup[];
}

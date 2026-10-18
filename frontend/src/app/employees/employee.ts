export interface Employee {
  readonly id: string;
  readonly employeeNumber: string;
  readonly fullName: string;
  readonly departmentId: string;
  readonly departmentName: string;
  readonly countryCode: string;
  readonly countryName: string;
  readonly salaryAmount: string;
  readonly currencyCode: string;
  readonly version: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface EmployeePage {
  readonly items: readonly Employee[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface EmployeeListQuery {
  readonly page: number;
  readonly size: number;
  readonly q: string;
}

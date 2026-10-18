import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { CreateEmployeeInput, Employee, EmployeeListQuery, EmployeePage } from './employee';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);

  create(input: CreateEmployeeInput): Observable<Employee> {
    const { employeeNumber, fullName, departmentId, countryCode, salaryAmount, currencyCode } =
      input;
    return this.http.get<void>('/api/v1/auth/csrf').pipe(
      switchMap(() =>
        this.http.post<Employee>('/api/v1/employees', {
          employeeNumber,
          fullName,
          departmentId,
          countryCode,
          salaryAmount,
          currencyCode,
        }),
      ),
    );
  }

  list(query: EmployeeListQuery): Observable<EmployeePage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q) params = params.set('q', query.q);
    if (query.departmentId) params = params.set('departmentId', query.departmentId);
    if (query.countryCode) params = params.set('countryCode', query.countryCode);
    if (query.currencyCode) params = params.set('currencyCode', query.currencyCode);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<EmployeePage>('/api/v1/employees', { params });
  }
}

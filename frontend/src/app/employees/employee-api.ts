import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EmployeeListQuery, EmployeePage } from './employee';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);

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

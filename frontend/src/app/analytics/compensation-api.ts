import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CompensationQuery, CompensationReport } from './compensation';

@Injectable({ providedIn: 'root' })
export class CompensationApi {
  private readonly http = inject(HttpClient);

  get(query: CompensationQuery): Observable<CompensationReport> {
    let params = new HttpParams().set('groupBy', query.groupBy);
    if (query.q) params = params.set('q', query.q);
    if (query.departmentId) params = params.set('departmentId', query.departmentId);
    if (query.countryCode) params = params.set('countryCode', query.countryCode);
    if (query.currencyCode) params = params.set('currencyCode', query.currencyCode);
    return this.http.get<CompensationReport>('/api/v1/analytics/compensation', { params });
  }
}

import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ReferenceData } from './reference-data';

@Injectable({ providedIn: 'root' })
export class ReferenceDataApi {
  private readonly http = inject(HttpClient);

  get(): Observable<ReferenceData> {
    return this.http.get<ReferenceData>('/api/v1/reference-data');
  }
}

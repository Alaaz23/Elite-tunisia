import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ApiHealth {
  status: 'UP' | 'DOWN';
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  constructor(private readonly http: HttpClient) {}

  check(): Observable<ApiHealth> {
    return this.http.get<ApiHealth>('/api/health');
  }
}
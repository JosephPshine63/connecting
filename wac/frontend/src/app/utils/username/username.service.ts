import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserResponse } from '../../services/models/user-response';

@Injectable({ providedIn: 'root' })
export class UsernameService {

  constructor(private http: HttpClient) {}

  getMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/v1/users/me');
  }

  updateUsername(username: string): Observable<UserResponse> {
    return this.http.put<UserResponse>('/api/v1/users/username', { username });
  }

  checkUsername(value: string): Observable<{ available: boolean }> {
    const params = new HttpParams().set('value', value);
    return this.http.get<{ available: boolean }>('/api/v1/users/check-username', { params });
  }
}

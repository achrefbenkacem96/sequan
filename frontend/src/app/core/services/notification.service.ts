import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http'
import { Observable } from 'rxjs'
import { environment } from '../../../environments/environment'
import { NotificationItem } from '../models/api.models'

@Injectable({ providedIn: 'root' })
export class NotificationService {
	constructor(private readonly http: HttpClient) {}

	list(): Observable<NotificationItem[]> {
		return this.http.get<NotificationItem[]>(`${environment.apiBaseUrl}/notifications`)
	}

	markRead(id: string): Observable<void> {
		return this.http.post<void>(`${environment.apiBaseUrl}/notifications/${id}/read`, {})
	}
}

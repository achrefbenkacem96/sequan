import { Injectable } from '@angular/core'
import { HttpClient, HttpHeaders } from '@angular/common/http'
import { Observable } from 'rxjs'
import { environment } from '../../../environments/environment'
import {
	DashboardStats,
	Escrow,
	EscrowUpdateRequest,
	FundResponse,
	MyRelease,
	TimelineEvent,
} from '../models/api.models'

@Injectable({ providedIn: 'root' })
export class EscrowService {
	constructor(private readonly http: HttpClient) {}

	list(): Observable<Escrow[]> {
		return this.http.get<Escrow[]>(`${environment.apiBaseUrl}/escrows`)
	}

	get(id: string): Observable<Escrow> {
		return this.http.get<Escrow>(`${environment.apiBaseUrl}/escrows/${id}`)
	}

	dashboard(): Observable<DashboardStats> {
		return this.http.get<DashboardStats>(`${environment.apiBaseUrl}/dashboard`)
	}

	myReleases(): Observable<MyRelease[]> {
		return this.http.get<MyRelease[]>(`${environment.apiBaseUrl}/releases/mine`)
	}

	create(body: unknown): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows`, body)
	}

	/** Updates editable draft fields (title, description, deadlines). */
	update(id: string, body: EscrowUpdateRequest): Observable<Escrow> {
		return this.http.patch<Escrow>(`${environment.apiBaseUrl}/escrows/${id}`, body)
	}

	publish(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/publish`, {})
	}

	cancel(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/cancel`, {})
	}

	accept(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/accept`, {})
	}

	decline(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/decline`, {})
	}

	start(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/start`, {})
	}

	requestRelease(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/request-release`, {})
	}

	approveRelease(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/approve-release`, {})
	}

	rejectRelease(id: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/reject-release`, {})
	}

	satisfyCondition(escrowId: string, conditionId: string): Observable<Escrow> {
		return this.http.post<Escrow>(
			`${environment.apiBaseUrl}/escrows/${escrowId}/conditions/${conditionId}/satisfy`,
			{},
		)
	}

	fund(id: string, returnBaseUrl?: string): Observable<FundResponse> {
		const headers = new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() })
		const body = returnBaseUrl ? { returnBaseUrl } : {}
		return this.http.post<FundResponse>(`${environment.apiBaseUrl}/escrows/${id}/fund`, body, { headers })
	}

	confirmFunding(id: string, sessionId: string): Observable<Escrow> {
		return this.http.post<Escrow>(
			`${environment.apiBaseUrl}/escrows/${id}/confirm-funding`,
			{},
			{ params: { session_id: sessionId } },
		)
	}

	release(id: string, beneficiaryParticipantId: string, amount: string): Observable<unknown> {
		return this.http.post(`${environment.apiBaseUrl}/escrows/${id}/releases`, {
			beneficiaryParticipantId,
			amount,
			idempotencyKey: crypto.randomUUID(),
		})
	}

	refund(id: string, amount: string): Observable<unknown> {
		return this.http.post(`${environment.apiBaseUrl}/escrows/${id}/refunds`, {
			amount,
			idempotencyKey: crypto.randomUUID(),
		})
	}

	dispute(id: string, reason: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/dispute`, { reason })
	}

	resolveDispute(id: string, resolution: string, notes?: string): Observable<Escrow> {
		return this.http.post<Escrow>(`${environment.apiBaseUrl}/escrows/${id}/dispute/resolve`, {
			resolution,
			notes: notes || null,
		})
	}

	timeline(id: string): Observable<TimelineEvent[]> {
		return this.http.get<TimelineEvent[]>(`${environment.apiBaseUrl}/escrows/${id}/events`)
	}

	connectOnboarding(): Observable<{ url: string }> {
		return this.http.post<{ url: string }>(`${environment.apiBaseUrl}/connect/onboarding-link`, {})
	}

	connectStatus(): Observable<{ complete: boolean; accountId: string | null }> {
		return this.http.get<{ complete: boolean; accountId: string | null }>(
			`${environment.apiBaseUrl}/connect/status`,
		)
	}

	lookupUser(email: string): Observable<{ id: string; email: string; fullName: string }> {
		return this.http.get<{ id: string; email: string; fullName: string }>(
			`${environment.apiBaseUrl}/users/lookup`,
			{ params: { email } },
		)
	}

	searchUsers(q: string): Observable<{ id: string; email: string; fullName: string }[]> {
		return this.http.get<{ id: string; email: string; fullName: string }[]>(
			`${environment.apiBaseUrl}/users/search`,
			{ params: { q } },
		)
	}
}

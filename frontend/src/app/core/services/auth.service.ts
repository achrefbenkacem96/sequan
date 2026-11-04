import { Injectable, signal, computed } from '@angular/core'
import { HttpClient } from '@angular/common/http'
import { Router } from '@angular/router'
import { BehaviorSubject, Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs'
import { environment } from '../../../environments/environment'
import { AuthResponse, User } from '../models/api.models'

const AUTH_NOTICE_KEY = 'sequan_auth_notice'
const ACCESS_KEY = 'sequan_access'
const REFRESH_KEY = 'sequan_refresh'
const USER_KEY = 'sequan_user'

@Injectable({ providedIn: 'root' })
export class AuthService {
	private readonly userSignal = signal<User | null>(null)
	private accessToken: string | null = null
	private refreshRequest$: Observable<AuthResponse> | null = null
	private readonly readySubject = new BehaviorSubject<boolean>(false)

	readonly user = this.userSignal.asReadonly()
	readonly isAuthenticated = computed(() => !!this.userSignal())
	readonly authReady$ = this.readySubject.asObservable()

	constructor(
		private readonly http: HttpClient,
		private readonly router: Router,
	) {
		const refresh = localStorage.getItem(REFRESH_KEY)
		const cachedUser = localStorage.getItem(USER_KEY)
		// Prefer localStorage so a Stripe return_url reload keeps the access token
		// (sessionStorage alone is fragile across origin/host mismatches).
		const cachedAccess =
			localStorage.getItem(ACCESS_KEY) || sessionStorage.getItem(ACCESS_KEY)
		if (cachedUser) {
			this.userSignal.set(JSON.parse(cachedUser))
		}
		if (cachedAccess) {
			this.accessToken = cachedAccess
			localStorage.setItem(ACCESS_KEY, cachedAccess)
			sessionStorage.setItem(ACCESS_KEY, cachedAccess)
		}

		if (this.isAccessTokenValid(60)) {
			this.readySubject.next(true)
		} else if (refresh) {
			this.refresh(refresh).subscribe({
				next: () => this.readySubject.next(true),
				error: () => {
					if (!this.isAccessTokenValid(0)) {
						this.clearSession()
						this.setAuthNotice(
							'Your session expired or could not be restored. Please sign in again.',
						)
					}
					this.readySubject.next(true)
				},
			})
		} else {
			this.readySubject.next(true)
		}
	}

	register(email: string, password: string, fullName: string): Observable<AuthResponse> {
		return this.http
			.post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, { email, password, fullName })
			.pipe(tap((res) => this.persist(res)))
	}

	login(email: string, password: string): Observable<AuthResponse> {
		return this.http
			.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, { email, password })
			.pipe(tap((res) => this.persist(res)))
	}

	refresh(refreshToken: string): Observable<AuthResponse> {
		if (this.refreshRequest$) {
			return this.refreshRequest$
		}
		this.refreshRequest$ = this.http
			.post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, { refreshToken })
			.pipe(
				tap((res) => this.persist(res)),
				finalize(() => {
					this.refreshRequest$ = null
				}),
				shareReplay(1),
			)
		return this.refreshRequest$
	}

	isRefreshInFlight(): boolean {
		return this.refreshRequest$ !== null
	}

	/**
	 * Ensures a usable access token exists (refreshing only when needed).
	 * Never navigates — callers decide how to surface failures.
	 */
	ensureFreshSession(): Observable<boolean> {
		if (this.isAccessTokenValid(30)) {
			return of(true)
		}
		const refreshToken = this.getRefreshToken()
		if (!refreshToken) {
			return of(false)
		}
		return this.refresh(refreshToken).pipe(
			map(() => true),
			catchError(() => of(false)),
		)
	}

	whenRefreshed(): Observable<boolean> {
		if (!this.refreshRequest$) {
			return of(this.isAccessTokenValid(0))
		}
		return this.refreshRequest$.pipe(
			map(() => true),
			catchError(() => of(false)),
		)
	}

	logout(reason?: string): void {
		const refreshToken = localStorage.getItem(REFRESH_KEY)
		if (refreshToken) {
			this.http.post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken }).subscribe()
		}
		this.clearSession()
		if (reason) {
			this.setAuthNotice(reason)
		}
		this.router.navigate(['/login'], {
			queryParams: reason ? { reason: 'session' } : undefined,
		})
	}

	/**
	 * Clears credentials after a failed refresh without forcing navigation.
	 * Payment / escrow screens can show an inline error instead of bouncing to login.
	 */
	expireSession(reason?: string): void {
		this.clearSession()
		if (reason) {
			this.setAuthNotice(reason)
		}
	}

	consumeAuthNotice(): string | null {
		const notice = sessionStorage.getItem(AUTH_NOTICE_KEY)
		if (notice) {
			sessionStorage.removeItem(AUTH_NOTICE_KEY)
		}
		return notice
	}

	setAuthNotice(message: string): void {
		sessionStorage.setItem(AUTH_NOTICE_KEY, message)
	}

	getAccessToken(): string | null {
		return this.accessToken
	}

	getRefreshToken(): string | null {
		return localStorage.getItem(REFRESH_KEY)
	}

	isAccessTokenValid(minTtlSeconds: number): boolean {
		if (!this.accessToken) {
			return false
		}
		const exp = this.readJwtExp(this.accessToken)
		if (exp == null) {
			return !!this.accessToken
		}
		return exp * 1000 > Date.now() + minTtlSeconds * 1000
	}

	private persist(res: AuthResponse): void {
		this.accessToken = res.accessToken
		localStorage.setItem(ACCESS_KEY, res.accessToken)
		sessionStorage.setItem(ACCESS_KEY, res.accessToken)
		localStorage.setItem(REFRESH_KEY, res.refreshToken)
		localStorage.setItem(USER_KEY, JSON.stringify(res.user))
		this.userSignal.set(res.user)
		sessionStorage.removeItem(AUTH_NOTICE_KEY)
	}

	private clearSession(): void {
		this.accessToken = null
		sessionStorage.removeItem(ACCESS_KEY)
		localStorage.removeItem(ACCESS_KEY)
		localStorage.removeItem(REFRESH_KEY)
		localStorage.removeItem(USER_KEY)
		this.userSignal.set(null)
	}

	private readJwtExp(token: string): number | null {
		try {
			const payload = token.split('.')[1]
			if (!payload) {
				return null
			}
			const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
			return typeof json.exp === 'number' ? json.exp : null
		} catch {
			return null
		}
	}
}

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http'
import { inject } from '@angular/core'
import { catchError, switchMap, throwError } from 'rxjs'
import { AuthService } from '../services/auth.service'

const SESSION_EXPIRED_MSG =
	'Your session expired. Please sign in again to continue.'

export const authInterceptor: HttpInterceptorFn = (req, next) => {
	const auth = inject(AuthService)

	const withAuth = (request: typeof req) => {
		const token = auth.getAccessToken()
		return token
			? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
			: request
	}

	return next(withAuth(req)).pipe(
		catchError((error: HttpErrorResponse) => {
			if (error.status !== 401 || req.url.includes('/auth/')) {
				return throwError(() => error)
			}

			return auth.ensureFreshSession().pipe(
				switchMap((ok) => {
					if (!ok || !auth.getAccessToken()) {
						// Clear credentials but do NOT navigate — callers show inline errors
						// (especially important during Stripe funding return / confirm).
						auth.expireSession(SESSION_EXPIRED_MSG)
						return throwError(() => error)
					}
					return next(withAuth(req))
				}),
				catchError((refreshError) => {
					auth.expireSession(SESSION_EXPIRED_MSG)
					return throwError(() => refreshError)
				}),
			)
		}),
	)
}

import { CanActivateFn, Router } from '@angular/router'
import { inject } from '@angular/core'
import { filter, map, switchMap, take } from 'rxjs'
import { AuthService } from '../services/auth.service'

export const authGuard: CanActivateFn = () => {
	const auth = inject(AuthService)
	const router = inject(Router)
	return auth.authReady$.pipe(
		filter((ready) => ready === true),
		take(1),
		switchMap(() => auth.ensureFreshSession()),
		map(() => {
			// Allow through if any credential remains. Failed refresh during a Stripe
			// return must not bounce to login — the page shows an inline error instead.
			if (auth.isAuthenticated() || auth.getAccessToken() || auth.getRefreshToken()) {
				return true
			}
			auth.setAuthNotice('Please sign in to access Sequan.')
			return router.createUrlTree(['/login'], { queryParams: { reason: 'required' } })
		}),
	)
}

export const guestGuard: CanActivateFn = () => {
	const auth = inject(AuthService)
	const router = inject(Router)
	return auth.authReady$.pipe(
		filter((ready) => ready === true),
		take(1),
		map(() => {
			if (auth.isAuthenticated() && auth.isAccessTokenValid(0)) {
				return router.createUrlTree(['/dashboard'])
			}
			return true
		}),
	)
}

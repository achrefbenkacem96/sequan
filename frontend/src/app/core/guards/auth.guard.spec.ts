import { TestBed } from '@angular/core/testing'
import { Router, UrlTree } from '@angular/router'
import { authGuard } from './auth.guard'
import { AuthService } from '../services/auth.service'

describe('authGuard', () => {
	it('allows authenticated users', () => {
		TestBed.configureTestingModule({
			providers: [
				{
					provide: AuthService,
					useValue: {
						isAuthenticated: () => true,
						getRefreshToken: () => null,
					},
				},
				{ provide: Router, useValue: { createUrlTree: () => ({}) as UrlTree } },
			],
		})
		const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))
		expect(result).toBeTrue()
	})
})

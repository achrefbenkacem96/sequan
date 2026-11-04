import { Component, OnInit, inject } from '@angular/core'
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms'
import { ActivatedRoute, Router, RouterLink } from '@angular/router'
import { MatFormFieldModule } from '@angular/material/form-field'
import { MatInputModule } from '@angular/material/input'
import { MatButtonModule } from '@angular/material/button'
import { AuthService } from '../../core/services/auth.service'
import { ApiError } from '../../core/models/api.models'

@Component({
	selector: 'app-login',
	standalone: true,
	imports: [
		ReactiveFormsModule,
		RouterLink,
		MatFormFieldModule,
		MatInputModule,
		MatButtonModule,
	],
	template: `
		<section class="auth-page">
			<div class="frame">
				<aside class="story">
					<p class="brand">Sequan</p>
					<h1>Escrow built for clear money movement.</h1>
					<p>
						Fund with confidence, track every status change, and release only when
						conditions are met.
					</p>
				</aside>
				<div class="panel">
					<p class="eyebrow">Welcome back</p>
					<h2>Sign in</h2>
					<p class="sub">Access your escrow workspace.</p>
					@if (notice) {
						<div class="sq-notice" role="alert">{{ notice }}</div>
					}
					<form [formGroup]="form" (ngSubmit)="submit()">
						<mat-form-field appearance="outline" class="full">
							<mat-label>Email</mat-label>
							<input matInput type="email" formControlName="email" autocomplete="username" />
						</mat-form-field>
						<mat-form-field appearance="outline" class="full">
							<mat-label>Password</mat-label>
							<input matInput type="password" formControlName="password" autocomplete="current-password" />
						</mat-form-field>
						@if (error) {
							<p class="sq-error">{{ error }}</p>
						}
						<button mat-flat-button color="primary" class="full" [disabled]="form.invalid || loading">
							{{ loading ? 'Signing in…' : 'Sign in' }}
						</button>
					</form>
					<p class="footer">No account? <a routerLink="/register">Create one</a></p>
				</div>
			</div>
		</section>
	`,
	styles: [`
		.auth-page {
			min-height: 100vh;
			display: grid;
			place-items: center;
			padding: 1.5rem;
			background: var(--sq-canvas);
		}
		.frame {
			width: min(920px, 100%);
			display: grid;
			grid-template-columns: 1.05fr 0.95fr;
			background: var(--sq-surface);
			border: 1px solid var(--sq-border);
			border-radius: 16px;
			overflow: hidden;
			box-shadow: var(--sq-shadow);
		}
		.story {
			padding: 2.25rem;
			background: var(--sq-ink);
			color: #e2e8f0;
			display: grid;
			align-content: center;
			gap: 0.85rem;
		}
		.brand {
			margin: 0;
			font-family: var(--sq-display);
			font-size: 1.7rem;
			color: #5eead4;
		}
		.story h1 {
			margin: 0;
			font-weight: 700;
			font-size: clamp(1.6rem, 2.6vw, 2.1rem);
			line-height: 1.15;
			letter-spacing: -0.03em;
			color: #f8fafc;
		}
		.story p:last-child {
			margin: 0;
			color: #94a3b8;
			max-width: 28rem;
		}
		.panel { padding: 2rem; }
		.eyebrow {
			margin: 0;
			color: var(--sq-accent);
			font-size: 0.72rem;
			font-weight: 700;
			letter-spacing: 0.06em;
			text-transform: uppercase;
		}
		h2 {
			margin: 0.35rem 0 0.25rem;
			font-size: 1.55rem;
			font-weight: 700;
			letter-spacing: -0.03em;
		}
		.sub, .footer { color: var(--sq-muted); margin: 0; }
		.footer { margin-top: 1rem; font-size: 0.9rem; }
		.full { width: 100%; }
		form { display: grid; gap: 0.45rem; margin-top: 1.15rem; }
		@media (max-width: 800px) {
			.frame { grid-template-columns: 1fr; }
			.story { padding: 1.5rem; }
		}
	`],
})
export class LoginComponent implements OnInit {
	private readonly fb = inject(FormBuilder)
	private readonly auth = inject(AuthService)
	private readonly router = inject(Router)
	private readonly route = inject(ActivatedRoute)

	loading = false
	error = ''
	notice = ''
	form = this.fb.nonNullable.group({
		email: ['', [Validators.required, Validators.email]],
		password: ['', Validators.required],
	})

	ngOnInit(): void {
		const stored = this.auth.consumeAuthNotice()
		const reason = this.route.snapshot.queryParamMap.get('reason')
		if (stored) {
			this.notice = stored
		} else if (reason === 'session') {
			this.notice = 'Your session expired. Please sign in again to continue.'
		} else if (reason === 'required') {
			this.notice = 'Please sign in to access Sequan.'
		}
	}

	submit(): void {
		if (this.form.invalid) return
		this.loading = true
		this.error = ''
		const { email, password } = this.form.getRawValue()
		this.auth.login(email, password).subscribe({
			next: () => this.router.navigate(['/dashboard']),
			error: (err) => {
				this.loading = false
				const api = err.error as ApiError
				this.error = api?.message || 'Unable to sign in'
			},
		})
	}
}

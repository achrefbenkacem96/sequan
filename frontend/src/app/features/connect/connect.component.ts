import { Component, OnInit, computed, inject, signal } from '@angular/core'
import { DatePipe } from '@angular/common'
import { RouterLink } from '@angular/router'
import { MatButtonModule } from '@angular/material/button'
import { MatIconModule } from '@angular/material/icon'
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner'
import { AuthService } from '../../core/services/auth.service'
import { EscrowService } from '../../core/services/escrow.service'
import { MyRelease } from '../../core/models/api.models'
import { MoneyPipe } from '../../shared/money.pipe'
import { PageHeaderComponent } from '../../shared/page-header/page-header.component'
import { StatusChipComponent } from '../../shared/status-chip/status-chip.component'

type ConnectState = 'loading' | 'ready' | 'pending' | 'error'

@Component({
	selector: 'app-connect',
	standalone: true,
	imports: [
		RouterLink,
		MatButtonModule,
		MatIconModule,
		MatProgressSpinnerModule,
		PageHeaderComponent,
		MoneyPipe,
		DatePipe,
		StatusChipComponent,
	],
	template: `
		<section class="sq-page wrap">
			<app-page-header eyebrow="Account" title="Payouts" [subtitle]="subtitle()">
				<button
					sqHeadActions
					mat-stroked-button
					type="button"
					(click)="refresh()"
					[disabled]="busy()"
				>
					<mat-icon>refresh</mat-icon>
					Refresh
				</button>
			</app-page-header>

			<div class="sq-panel status-card">
				@if (state() === 'loading') {
					<div class="center"><mat-spinner diameter="32" /></div>
				} @else {
					<div class="status-row">
						<span [class]="'indicator ' + state()" aria-hidden="true">
							<mat-icon>{{ icon() }}</mat-icon>
						</span>
						<div class="status-text">
							<span class="label">Stripe Connect</span>
							<strong
								[class.ready]="state() === 'ready'"
								[class.pending]="state() === 'pending'"
							>
								{{ headline() }}
							</strong>
							@if (accountId(); as id) {
								<small>Account {{ id }}</small>
							}
							@if (user(); as u) {
								<small>Payouts for {{ u.fullName }} · {{ u.email }}</small>
							}
						</div>
					</div>

					@if (state() !== 'error') {
						<div class="actions">
							<button
								mat-flat-button
								color="primary"
								type="button"
								(click)="start()"
								[disabled]="busy()"
							>
								{{ state() === 'ready' ? 'Update payout details' : 'Start onboarding' }}
							</button>
						</div>
					}
				}

				@if (error()) {
					<p class="sq-error" role="alert">{{ error() }}</p>
				}
			</div>

			<section class="sq-panel">
				<div class="sq-panel-head">
					<div>
						<h2>How payouts work</h2>
						<p>Released escrow funds move to your connected Stripe account.</p>
					</div>
				</div>
				<ol class="how">
					<li>
						<strong>Connect Stripe</strong>
						<span>Complete onboarding so Sequan can transfer funds to you.</span>
					</li>
					<li>
						<strong>Finish the escrow</strong>
						<span>Conditions are met, release is approved, then funds are released.</span>
					</li>
					<li>
						<strong>Receive the transfer</strong>
						<span>Stripe pays out to your bank on its normal schedule.</span>
					</li>
				</ol>
			</section>

			<section class="sq-panel">
				<div class="sq-panel-head">
					<div>
						<h2>Recent releases to you</h2>
						<p>{{ releasesSummary() }}</p>
					</div>
				</div>

				@if (releasesLoading()) {
					<div class="center"><mat-spinner diameter="28" /></div>
				} @else if (releases().length === 0) {
					<div class="sq-empty sq-empty--compact">
						<strong>No releases yet</strong>
						<p>
							When an escrow pays you as provider or beneficiary, the transfer
							will show up here.
						</p>
						<a mat-stroked-button routerLink="/escrows">View escrows</a>
					</div>
				} @else {
					<ul class="release-list">
						@for (row of releases(); track row.id) {
							<li>
								<div class="release-main">
									<a [routerLink]="['/escrows', row.escrowId]">
										{{ row.escrowTitle }}
									</a>
									<small>
										{{ humanize(row.role) }}
										· {{ row.createdAt | date:'mediumDate' }}
									</small>
								</div>
								<div class="release-meta">
									<strong>{{ row.amount | money:row.currency }}</strong>
									<app-status-chip [status]="row.status" />
								</div>
							</li>
						}
					</ul>
				}
			</section>
		</section>
	`,
	styles: [`
		.wrap { max-width: 760px; }
		.status-card { display: grid; gap: 0.9rem; }
		.status-row {
			display: flex;
			gap: 0.85rem;
			align-items: flex-start;
		}
		.status-text { display: grid; gap: 0.15rem; min-width: 0; }
		.label {
			color: var(--sq-muted);
			font-size: 0.78rem;
			font-weight: 600;
			letter-spacing: 0.04em;
			text-transform: uppercase;
		}
		strong {
			font-size: 1.15rem;
			letter-spacing: -0.01em;
		}
		strong.ready { color: var(--sq-success); }
		strong.pending { color: var(--sq-warning); }
		small {
			color: var(--sq-muted);
			font-size: 0.8rem;
			overflow-wrap: anywhere;
		}
		.indicator {
			width: 40px;
			height: 40px;
			border-radius: 10px;
			display: grid;
			place-items: center;
			flex-shrink: 0;
			background: var(--sq-surface-2);
			color: var(--sq-ink-soft);
			border: 1px solid var(--sq-border);
		}
		.indicator mat-icon { font-size: 22px; width: 22px; height: 22px; }
		.indicator.ready {
			background: var(--sq-success-soft);
			color: var(--sq-success);
			border-color: #bbf7d0;
		}
		.indicator.pending {
			background: var(--sq-warning-soft);
			color: var(--sq-warning);
			border-color: #fed7aa;
		}
		.indicator.error {
			background: var(--sq-danger-soft);
			color: var(--sq-danger);
			border-color: #fecaca;
		}
		.actions { display: flex; gap: 0.65rem; flex-wrap: wrap; }
		.how {
			margin: 0;
			padding: 0;
			list-style: none;
			display: grid;
			gap: 0.7rem;
			counter-reset: how;
		}
		.how li {
			display: grid;
			grid-template-columns: auto 1fr;
			gap: 0.35rem 0.85rem;
			align-items: start;
			counter-increment: how;
		}
		.how li::before {
			content: counter(how);
			width: 1.6rem;
			height: 1.6rem;
			border-radius: 999px;
			display: grid;
			place-items: center;
			font-size: 0.78rem;
			font-weight: 700;
			color: var(--sq-accent);
			background: color-mix(in srgb, var(--sq-accent) 12%, transparent);
			border: 1px solid color-mix(in srgb, var(--sq-accent) 28%, transparent);
			grid-row: span 2;
		}
		.how strong {
			font-size: 0.95rem;
			letter-spacing: 0;
		}
		.how span {
			color: var(--sq-muted);
			font-size: 0.88rem;
			line-height: 1.45;
		}
		.release-list {
			list-style: none;
			margin: 0;
			padding: 0;
		}
		.release-list li {
			display: flex;
			justify-content: space-between;
			gap: 1rem;
			align-items: center;
			padding: 0.7rem 0;
			border-top: 1px solid var(--sq-border);
		}
		.release-list li:first-child {
			border-top: 0;
			padding-top: 0.1rem;
		}
		.release-main {
			min-width: 0;
			display: grid;
			gap: 0.2rem;
		}
		.release-main a {
			font-weight: 600;
			color: var(--sq-ink);
			text-decoration: none;
			overflow-wrap: anywhere;
		}
		.release-main a:hover { color: var(--sq-accent); }
		.release-meta {
			display: grid;
			justify-items: end;
			gap: 0.35rem;
			flex-shrink: 0;
		}
		.release-meta strong {
			font-size: 0.95rem;
			font-variant-numeric: tabular-nums;
		}
		.center { display: grid; place-items: center; padding: 1.15rem 0; }
		@media (max-width: 560px) {
			.release-list li {
				flex-direction: column;
				align-items: stretch;
			}
			.release-meta { justify-items: start; }
		}
	`],
})
export class ConnectComponent implements OnInit {
	private readonly escrows = inject(EscrowService)
	private readonly auth = inject(AuthService)

	readonly user = this.auth.user
	readonly state = signal<ConnectState>('loading')
	readonly accountId = signal<string | null>(null)
	readonly busy = signal(false)
	readonly error = signal('')
	readonly releases = signal<MyRelease[]>([])
	readonly releasesLoading = signal(true)

	readonly subtitle = computed(() => {
		switch (this.state()) {
			case 'ready':
				return 'Your Stripe account can receive released escrow funds.'
			case 'pending':
				return 'Finish Stripe onboarding before funds can be released to you.'
			case 'error':
				return 'Payout status could not be loaded.'
			default:
				return 'Checking your Stripe Connect status…'
		}
	})

	readonly headline = computed(() => {
		switch (this.state()) {
			case 'ready':
				return 'Ready to receive payouts'
			case 'pending':
				return 'Onboarding incomplete'
			case 'error':
				return 'Status unavailable'
			default:
				return ''
		}
	})

	readonly icon = computed(() => {
		switch (this.state()) {
			case 'ready':
				return 'verified'
			case 'pending':
				return 'pending_actions'
			case 'error':
				return 'error_outline'
			default:
				return 'hourglass_empty'
		}
	})

	releasesSummary(): string {
		const n = this.releases().length
		if (this.releasesLoading()) return 'Loading recent transfers…'
		if (n === 0) return 'Transfers paid to you as provider or beneficiary.'
		return `${n} recent ${n === 1 ? 'transfer' : 'transfers'}.`
	}

	humanize(value: string): string {
		if (!value) return 'Payee'
		const text = value.replaceAll('_', ' ').toLowerCase()
		return text.charAt(0).toUpperCase() + text.slice(1)
	}

	ngOnInit(): void {
		this.refresh()
		this.loadReleases()
	}

	refresh(): void {
		this.busy.set(true)
		this.error.set('')
		this.escrows.connectStatus().subscribe({
			next: (status) => {
				this.state.set(status.complete ? 'ready' : 'pending')
				this.accountId.set(status.accountId)
				this.busy.set(false)
			},
			error: () => {
				this.state.set('error')
				this.busy.set(false)
				this.error.set('Unable to load Connect status.')
			},
		})
		this.loadReleases()
	}

	loadReleases(): void {
		this.releasesLoading.set(true)
		this.escrows.myReleases().subscribe({
			next: (rows) => {
				this.releases.set(rows)
				this.releasesLoading.set(false)
			},
			error: () => {
				this.releases.set([])
				this.releasesLoading.set(false)
			},
		})
	}

	start(): void {
		this.busy.set(true)
		this.error.set('')
		this.escrows.connectOnboarding().subscribe({
			next: (res) => {
				window.location.href = res.url
			},
			error: () => {
				this.busy.set(false)
				this.error.set('Unable to start Connect onboarding.')
			},
		})
	}
}

import { Component, OnInit, computed, inject, signal } from '@angular/core'
import { RouterLink } from '@angular/router'
import { MatButtonModule } from '@angular/material/button'
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner'
import { AuthService } from '../../core/services/auth.service'
import { EscrowService } from '../../core/services/escrow.service'
import { DashboardStats } from '../../core/models/api.models'
import { StatusChipComponent } from '../../shared/status-chip/status-chip.component'
import { MoneyPipe } from '../../shared/money.pipe'
import { PageHeaderComponent } from '../../shared/page-header/page-header.component'

interface StatusCount {
	status: string
	count: number
}

@Component({
	selector: 'app-dashboard',
	standalone: true,
	imports: [
		RouterLink,
		MatButtonModule,
		MatProgressSpinnerModule,
		StatusChipComponent,
		MoneyPipe,
		PageHeaderComponent,
	],
	template: `
		<section class="sq-page">
			<app-page-header
				[eyebrow]="workspaceLabel()"
				[title]="greeting()"
				[subtitle]="summary()"
			>
				<a sqHeadActions mat-flat-button color="primary" routerLink="/escrows/new">
					Create escrow
				</a>
			</app-page-header>

			@if (loading()) {
				<div class="center"><mat-spinner diameter="36" /></div>
			} @else if (hasError()) {
				<div class="sq-empty">
					<h2>Dashboard unavailable</h2>
					<p>We could not load your escrow summary.</p>
					<button mat-stroked-button type="button" (click)="load()">Try again</button>
				</div>
			} @else {
				@if (stats(); as s) {
				@if (s.totalEscrows === 0) {
					<div class="sq-empty empty-workspace">
						<strong>Start your first escrow</strong>
						<p>
							Hold funds safely until work is done. Invite a provider,
							set release conditions, then fund when everyone is ready.
						</p>
						<ol class="start-steps">
							<li>Create a draft with amount and conditions</li>
							<li>Invite provider (and approver if you need dual control)</li>
							<li>Publish, fund, and release when conditions are met</li>
						</ol>
						<a mat-flat-button color="primary" routerLink="/escrows/new">
							Create escrow
						</a>
					</div>
				} @else {
					<div class="stats">
						<article class="sq-metric">
							<span>Total escrows</span>
							<strong>{{ s.totalEscrows }}</strong>
							<small>{{ openCount() }} open</small>
						</article>
						<article class="sq-metric">
							<span>Available balance</span>
							<strong>{{ (s.totalAvailable / 100) | money:'USD' }}</strong>
							<small>
								{{ holdingCount() }}
								{{ holdingCount() === 1 ? 'escrow holds' : 'escrows hold' }}
								funds
							</small>
						</article>
						<article class="sq-metric">
							<span>Awaiting funding</span>
							<strong>{{ s.countsByStatus['PENDING_FUNDING'] || 0 }}</strong>
							<small>
								{{ s.countsByStatus['PENDING_PARTICIPANTS'] || 0 }}
								awaiting participants
							</small>
						</article>
						<article class="sq-metric">
							<span>Funded or in progress</span>
							<strong>{{ fundedCount() }}</strong>
							<small>
								{{ s.countsByStatus['AWAITING_RELEASE'] || 0 }}
								awaiting release
							</small>
						</article>
					</div>

					<div class="columns">
						<section class="sq-panel attention">
							<div class="sq-panel-head">
								<div>
									<h2>Needs attention</h2>
									<p>{{ attentionSummary() }}</p>
								</div>
								<a routerLink="/escrows">View all escrows</a>
							</div>

							@if (s.actionRequired.length === 0) {
								<div class="sq-empty sq-empty--compact caught-up">
									<span class="tick" aria-hidden="true"></span>
									<div>
										<strong>You're caught up</strong>
										<p>
											Nothing is waiting on you. Open escrows from the list
											if you want to review progress.
										</p>
									</div>
								</div>
							} @else {
								<ul class="attention-list">
									@for (item of s.actionRequired; track item.id) {
										<li>
											<div class="attn-main">
												<a
													class="attn-title"
													[routerLink]="['/escrows', item.id]"
												>
													{{ item.title }}
												</a>
												<app-status-chip [status]="item.status" />
											</div>
											<a
												mat-flat-button
												color="primary"
												[routerLink]="['/escrows', item.id]"
											>
												{{ actionLabel(item.primaryAction) }}
											</a>
										</li>
									}
								</ul>
							}
						</section>

						<section class="sq-panel">
							<div class="sq-panel-head">
								<div>
									<h2>By status</h2>
									@if (statusBreakdown().length > 0) {
										<p>
											{{ statusBreakdown().length }}
											{{ statusBreakdown().length === 1 ? 'state' : 'states' }}
											in use
										</p>
									}
								</div>
							</div>
							@if (statusBreakdown().length === 0) {
								<div class="sq-empty sq-empty--compact">
									<strong>No status data</strong>
									<p>Create an escrow to see a breakdown here.</p>
								</div>
							} @else {
								<ul class="breakdown">
									@for (row of statusBreakdown(); track row.status) {
										<li>
											<app-status-chip [status]="row.status" />
											<span class="bar" aria-hidden="true">
												<span
													class="fill"
													[style.width.%]="barWidth(row.count)"
												></span>
											</span>
											<strong>{{ row.count }}</strong>
										</li>
									}
								</ul>
							}
						</section>
					</div>
				}
				}
			}
		</section>
	`,
	styles: [`
		.stats {
			display: grid;
			grid-template-columns: repeat(4, minmax(0, 1fr));
			gap: 0.75rem;
		}
		.columns {
			display: grid;
			grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
			gap: 0.85rem;
			align-items: start;
		}
		.empty-workspace {
			justify-items: start;
			text-align: left;
			padding: 1.35rem 1.25rem;
		}
		.empty-workspace strong { font-size: 1.1rem; }
		.empty-workspace p { max-width: 40rem; }
		.start-steps {
			margin: 0.65rem 0 0.15rem;
			padding-left: 1.15rem;
			color: var(--sq-ink-soft);
			display: grid;
			gap: 0.3rem;
			font-size: 0.9rem;
			justify-self: stretch;
			text-align: left;
		}
		.attention-list {
			list-style: none;
			margin: 0;
			padding: 0;
			display: grid;
			gap: 0;
		}
		.attention-list li {
			display: flex;
			align-items: center;
			justify-content: space-between;
			gap: 0.75rem;
			padding: 0.65rem 0;
			border-top: 1px solid var(--sq-border);
		}
		.attention-list li:first-child {
			border-top: 0;
			padding-top: 0.1rem;
		}
		.attn-main {
			min-width: 0;
			display: grid;
			gap: 0.35rem;
			justify-items: start;
		}
		.attn-title {
			font-weight: 600;
			color: var(--sq-ink);
			text-decoration: none;
			overflow-wrap: anywhere;
		}
		.attn-title:hover { color: var(--sq-accent); }
		.caught-up {
			display: flex;
			justify-items: unset;
			text-align: left;
			gap: 0.75rem;
			align-items: flex-start;
			width: 100%;
		}
		.caught-up .tick {
			width: 26px;
			height: 26px;
			border-radius: 50%;
			background: var(--sq-success-soft);
			border: 1px solid #bbf7d0;
			position: relative;
			flex-shrink: 0;
		}
		.caught-up .tick::after {
			content: "";
			position: absolute;
			left: 8px;
			top: 4px;
			width: 6px;
			height: 11px;
			border: solid var(--sq-success);
			border-width: 0 2px 2px 0;
			transform: rotate(45deg);
		}
		.caught-up > div {
			display: grid;
			gap: 0.15rem;
			justify-items: start;
		}
		.breakdown {
			list-style: none;
			margin: 0;
			padding: 0;
			display: grid;
			gap: 0.55rem;
		}
		.breakdown li {
			display: grid;
			grid-template-columns: auto minmax(3rem, 1fr) auto;
			align-items: center;
			gap: 0.75rem;
		}
		.bar {
			display: block;
			height: 6px;
			border-radius: 999px;
			background: var(--sq-surface-2);
			border: 1px solid var(--sq-border);
			overflow: hidden;
		}
		.fill {
			display: block;
			height: 100%;
			background: var(--sq-accent);
			border-radius: inherit;
		}
		.breakdown strong {
			font-variant-numeric: tabular-nums;
			min-width: 1.5rem;
			text-align: right;
		}
		.center { display: grid; place-items: center; padding: 2rem 0; }
		@media (max-width: 1000px) {
			.columns { grid-template-columns: 1fr; }
		}
		@media (max-width: 900px) {
			.stats { grid-template-columns: 1fr 1fr; }
		}
		@media (max-width: 640px) {
			.attention-list li {
				flex-direction: column;
				align-items: stretch;
			}
		}
		@media (max-width: 520px) {
			.stats { grid-template-columns: 1fr; }
		}
	`],
})
export class DashboardComponent implements OnInit {
	private readonly escrows = inject(EscrowService)
	private readonly auth = inject(AuthService)

	readonly stats = signal<DashboardStats | null>(null)
	readonly loading = signal(true)
	readonly hasError = signal(false)

	readonly fundedCount = computed(() => {
		const s = this.stats()
		if (!s) return 0
		return (s.countsByStatus['FUNDED'] || 0) + (s.countsByStatus['IN_PROGRESS'] || 0)
	})

	readonly holdingCount = computed(() => {
		const s = this.stats()
		if (!s) return 0
		const holding = [
			'FUNDED',
			'IN_PROGRESS',
			'AWAITING_RELEASE',
			'PARTIALLY_RELEASED',
			'DISPUTED',
		]
		return holding.reduce((sum, status) => sum + (s.countsByStatus[status] || 0), 0)
	})

	readonly openCount = computed(() => {
		const s = this.stats()
		if (!s) return 0
		const closed = ['RELEASED', 'REFUNDED', 'CANCELLED', 'EXPIRED']
		return Object.entries(s.countsByStatus)
			.filter(([status]) => !closed.includes(status))
			.reduce((sum, [, count]) => sum + count, 0)
	})

	readonly statusBreakdown = computed<StatusCount[]>(() => {
		const s = this.stats()
		if (!s) return []
		return Object.entries(s.countsByStatus)
			.filter(([, count]) => count > 0)
			.map(([status, count]) => ({ status, count }))
			.sort((a, b) => b.count - a.count)
	})

	readonly summary = computed(() => {
		const s = this.stats()
		if (!s) return ''
		if (s.totalEscrows === 0) {
			return 'You have no escrows yet. Create one to start holding funds.'
		}
		const escrows = `${s.totalEscrows} ${s.totalEscrows === 1 ? 'escrow' : 'escrows'}`
		const pending = s.actionRequired.length
		const action =
			pending === 0
				? 'nothing is waiting on you'
				: `${pending} ${pending === 1 ? 'needs' : 'need'} your action`
		return `${escrows} in your workspace · ${action}.`
	})

	readonly attentionSummary = computed(() => {
		const count = this.stats()?.actionRequired.length || 0
		if (count === 0) return 'No pending actions.'
		return `${count} ${count === 1 ? 'escrow needs' : 'escrows need'} a next step from you.`
	})

	workspaceLabel(): string {
		return this.auth.user()?.globalRole === 'ADMIN'
			? 'Administrator'
			: 'Your workspace'
	}

	greeting(): string {
		const hour = new Date().getHours()
		const period = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
		const first = (this.auth.user()?.fullName || '').trim().split(/\s+/)[0]
		return first ? `Good ${period}, ${first}` : `Good ${period}`
	}

	actionLabel(action: string): string {
		const labels: Record<string, string> = {
			APPROVE_RELEASE: 'Approve release',
			RELEASE: 'Release funds',
			REQUEST_RELEASE: 'Request release',
			START: 'Start work',
			FUND: 'Fund escrow',
			SATISFY_CONDITION: 'Mark conditions',
			ACCEPT: 'Accept invite',
			PUBLISH: 'Publish draft',
			REJECT_RELEASE: 'Review release',
			RESOLVE_DISPUTE_RELEASE: 'Resolve dispute',
			RESOLVE_DISPUTE_REFUND: 'Resolve dispute',
			RESOLVE_DISPUTE_CANCEL: 'Resolve dispute',
		}
		return labels[action] || this.humanize(action)
	}

	humanize(value: string): string {
		const text = value.replaceAll('_', ' ').toLowerCase()
		return text.charAt(0).toUpperCase() + text.slice(1)
	}

	barWidth(count: number): number {
		const max = this.statusBreakdown()[0]?.count || 1
		return Math.max(6, Math.round((count / max) * 100))
	}

	ngOnInit(): void {
		this.load()
	}

	load(): void {
		this.loading.set(true)
		this.hasError.set(false)
		this.escrows.dashboard().subscribe({
			next: (stats) => {
				this.stats.set(stats)
				this.loading.set(false)
			},
			error: () => {
				this.hasError.set(true)
				this.loading.set(false)
			},
		})
	}
}

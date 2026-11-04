import { Component, OnInit, inject } from '@angular/core'
import { DatePipe } from '@angular/common'
import { ActivatedRoute, Router, RouterLink } from '@angular/router'
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms'
import { MatButtonModule } from '@angular/material/button'
import { MatFormFieldModule } from '@angular/material/form-field'
import { MatInputModule } from '@angular/material/input'
import { MatSelectModule } from '@angular/material/select'
import { MatDialog, MatDialogModule } from '@angular/material/dialog'
import { MatIconModule } from '@angular/material/icon'
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner'
import { filter, switchMap, take } from 'rxjs'
import { AuthService } from '../../core/services/auth.service'
import { EscrowService } from '../../core/services/escrow.service'
import { Escrow, TimelineEvent } from '../../core/models/api.models'
import { StatusChipComponent } from '../../shared/status-chip/status-chip.component'
import { MoneyPipe } from '../../shared/money.pipe'
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component'
import { ApiError } from '../../core/models/api.models'

@Component({
	selector: 'app-escrow-detail',
	standalone: true,
	imports: [
		RouterLink,
		ReactiveFormsModule,
		MatButtonModule,
		MatFormFieldModule,
		MatInputModule,
		MatSelectModule,
		MatDialogModule,
		MatIconModule,
		MatProgressSpinnerModule,
		StatusChipComponent,
		MoneyPipe,
		DatePipe,
	],
	template: `
		@if (loading) {
			<div class="center"><mat-spinner diameter="40" /></div>
		} @else if (escrow) {
			<section class="sq-page detail">
				<a routerLink="/escrows" class="sq-back">
					<mat-icon>arrow_back</mat-icon>
					Back to escrows
				</a>

				<section class="hero">
					<div class="intro">
						<p class="eyebrow">
							Escrow · created {{ escrow.createdAt | date:'mediumDate' }}
							· {{ escrow.participants.length }} {{ escrow.participants.length === 1 ? 'participant' : 'participants' }}
						</p>
						<div class="title-row">
							<h1>{{ escrow.title }}</h1>
							<app-status-chip [status]="escrow.status" />
						</div>
						@if (escrow.description) {
							<p class="description">{{ escrow.description }}</p>
						}
						<p class="ref">Reference {{ escrow.id }}</p>
					</div>
					<div class="money-panel">
						<span>Available to move</span>
						<strong class="sq-amount">{{ escrow.amountAvailable | money:escrow.currency }}</strong>
						<div class="money-grid">
							<div>
								<span>Funded</span>
								<strong>{{ escrow.amountFunded | money:escrow.currency }}</strong>
							</div>
							<div>
								<span>Released</span>
								<strong>{{ escrow.amountReleased | money:escrow.currency }}</strong>
							</div>
							<div>
								<span>Refunded</span>
								<strong>{{ escrow.amountRefunded | money:escrow.currency }}</strong>
							</div>
						</div>
					</div>
				</section>

				<section class="sq-panel stages" aria-label="Transaction progress">
					@for (stage of progressStages; track stage.key; let i = $index) {
						<div class="stage" [class.done]="stageState(stage.key) === 'done'" [class.current]="stageState(stage.key) === 'current'">
							<span class="index">{{ i + 1 }}</span>
							<span class="label">{{ stage.label }}</span>
						</div>
					}
				</section>

				@if (error) {
					<p class="sq-error">{{ error }}</p>
				}

				@if (escrow.status === 'CANCELLED') {
					<p class="cancel-banner" role="status">
						This escrow is cancelled.
						@if (declinedNames) {
							Declined by {{ declinedNames }} — see Participants below.
						}
					</p>
				}

				@if (visibleActions.length > 0) {
					<section class="actions" aria-label="Available actions">
						@for (action of visibleActions; track action) {
							@if (isPrimaryAction(action)) {
								<button
									mat-flat-button
									[color]="actionColor(action)"
									type="button"
									(click)="runAction(action)"
									[disabled]="busy"
								>
									{{ actionLabel(action) }}
								</button>
							} @else {
								<button
									mat-stroked-button
									[color]="actionColor(action)"
									type="button"
									(click)="runAction(action)"
									[disabled]="busy"
								>
									{{ actionLabel(action) }}
								</button>
							}
						}
					</section>
				}

				@if (showEditForm) {
					<section class="sq-panel">
						<h2>Edit details</h2>
						<form class="edit" [formGroup]="editForm" (ngSubmit)="saveEdit()">
							<mat-form-field appearance="outline" class="full">
								<mat-label>Title</mat-label>
								<input matInput formControlName="title" maxlength="200" />
							</mat-form-field>
							<mat-form-field appearance="outline" class="full">
								<mat-label>Description</mat-label>
								<textarea matInput rows="3" formControlName="description" maxlength="5000"></textarea>
							</mat-form-field>
							<div class="form-actions">
								<button mat-button type="button" (click)="showEditForm = false" [disabled]="busy">
									Cancel
								</button>
								<button mat-flat-button color="primary" [disabled]="editForm.invalid || busy">
									Save changes
								</button>
							</div>
						</form>
					</section>
				}

				@if (showCheckout) {
					<section class="sq-panel checkout-panel">
						<h2>Complete funding</h2>
						<div class="checkout-loading">
							<mat-spinner diameter="36"></mat-spinner>
							<p>Redirecting to Stripe Checkout…</p>
						</div>
					</section>
				}

				<div class="columns">
					<section class="sq-panel">
						<h2>Participants <span class="sq-count">{{ escrow.participants.length }}</span></h2>
						<ul class="entity-list">
							@for (p of escrow.participants; track p.id) {
								<li [class.declined]="p.participationStatus === 'DECLINED'">
									<div class="entity-main">
										<strong>{{ p.fullName || p.email }}</strong>
										<span>{{ humanize(p.role) }} · {{ humanize(p.participationStatus) }}</span>
									</div>
									@if (p.participationStatus === 'DECLINED') {
										<span class="declined-tag">Declined</span>
									} @else {
										<span class="share">
											@if (p.role === 'PROVIDER' || p.role === 'BENEFICIARY') {
												{{ p.shareType === 'PERCENT_BPS' ? (p.shareValue / 100) + '%' : (p.entitledAmount | money:escrow.currency) }}
												@if (p.remainingShare != null) {
													<span class="share-left">left {{ p.remainingShare | money:escrow.currency }}</span>
												}
											} @else {
												—
											}
										</span>
									}
								</li>
							}
						</ul>
					</section>

					<section class="sq-panel" id="escrow-conditions">
						<h2>
							Conditions
							<span class="sq-count">{{ satisfiedCount }}/{{ escrow.conditions.length }}</span>
						</h2>
						@if (pendingRequiredConditions.length > 0 && !isTerminalStatus) {
							<p class="condition-hint">
								{{ pendingRequiredConditions.length }} required
								{{ pendingRequiredConditions.length === 1 ? 'condition is' : 'conditions are' }}
								still pending. Mark
								{{ pendingRequiredConditions.length === 1 ? 'it' : 'them' }}
								satisfied before you can request release.
							</p>
						}
						@if (escrow.conditions.length === 0) {
							<div class="sq-empty sq-empty--compact">
								<strong>No conditions</strong>
								<p>Release is not gated on checklist items for this escrow.</p>
							</div>
						} @else {
							<ul class="entity-list">
								@for (c of escrow.conditions; track c.id) {
									<li class="condition" [class.needs-action]="c.required && c.status === 'PENDING'">
										<div class="entity-main">
											<strong>{{ c.title }}</strong>
											<span>{{ c.required ? 'Required' : 'Optional' }}</span>
										</div>
										<app-status-chip [status]="c.status" />
										@if (c.status === 'PENDING' && canSatisfy) {
											<button
												mat-stroked-button
												type="button"
												class="condition-action"
												[disabled]="busy"
												(click)="satisfy(c.id)"
											>
												Mark satisfied
											</button>
										}
									</li>
								}
							</ul>
						}
					</section>
				</div>

				@if (showReleaseForm) {
					<section class="sq-panel">
						<h2>Release funds</h2>
						<p class="release-hint">
							Amount is capped to each payee’s remaining share
							@if (selectedPayeeRemaining) {
								— max for selection:
								<strong>{{ selectedPayeeRemaining | money:escrow.currency }}</strong>
							}
						</p>
						<form class="release" [formGroup]="releaseForm" (ngSubmit)="release()">
							<mat-form-field appearance="outline">
								<mat-label>Payee</mat-label>
								<mat-select
									formControlName="beneficiaryParticipantId"
									(selectionChange)="onReleasePayeeChange($event.value)"
								>
									@for (p of payees; track p.id) {
										<mat-option [value]="p.id">
											{{ p.fullName || p.email }} ({{ humanize(p.role) }})
											@if (p.remainingShare != null) {
												— up to {{ p.remainingShare | money:escrow.currency }}
											}
										</mat-option>
									}
								</mat-select>
							</mat-form-field>
							<mat-form-field appearance="outline">
								<mat-label>Amount</mat-label>
								<input matInput formControlName="amount" />
								@if (selectedPayeeRemaining) {
									<mat-hint>Max {{ selectedPayeeRemaining | money:escrow.currency }}</mat-hint>
								}
							</mat-form-field>
							<button mat-flat-button color="primary" [disabled]="releaseForm.invalid || busy">Release</button>
						</form>
					</section>
				}

				@if (showRefundForm) {
					<section class="sq-panel">
						<h2>Refund available balance</h2>
						<form class="release" [formGroup]="refundForm" (ngSubmit)="refund()">
							<mat-form-field appearance="outline">
								<mat-label>Amount</mat-label>
								<input matInput formControlName="amount" />
							</mat-form-field>
							<button mat-flat-button color="warn" [disabled]="refundForm.invalid || busy">Refund</button>
						</form>
					</section>
				}

				<section class="sq-panel">
					<h2>Activity <span class="sq-count">{{ timeline.length }}</span></h2>
					@if (timeline.length === 0) {
						<div class="sq-empty sq-empty--compact">
							<strong>No activity yet</strong>
							<p>Events like publish, funding, and release will appear here.</p>
						</div>
					} @else {
						<ol class="timeline">
							@for (event of timeline; track event.id) {
								<li>
									<span class="rail" aria-hidden="true"></span>
									<div>
										<strong>{{ humanize(event.action) }}</strong>
										<span>{{ event.createdAt | date:'medium' }}</span>
									</div>
								</li>
							}
						</ol>
					}
				</section>
			</section>
		}
	`,
	styles: [`
		.detail { gap: 0.9rem; }
		.sq-back mat-icon { font-size: 16px; width: 16px; height: 16px; }
		.hero {
			display: grid;
			grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
			gap: 1rem;
			align-items: start;
		}
		.intro { display: grid; gap: 0.35rem; min-width: 0; }
		.eyebrow {
			margin: 0;
			color: var(--sq-accent);
			font-size: 0.72rem;
			font-weight: 700;
			letter-spacing: 0.08em;
			text-transform: uppercase;
		}
		.title-row {
			display: flex;
			gap: 0.75rem;
			align-items: center;
			flex-wrap: wrap;
		}
		h1 {
			margin: 0;
			font-size: clamp(1.5rem, 2.4vw, 1.9rem);
			font-weight: 700;
			line-height: 1.15;
			letter-spacing: -0.03em;
			overflow-wrap: anywhere;
		}
		.description {
			margin: 0;
			color: var(--sq-muted);
			font-size: 0.95rem;
			max-width: 44rem;
		}
		.ref {
			margin: 0.25rem 0 0;
			color: var(--sq-muted);
			font-size: 0.75rem;
			font-variant-numeric: tabular-nums;
			overflow-wrap: anywhere;
		}
		h2 .sq-count { margin-left: 0.4rem; vertical-align: middle; }
		.money-panel {
			background: var(--sq-ink);
			color: #e2e8f0;
			border-radius: var(--sq-radius);
			padding: 1.15rem 1.2rem;
			display: grid;
			gap: 0.35rem;
		}
		.money-panel > span {
			color: #94a3b8;
			font-size: 0.8rem;
		}
		.money-panel > strong {
			font-size: 1.7rem;
			color: #f8fafc;
			margin-bottom: 0.55rem;
		}
		.money-grid {
			display: grid;
			grid-template-columns: repeat(3, 1fr);
			gap: 0.65rem;
			border-top: 1px solid rgba(148, 163, 184, 0.25);
			padding-top: 0.75rem;
		}
		.money-grid span {
			display: block;
			color: #94a3b8;
			font-size: 0.72rem;
		}
		.money-grid strong {
			display: block;
			margin-top: 0.15rem;
			font-size: 0.92rem;
			color: #f8fafc;
			font-variant-numeric: tabular-nums;
		}
		.stages {
			display: grid;
			grid-template-columns: repeat(6, minmax(0, 1fr));
			gap: 0.5rem;
			padding: 0.85rem;
		}
		.stage {
			display: grid;
			gap: 0.35rem;
			justify-items: center;
			text-align: center;
			opacity: 0.45;
		}
		.stage.done, .stage.current { opacity: 1; }
		.stage .index {
			width: 22px;
			height: 22px;
			border-radius: 50%;
			display: grid;
			place-items: center;
			font-size: 0.7rem;
			font-weight: 700;
			background: #e2e8f0;
			color: var(--sq-ink-soft);
		}
		.stage.done .index {
			background: var(--sq-accent-soft);
			color: var(--sq-accent);
		}
		.stage.current .index {
			background: var(--sq-accent);
			color: white;
		}
		.stage .label {
			font-size: 0.72rem;
			font-weight: 600;
			color: var(--sq-ink-soft);
			line-height: 1.25;
			text-align: center;
			width: 100%;
		}
		.actions {
			display: flex;
			flex-wrap: wrap;
			gap: 0.5rem;
		}
		.columns {
			display: grid;
			grid-template-columns: 1fr 1fr;
			gap: 1rem;
		}
		.entity-list, .timeline {
			list-style: none;
			padding: 0;
			margin: 0;
		}
		.entity-list li {
			display: flex;
			justify-content: space-between;
			align-items: center;
			gap: 0.75rem;
			padding: 0.8rem 0;
			border-top: 1px solid var(--sq-border);
		}
		.entity-list li:first-child { border-top: 0; padding-top: 0.15rem; }
		.entity-main { min-width: 0; }
		.entity-main strong {
			display: block;
			font-size: 0.92rem;
			overflow-wrap: anywhere;
		}
		.entity-main span {
			color: var(--sq-muted);
			font-size: 0.82rem;
			display: block;
			margin-top: 0.1rem;
		}
		.share {
			font-weight: 600;
			color: var(--sq-ink);
			font-variant-numeric: tabular-nums;
			white-space: nowrap;
			text-align: right;
		}
		.share-left {
			display: block;
			font-weight: 500;
			font-size: 0.78rem;
			color: var(--sq-muted);
		}
		.release-hint {
			margin: 0 0 0.85rem;
			color: var(--sq-muted);
			font-size: 0.9rem;
			line-height: 1.45;
		}
		.condition-hint {
			margin: 0 0 0.85rem;
			padding: 0.65rem 0.8rem;
			border-radius: 10px;
			border: 1px solid color-mix(in srgb, var(--sq-warn, #b45309) 35%, transparent);
			background: color-mix(in srgb, var(--sq-warn, #b45309) 10%, transparent);
			color: var(--sq-ink);
			font-size: 0.9rem;
			line-height: 1.45;
		}
		.cancel-banner {
			margin: 0;
			padding: 0.75rem 0.9rem;
			border-radius: 10px;
			border: 1px solid color-mix(in srgb, #b91c1c 28%, transparent);
			background: color-mix(in srgb, #b91c1c 8%, transparent);
			color: var(--sq-ink);
			font-size: 0.9rem;
			line-height: 1.45;
		}
		.entity-list li.declined {
			border-color: color-mix(in srgb, #b91c1c 35%, transparent);
			background: color-mix(in srgb, #b91c1c 6%, transparent);
		}
		.declined-tag {
			padding: 0.2rem 0.55rem;
			border-radius: 999px;
			background: color-mix(in srgb, #b91c1c 12%, transparent);
			color: #b91c1c;
			font-size: 0.75rem;
			font-weight: 700;
			white-space: nowrap;
		}
		.entity-list li.condition {
			display: grid;
			grid-template-columns: minmax(0, 1fr) auto;
			align-items: center;
			gap: 0.5rem 0.75rem;
		}
		.entity-list li.condition.needs-action {
			border-color: color-mix(in srgb, var(--sq-warn, #b45309) 40%, transparent);
			background: color-mix(in srgb, var(--sq-warn, #b45309) 6%, transparent);
		}
		.condition-action {
			grid-column: 1 / -1;
			justify-self: start;
		}
		.release {
			display: grid;
			grid-template-columns: 2fr 1fr auto;
			gap: 0.75rem;
			align-items: start;
		}
		.edit { display: grid; gap: 0.25rem; }
		.full { width: 100%; }
		.form-actions {
			display: flex;
			justify-content: flex-end;
			gap: 0.5rem;
		}
		.timeline li {
			display: grid;
			grid-template-columns: 16px 1fr;
			gap: 0.75rem;
			padding: 0.65rem 0;
		}
		.rail {
			width: 10px;
			height: 10px;
			border-radius: 50%;
			background: var(--sq-accent);
			margin-top: 0.35rem;
			box-shadow: 0 0 0 3px var(--sq-accent-soft);
		}
		.timeline strong {
			display: block;
			font-size: 0.9rem;
		}
		.timeline span {
			color: var(--sq-muted);
			font-size: 0.78rem;
		}
		.muted, .center { color: var(--sq-muted); }
		.center { display: grid; place-items: center; padding: 3rem 0; }
		.checkout-panel { min-height: 420px; }
		.checkout { min-height: 380px; }
		.checkout-loading {
			display: grid;
			justify-items: center;
			gap: 0.75rem;
			padding: 2.5rem 1rem;
			color: var(--sq-muted);
			font-size: 0.9rem;
		}
		.checkout-loading p { margin: 0; }
		@media (max-width: 900px) {
			.hero, .columns, .release, .stages { grid-template-columns: 1fr; }
			.money-grid { grid-template-columns: 1fr; }
			.stages { gap: 0.75rem; }
		}
	`],
})
export class EscrowDetailComponent implements OnInit {
	private readonly route = inject(ActivatedRoute)
	private readonly router = inject(Router)
	private readonly escrows = inject(EscrowService)
	private readonly auth = inject(AuthService)
	private readonly fb = inject(FormBuilder)
	private readonly dialog = inject(MatDialog)

	escrow: Escrow | null = null
	timeline: TimelineEvent[] = []
	loading = true
	busy = false
	error = ''
	showCheckout = false
	showReleaseForm = false
	showRefundForm = false
	showEditForm = false
	private escrowId = ''
	private confirmingFunding = false

	releaseForm = this.fb.nonNullable.group({
		beneficiaryParticipantId: ['', Validators.required],
		amount: ['', Validators.required],
	})

	refundForm = this.fb.nonNullable.group({
		amount: ['', Validators.required],
	})

	editForm = this.fb.nonNullable.group({
		title: ['', [Validators.required, Validators.maxLength(200)]],
		description: ['', Validators.maxLength(5000)],
	})

	get payees() {
		return this.escrow?.participants.filter((p) => p.role === 'PROVIDER' || p.role === 'BENEFICIARY') || []
	}

	get selectedPayeeRemaining(): string | null {
		const id = this.releaseForm.getRawValue().beneficiaryParticipantId
		const payee = this.payees.find((p) => p.id === id)
		return payee?.remainingShare ?? null
	}

	get canSatisfy(): boolean {
		return !!this.escrow?.availableActions.includes('SATISFY_CONDITION')
	}

	onReleasePayeeChange(participantId: string): void {
		this.syncReleaseAmount(participantId)
	}

	openReleaseForm(): void {
		this.showRefundForm = false
		this.showEditForm = false
		this.showReleaseForm = true
		const preferred =
			this.payees.find((p) => Number(p.remainingShare || 0) > 0)?.id
			|| this.payees[0]?.id
			|| ''
		if (preferred) {
			this.releaseForm.patchValue({ beneficiaryParticipantId: preferred })
			this.syncReleaseAmount(preferred)
		}
	}

	private syncReleaseAmount(participantId: string): void {
		const payee = this.payees.find((p) => p.id === participantId)
		const remaining = payee?.remainingShare
		if (remaining != null) {
			this.releaseForm.patchValue({ amount: remaining })
		}
	}

	ngOnInit(): void {
		this.escrowId = this.route.snapshot.paramMap.get('id') || ''
		const funded = this.route.snapshot.queryParamMap.get('funded')
		const sessionId = this.route.snapshot.queryParamMap.get('session_id')
		if (funded === '1' && sessionId) {
			this.busy = true
			this.auth.authReady$.pipe(
				filter((ready) => ready === true),
				take(1),
				switchMap(() => this.auth.ensureFreshSession()),
				switchMap((ok) => {
					if (!ok) {
						this.busy = false
						this.loading = false
						this.error =
							'Payment may have succeeded, but your session could not be restored. ' +
							'Stay on this page, sign in from another tab if needed, then refresh — ' +
							'do not pay again until status updates.'
						this.reload()
						return []
					}
					return this.confirmFundingRequest(sessionId)
				}),
			).subscribe({
				next: (escrow) => this.onFundingConfirmed(escrow),
				error: (err) => {
					this.handleError(err)
					this.pollAfterFunding()
				},
			})
			return
		}
		if (funded === '0') {
			this.error = 'Checkout was cancelled. No funds were captured.'
			this.clearFundingQueryParams()
		}
		this.reload()
	}

	private confirmFundingRequest(sessionId: string) {
		this.confirmingFunding = true
		return this.escrows.confirmFunding(this.escrowId, sessionId)
	}

	private onFundingConfirmed(escrow: Escrow): void {
		this.escrow = escrow
		this.busy = false
		this.loading = false
		this.showCheckout = false
		this.confirmingFunding = false
		this.clearFundingQueryParams()
		this.escrows.timeline(this.escrowId).subscribe((events) => (this.timeline = events))
	}

	private clearFundingQueryParams(): void {
		this.router.navigate([], {
			relativeTo: this.route,
			queryParams: {},
			replaceUrl: true,
		})
	}

	private pollAfterFunding(attempt = 0): void {
		this.reload()
		if (attempt >= 8) {
			this.busy = false
			if (!this.error) {
				this.error =
					'Payment submitted, but escrow status has not updated yet. Refresh this page shortly — do not pay again.'
			}
			return
		}
		setTimeout(() => {
			this.escrows.get(this.escrowId).subscribe({
				next: (escrow) => {
					this.escrow = escrow
					if (escrow.status === 'FUNDED' || escrow.status === 'IN_PROGRESS') {
						this.busy = false
						this.showCheckout = false
						this.error = ''
						this.clearFundingQueryParams()
						return
					}
					this.pollAfterFunding(attempt + 1)
				},
				error: (err) => {
					this.busy = false
					this.handleError(err)
				},
			})
		}, 1500)
	}

	reload(): void {
		this.loading = true
		this.escrows.get(this.escrowId).subscribe({
			next: (escrow) => {
				this.escrow = escrow
				this.loading = false
				this.refundForm.patchValue({ amount: escrow.amountAvailable })
				const selected = this.releaseForm.getRawValue().beneficiaryParticipantId
				const payeeId =
					(selected && this.payees.some((p) => p.id === selected) ? selected : null)
					|| this.payees.find((p) => Number(p.remainingShare || 0) > 0)?.id
					|| this.payees[0]?.id
				if (payeeId) {
					this.releaseForm.patchValue({ beneficiaryParticipantId: payeeId })
					this.syncReleaseAmount(payeeId)
				}
			},
			error: () => {
				this.loading = false
				this.error = 'Unable to load escrow'
			},
		})
		this.escrows.timeline(this.escrowId).subscribe((events) => (this.timeline = events))
	}

	readonly progressStages = [
		{ key: 'DRAFT', label: 'Draft' },
		{ key: 'PENDING_PARTICIPANTS', label: 'Participants' },
		{ key: 'PENDING_FUNDING', label: 'Funding' },
		{ key: 'FUNDED', label: 'Funded' },
		{ key: 'IN_PROGRESS', label: 'In progress' },
		{ key: 'AWAITING_RELEASE', label: 'Release' },
	] as const

	/** Actions this page knows how to run; anything else is never rendered. */
	private static readonly SUPPORTED_ACTIONS = new Set([
		'PUBLISH',
		'UPDATE',
		'CANCEL',
		'ACCEPT',
		'DECLINE',
		'FUND',
		'START',
		'REQUEST_RELEASE',
		'APPROVE_RELEASE',
		'REJECT_RELEASE',
		'RELEASE',
		'REFUND',
		'DISPUTE',
		'RESOLVE_DISPUTE_RELEASE',
		'RESOLVE_DISPUTE_REFUND',
		'RESOLVE_DISPUTE_CANCEL',
	])

	get visibleActions(): string[] {
		return (this.escrow?.availableActions || []).filter((action) =>
			EscrowDetailComponent.SUPPORTED_ACTIONS.has(action),
		)
	}

	get satisfiedCount(): number {
		return this.escrow?.conditions.filter((c) => c.status === 'SATISFIED').length || 0
	}

	humanize(value: string): string {
		const text = value.replaceAll('_', ' ').toLowerCase()
		return text.charAt(0).toUpperCase() + text.slice(1)
	}

	actionLabel(action: string): string {
		return this.humanize(action)
	}

	isPrimaryAction(action: string): boolean {
		return ['FUND', 'PUBLISH', 'RELEASE', 'START', 'REQUEST_RELEASE', 'APPROVE_RELEASE'].includes(action)
	}

	actionColor(action: string): 'primary' | 'warn' | undefined {
		if (['CANCEL', 'REFUND', 'DISPUTE', 'DECLINE', 'REJECT_RELEASE', 'RESOLVE_DISPUTE_CANCEL'].includes(action)) {
			return 'warn'
		}
		if (this.isPrimaryAction(action)) {
			return 'primary'
		}
		return undefined
	}

	stageState(stageKey: string): 'done' | 'current' | 'upcoming' {
		if (!this.escrow) return 'upcoming'
		const order = [
			'DRAFT',
			'PENDING_PARTICIPANTS',
			'PENDING_FUNDING',
			'FUNDED',
			'IN_PROGRESS',
			'AWAITING_RELEASE',
			'PARTIALLY_RELEASED',
			'RELEASED',
		]
		const status = this.escrow.status
		// Terminal statuses: highlight the stage where the escrow stopped.
		if (status === 'CANCELLED' || status === 'EXPIRED') {
			const stopKey = this.stoppedAtStageKey()
			const stopIdx = order.indexOf(stopKey)
			const stageIdx = order.indexOf(stageKey)
			if (stageIdx < stopIdx) return 'done'
			if (stageIdx === stopIdx) return 'current'
			return 'upcoming'
		}
		if (['REFUNDED', 'DISPUTED'].includes(status)) {
			return stageKey === 'DRAFT' ? 'done' : 'upcoming'
		}
		const currentIdx = order.indexOf(status === 'PARTIALLY_RELEASED' || status === 'RELEASED' ? 'AWAITING_RELEASE' : status)
		const stageIdx = order.indexOf(stageKey)
		if (status === 'RELEASED' || status === 'PARTIALLY_RELEASED') {
			return 'done'
		}
		if (stageIdx < currentIdx) return 'done'
		if (stageIdx === currentIdx) return 'current'
		return 'upcoming'
	}

	/** Infer where a cancelled/expired escrow stopped, from timeline + balances. */
	private stoppedAtStageKey(): string {
		if (this.escrow?.status === 'EXPIRED') {
			return 'PENDING_FUNDING'
		}
		const actions = new Set(this.timeline.map((e) => e.action))
		const funded = Number(this.escrow?.amountFunded || 0)
		if (actions.has('RELEASE_REQUESTED') || actions.has('RELEASE_APPROVED')) {
			return 'AWAITING_RELEASE'
		}
		if (actions.has('ESCROW_STARTED') || actions.has('CONDITION_SATISFIED')) {
			return 'IN_PROGRESS'
		}
		if (actions.has('ESCROW_FUNDED') || actions.has('PAYMENT_SUCCEEDED') || funded > 0) {
			return 'FUNDED'
		}
		if (
			actions.has('ESCROW_READY_FOR_FUNDING')
			|| actions.has('PAYMENT_INITIATED')
			|| actions.has('PAYMENT_FAILED')
		) {
			return 'PENDING_FUNDING'
		}
		if (
			actions.has('ESCROW_PUBLISHED')
			|| actions.has('PARTICIPANT_ACCEPTED')
			|| actions.has('PARTICIPANT_DECLINED')
		) {
			return 'PENDING_PARTICIPANTS'
		}
		return 'DRAFT'
	}

	get isTerminalStatus(): boolean {
		return ['CANCELLED', 'EXPIRED', 'REFUNDED', 'RELEASED'].includes(this.escrow?.status || '')
	}

	get declinedParticipants() {
		return (this.escrow?.participants || []).filter((p) => p.participationStatus === 'DECLINED')
	}

	get declinedNames(): string {
		return this.declinedParticipants
			.map((p) => p.fullName || p.email)
			.filter(Boolean)
			.join(', ')
	}

	runAction(action: string): void {
		this.error = ''
		switch (action) {
			case 'PUBLISH':
				this.confirm('Publish this escrow and invite participants?', () => this.escrows.publish(this.escrowId))
				break
			case 'UPDATE':
				this.openEdit()
				break
			case 'CANCEL':
				this.confirm('Cancel this escrow?', () => this.escrows.cancel(this.escrowId))
				break
			case 'ACCEPT':
				this.wrap(this.escrows.accept(this.escrowId))
				break
			case 'DECLINE':
				this.confirm('Decline participation?', () => this.escrows.decline(this.escrowId), {
					afterSuccess: () => this.reload(),
				})
				break
			case 'FUND':
				this.startFunding()
				break
			case 'START':
				this.wrap(this.escrows.start(this.escrowId))
				break
			case 'REQUEST_RELEASE':
				this.requestRelease()
				break
			case 'APPROVE_RELEASE':
				this.wrap(this.escrows.approveRelease(this.escrowId))
				break
			case 'REJECT_RELEASE':
				this.wrap(this.escrows.rejectRelease(this.escrowId))
				break
			case 'RELEASE':
				this.openReleaseForm()
				break
			case 'REFUND':
				this.showReleaseForm = false
				this.showRefundForm = true
				break
			case 'DISPUTE':
				this.openDispute()
				break
			case 'RESOLVE_DISPUTE_RELEASE':
				this.resolveDispute('RELEASE')
				break
			case 'RESOLVE_DISPUTE_REFUND':
				this.resolveDispute('REFUND')
				break
			case 'RESOLVE_DISPUTE_CANCEL':
				this.resolveDispute('CANCEL')
				break
			default:
				break
		}
	}

	satisfy(conditionId: string): void {
		this.wrap(this.escrows.satisfyCondition(this.escrowId, conditionId))
	}

	get pendingRequiredConditions() {
		return (this.escrow?.conditions || []).filter(
			(c) => c.required && c.status !== 'SATISFIED' && c.status !== 'WAIVED',
		)
	}

	private requestRelease(): void {
		const pending = this.pendingRequiredConditions
		if (pending.length > 0) {
			const names = pending.map((c) => `"${c.title}"`).join(', ')
			this.error =
				`Mark all required conditions as satisfied before requesting release. ` +
				`Still pending: ${names}. Use Mark satisfied under Conditions.`
			document.getElementById('escrow-conditions')?.scrollIntoView({
				behavior: 'smooth',
				block: 'center',
			})
			return
		}
		this.wrap(this.escrows.requestRelease(this.escrowId))
	}

	saveEdit(): void {
		if (this.editForm.invalid || !this.escrow) return
		const { title, description } = this.editForm.getRawValue()
		this.wrap(this.escrows.update(this.escrowId, {
			title: title.trim(),
			description: description.trim(),
			fundingDeadline: this.escrow.fundingDeadline,
			completionDeadline: this.escrow.completionDeadline,
		}))
	}

	private openEdit(): void {
		if (!this.escrow) return
		this.editForm.reset({
			title: this.escrow.title,
			description: this.escrow.description || '',
		})
		this.showReleaseForm = false
		this.showRefundForm = false
		this.showEditForm = true
	}

	release(): void {
		if (this.releaseForm.invalid || !this.escrow) return
		const { beneficiaryParticipantId, amount } = this.releaseForm.getRawValue()
		const max = this.selectedPayeeRemaining
		if (max != null && Number(amount) > Number(max) + 1e-9) {
			this.error =
				`Amount exceeds this payee’s remaining share (${max} ${this.escrow.currency}).`
			return
		}
		this.confirm(`Release ${amount} ${this.escrow.currency}?`, () =>
			this.escrows.release(this.escrowId, beneficiaryParticipantId, amount),
		)
	}

	refund(): void {
		if (this.refundForm.invalid || !this.escrow) return
		const { amount } = this.refundForm.getRawValue()
		this.confirm(`Refund ${amount} ${this.escrow.currency} to the client?`, () =>
			this.escrows.refund(this.escrowId, amount),
		)
	}

	private openDispute(): void {
		const reason = window.prompt('Describe the dispute reason')
		if (!reason) return
		this.wrap(this.escrows.dispute(this.escrowId, reason))
	}

	private resolveDispute(resolution: string): void {
		const labels: Record<string, string> = {
			RELEASE: 'Resolve dispute by allowing release?',
			REFUND: 'Resolve dispute as refunded? Available funds must already be 0.',
			CANCEL: 'Resolve dispute by cancelling? Available funds must already be 0.',
		}
		this.confirm(labels[resolution] || 'Resolve dispute?', () =>
			this.escrows.resolveDispute(this.escrowId, resolution),
		)
	}

	private startFunding(): void {
		this.busy = true
		this.error = ''
		this.showCheckout = true
		this.auth.ensureFreshSession().pipe(
			switchMap((ok) => {
				if (!ok) {
					this.busy = false
					this.showCheckout = false
					this.error = 'Your session expired. Please sign in again, then retry Fund.'
					return []
				}
				return this.escrows.fund(this.escrowId, window.location.origin)
			}),
		).subscribe({
			next: (fund) => {
				if (!fund.checkoutUrl) {
					this.busy = false
					this.showCheckout = false
					this.error = 'Stripe Checkout URL missing — try Fund again'
					return
				}
				// Hosted Checkout: leave the app, pay on Stripe, then return with ?funded=1
				window.location.assign(fund.checkoutUrl)
			},
			error: (err) => {
				this.showCheckout = false
				this.handleError(err)
			},
		})
	}

	private confirm(
		message: string,
		action: () => import('rxjs').Observable<unknown>,
		options?: { afterSuccess?: () => void },
	): void {
		const ref = this.dialog.open(ConfirmDialogComponent, {
			data: { title: 'Confirm', message },
			width: '420px',
		})
		ref.afterClosed().subscribe((ok) => {
			if (ok) this.wrap(action(), options)
		})
	}

	private wrap(
		obs: import('rxjs').Observable<unknown>,
		options?: { afterSuccess?: () => void },
	): void {
		this.busy = true
		obs.subscribe({
			next: () => {
				this.busy = false
				this.showReleaseForm = false
				this.showRefundForm = false
				this.showEditForm = false
				if (options?.afterSuccess) {
					options.afterSuccess()
					return
				}
				this.reload()
			},
			error: (err) => this.handleError(err),
		})
	}

	private handleError(err: { error?: ApiError; status?: number }): void {
		this.busy = false
		if (err.status === 401) {
			this.error =
				err.error?.message ||
				'Your session expired while processing this action. Sign in again if needed, then refresh — do not repeat a successful payment.'
			return
		}
		const message = err.error?.message || 'Action failed'
		const isConditions =
			/required conditions|still pending|mark all required/i.test(message)
			|| (err.error?.code === 'ESCROW_INVALID_STATE'
				&& /condition/i.test(message))
		if (isConditions) {
			this.error =
				message.includes('Mark all required')
					? message
					: `Mark all required conditions as satisfied before requesting release. ${message}`
			document.getElementById('escrow-conditions')?.scrollIntoView({
				behavior: 'smooth',
				block: 'center',
			})
			return
		}
		this.error = message
	}
}

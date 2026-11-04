import { Component, inject, OnDestroy, signal } from '@angular/core'
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms'
import { Router, RouterLink } from '@angular/router'
import { MatFormFieldModule } from '@angular/material/form-field'
import { MatInputModule } from '@angular/material/input'
import { MatSelectModule } from '@angular/material/select'
import { MatButtonModule } from '@angular/material/button'
import { MatCheckboxModule } from '@angular/material/checkbox'
import { MatIconModule } from '@angular/material/icon'
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete'
import { AuthService } from '../../core/services/auth.service'
import { EscrowService } from '../../core/services/escrow.service'
import { ApiError } from '../../core/models/api.models'
import { MoneyPipe } from '../../shared/money.pipe'
import { PageHeaderComponent } from '../../shared/page-header/page-header.component'

type LookupState = 'idle' | 'checking' | 'found' | 'missing'
type UserOption = { id: string; email: string; fullName: string }

@Component({
	selector: 'app-escrow-create',
	standalone: true,
	imports: [
		ReactiveFormsModule,
		RouterLink,
		MatFormFieldModule,
		MatInputModule,
		MatSelectModule,
		MatButtonModule,
		MatCheckboxModule,
		MatIconModule,
		MatAutocompleteModule,
		PageHeaderComponent,
	],
	template: `
		<section class="sq-page wrap">
			<app-page-header eyebrow="Escrows" title="Create escrow" [subtitle]="summary()" />

			<form class="form" [formGroup]="form" (ngSubmit)="submit()">
				<section class="sq-panel">
					<div class="sq-panel-head">
						<div>
							<h2>Details</h2>
							<p>{{ detailsSummary() }}</p>
						</div>
					</div>
					<div class="grid">
						<mat-form-field appearance="outline" class="span-2">
							<mat-label>Title</mat-label>
							<input matInput formControlName="title" />
						</mat-form-field>
						<mat-form-field appearance="outline">
							<mat-label>Currency</mat-label>
							<mat-select formControlName="currency">
								<mat-option value="USD">USD</mat-option>
								<mat-option value="EUR">EUR</mat-option>
								<mat-option value="GBP">GBP</mat-option>
							</mat-select>
						</mat-form-field>
						<mat-form-field appearance="outline">
							<mat-label>Amount</mat-label>
							<input matInput inputmode="decimal" formControlName="amount" />
						</mat-form-field>
					</div>
					<mat-form-field appearance="outline" class="full">
						<mat-label>Description</mat-label>
						<textarea matInput rows="3" formControlName="description"></textarea>
					</mat-form-field>
				</section>

				<section class="sq-panel">
					<div class="sq-panel-head">
						<div>
							<h2>Participants <span class="sq-count">{{ participants.length + 1 }}</span></h2>
							<p>{{ participantsSummary() }}</p>
						</div>
						<button mat-stroked-button type="button" (click)="addParticipant()">
							<mat-icon>person_add</mat-icon>
							Add participant
						</button>
					</div>

					<ul class="people">
						<li class="person self">
							<div class="who">
								<span class="avatar">{{ initials() }}</span>
								<div>
									<strong>{{ currentUser()?.fullName }}</strong>
									<span>{{ currentUser()?.email }}</span>
								</div>
							</div>
							<span class="role-tag">Client · you</span>
						</li>

						<div formArrayName="participants">
							@for (ctrl of participants.controls; track ctrl; let i = $index) {
								<li class="person" [formGroupName]="i">
									<div class="person-grid">
										<mat-form-field appearance="outline" class="participant">
											<mat-label>Participant</mat-label>
											<input
												matInput
												formControlName="displayName"
												[matAutocomplete]="auto"
												(input)="onParticipantQuery(i)"
												placeholder="Search by full name"
											/>
											<mat-autocomplete
												#auto="matAutocomplete"
												[displayWith]="displayParticipant"
												(optionSelected)="onParticipantSelected(i, $event)"
											>
												@for (user of optionsFor(i); track user.id) {
													<mat-option [value]="user">
														<span class="opt-name">{{ user.fullName }}</span>
														<span class="opt-email">{{ user.email }}</span>
													</mat-option>
												}
												@if (lookupState(i) === 'checking') {
													<mat-option disabled>Searching…</mat-option>
												}
												@if (
													lookupState(i) === 'missing'
													&& participants.at(i).get('displayName')?.value
												) {
													<mat-option disabled>No matching users</mat-option>
												}
											</mat-autocomplete>
											@switch (lookupState(i)) {
												@case ('found') {
													<mat-hint class="ok">{{ resolvedEmail(i) }}</mat-hint>
												}
												@case ('missing') {
													<mat-error>Select a registered user from the list</mat-error>
												}
												@default {
													<mat-hint>Type a name to search registered users</mat-hint>
												}
											}
										</mat-form-field>
										<mat-form-field appearance="outline">
											<mat-label>Role</mat-label>
											<mat-select
												formControlName="role"
												(selectionChange)="onRoleChange(i, $event.value)"
											>
												<mat-option value="PROVIDER">Provider</mat-option>
												<mat-option value="BENEFICIARY">Beneficiary</mat-option>
												<mat-option value="APPROVER">Approver</mat-option>
												<mat-option value="ARBITRATOR">Arbitrator</mat-option>
											</mat-select>
										</mat-form-field>
										@if (isPayeeRole(participants.at(i).get('role')?.value)) {
											<mat-form-field appearance="outline">
												<mat-label>Share type</mat-label>
												<mat-select formControlName="shareType">
													<mat-option value="PERCENT_BPS">Percent</mat-option>
													<mat-option value="FIXED_MINOR">Fixed amount</mat-option>
												</mat-select>
											</mat-form-field>
											<mat-form-field appearance="outline">
												<mat-label>{{ shareLabel(i) }}</mat-label>
												<input matInput type="number" min="0" formControlName="shareValue" />
												<mat-hint>{{ sharePreview(i) }}</mat-hint>
											</mat-form-field>
										}
									</div>
									<div class="person-foot">
										@if (participants.at(i).get('role')?.value === 'APPROVER') {
											<span class="role-note">Approves release; no payout share</span>
										} @else if (participants.at(i).get('role')?.value === 'ARBITRATOR') {
											<span class="role-note">Resolves disputes; no payout share</span>
										} @else if (participants.at(i).get('role')?.value === 'PROVIDER') {
											<span class="role-note">Providers request release; they cannot approve it</span>
										} @else if (participants.at(i).get('role')?.value === 'BENEFICIARY') {
											<span class="role-note">Receives a share of released funds</span>
										}
										<button
											mat-button
											type="button"
											color="warn"
											(click)="removeParticipant(i)"
										>
											Remove
										</button>
									</div>
								</li>
							}
						</div>
					</ul>
				</section>

				<section class="sq-panel">
					<div class="sq-panel-head">
						<div>
							<h2>Release conditions <span class="sq-count">{{ conditions.length }}</span></h2>
							<p>{{ conditionsSummary() }}</p>
						</div>
						<button mat-stroked-button type="button" (click)="addCondition()">
							<mat-icon>add_task</mat-icon>
							Add condition
						</button>
					</div>
					<div formArrayName="conditions" class="conditions">
						@for (ctrl of conditions.controls; track ctrl; let i = $index) {
							<div class="condition" [formGroupName]="i">
								<mat-form-field appearance="outline" class="full" subscriptSizing="dynamic">
									<mat-label>Condition</mat-label>
									<input matInput formControlName="title" />
								</mat-form-field>
								<mat-checkbox formControlName="required">Required</mat-checkbox>
								<button
									mat-icon-button
									type="button"
									aria-label="Remove condition"
									[disabled]="conditions.length === 1"
									(click)="removeCondition(i)"
								>
									<mat-icon>close</mat-icon>
								</button>
							</div>
						}
					</div>
				</section>

				@if (error()) {
					<p class="sq-error" role="alert">{{ error() }}</p>
				}

				<div class="actions">
					<a mat-button routerLink="/escrows">Cancel</a>
					<button mat-flat-button color="primary" [disabled]="form.invalid || loading()">
						{{ loading() ? 'Creating…' : 'Create draft' }}
					</button>
				</div>
			</form>
		</section>
	`,
	styles: [`
		.wrap { max-width: 920px; }
		.form { display: grid; gap: 1rem; }
		.grid {
			display: grid;
			grid-template-columns: repeat(4, minmax(0, 1fr));
			gap: 0.75rem;
		}
		.span-2 { grid-column: span 2; }
		.full { width: 100%; }
		h2 .sq-count { margin-left: 0.4rem; vertical-align: middle; }
		.people {
			list-style: none;
			margin: 0;
			padding: 0;
			display: grid;
			gap: 0.75rem;
		}
		.people > div { display: contents; }
		.person {
			border: 1px solid var(--sq-border);
			border-radius: var(--sq-radius-sm);
			background: var(--sq-surface-2);
			padding: 0.85rem 0.9rem 0.5rem;
		}
		.person.self {
			display: flex;
			justify-content: space-between;
			align-items: center;
			gap: 1rem;
			padding: 0.75rem 0.9rem;
		}
		.who {
			display: flex;
			align-items: center;
			gap: 0.7rem;
			min-width: 0;
		}
		.who strong { display: block; font-size: 0.92rem; }
		.who div span {
			display: block;
			color: var(--sq-muted);
			font-size: 0.8rem;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.avatar {
			width: 34px;
			height: 34px;
			border-radius: 50%;
			background: var(--sq-accent-soft);
			color: var(--sq-accent);
			display: grid;
			place-items: center;
			font-size: 0.78rem;
			font-weight: 700;
			flex-shrink: 0;
		}
		.role-tag {
			padding: 0.2rem 0.6rem;
			border-radius: 999px;
			background: var(--sq-surface);
			border: 1px solid var(--sq-border);
			color: var(--sq-ink-soft);
			font-size: 0.75rem;
			font-weight: 600;
			white-space: nowrap;
		}
		.person-grid {
			display: grid;
			grid-template-columns: minmax(0, 1.6fr) repeat(3, minmax(150px, 1fr));
			gap: 0.6rem;
		}
		.person-foot {
			display: flex;
			justify-content: space-between;
			align-items: center;
			gap: 0.75rem;
		}
		.role-note {
			color: var(--sq-muted);
			font-size: 0.8rem;
		}
		.ok { color: var(--sq-success); }
		.opt-name {
			display: block;
			font-weight: 600;
		}
		.opt-email {
			display: block;
			font-size: 0.8rem;
			color: var(--sq-muted);
		}
		.conditions { display: grid; gap: 0.6rem; }
		.condition {
			display: grid;
			grid-template-columns: 1fr auto auto;
			gap: 0.6rem;
			align-items: center;
		}
		.actions {
			display: flex;
			justify-content: flex-end;
			gap: 0.6rem;
		}
		@media (max-width: 1100px) {
			.person-grid { grid-template-columns: 1fr 1fr 1fr; }
			.person-grid .participant { grid-column: span 3; }
		}
		@media (max-width: 900px) {
			.grid { grid-template-columns: 1fr 1fr; }
			.span-2 { grid-column: span 2; }
			.person-grid { grid-template-columns: 1fr 1fr; }
			.person-grid .participant { grid-column: span 2; }
		}
		@media (max-width: 560px) {
			.grid, .person-grid { grid-template-columns: 1fr; }
			.span-2, .person-grid .participant { grid-column: auto; }
			.person.self { flex-direction: column; align-items: flex-start; }
		}
	`],
})
export class EscrowCreateComponent implements OnDestroy {
	private readonly fb = inject(FormBuilder)
	private readonly escrows = inject(EscrowService)
	private readonly auth = inject(AuthService)
	private readonly router = inject(Router)
	private readonly money = new MoneyPipe()
	private readonly searchTimers = new Map<number, ReturnType<typeof setTimeout>>()
	private readonly optionsByIndex = signal<Record<number, UserOption[]>>({})

	readonly loading = signal(false)
	readonly error = signal('')
	readonly currentUser = this.auth.user
	readonly summary = signal('')
	readonly detailsSummary = signal('')
	readonly participantsSummary = signal('')
	readonly conditionsSummary = signal('')

	form = this.fb.nonNullable.group({
		title: ['', Validators.required],
		description: [''],
		currency: ['USD', Validators.required],
		amount: ['', Validators.required],
		participants: this.fb.array<FormGroup>([]),
		conditions: this.fb.array<FormGroup>([]),
	})

	constructor() {
		this.addCondition()
		this.form.valueChanges.subscribe(() => this.refreshSummaries())
		this.refreshSummaries()
	}

	ngOnDestroy(): void {
		for (const timer of this.searchTimers.values()) {
			clearTimeout(timer)
		}
		this.searchTimers.clear()
	}

	get participants(): FormArray<FormGroup> {
		return this.form.controls.participants
	}

	get conditions(): FormArray<FormGroup> {
		return this.form.controls.conditions
	}

	initials(): string {
		const name = this.currentUser()?.fullName || 'U'
		return name
			.split(/\s+/)
			.slice(0, 2)
			.map((part) => part[0]?.toUpperCase() || '')
			.join('')
	}

	addParticipant(): void {
		this.participants.push(this.fb.nonNullable.group({
			displayName: ['', Validators.required],
			email: [''],
			userId: ['', Validators.required],
			fullName: [''],
			lookup: ['idle' as LookupState],
			role: ['PROVIDER', Validators.required],
			shareType: ['PERCENT_BPS', Validators.required],
			shareValue: [10000, [Validators.required, Validators.min(0)]],
			canApproveRelease: [false],
		}))
	}

	removeParticipant(index: number): void {
		const timer = this.searchTimers.get(index)
		if (timer) {
			clearTimeout(timer)
			this.searchTimers.delete(index)
		}
		this.participants.removeAt(index)
		const next = { ...this.optionsByIndex() }
		delete next[index]
		this.optionsByIndex.set(next)
	}

	addCondition(): void {
		this.conditions.push(this.fb.nonNullable.group({
			title: ['', Validators.required],
			description: [''],
			required: [true],
		}))
	}

	removeCondition(index: number): void {
		if (this.conditions.length === 1) return
		this.conditions.removeAt(index)
	}

	lookupState(index: number): LookupState {
		return (this.participants.at(index).get('lookup')?.value as LookupState) || 'idle'
	}

	resolvedEmail(index: number): string {
		return String(this.participants.at(index).get('email')?.value || '')
	}

	optionsFor(index: number): UserOption[] {
		return this.optionsByIndex()[index] || []
	}

	displayParticipant = (value: string | UserOption | null): string => {
		if (!value) return ''
		if (typeof value === 'string') return value
		return value.fullName
	}

	onParticipantQuery(index: number): void {
		const group = this.participants.at(index)
		const raw = group.get('displayName')?.value
		const query = (typeof raw === 'string' ? raw : raw?.fullName || '').trim()
		const selectedName = String(group.get('fullName')?.value || '')
		if (selectedName && query !== selectedName) {
			group.patchValue({ userId: '', email: '', fullName: '', lookup: 'idle' })
		}

		const existing = this.searchTimers.get(index)
		if (existing) clearTimeout(existing)

		if (query.length < 2) {
			this.setOptions(index, [])
			group.patchValue({ lookup: 'idle' })
			return
		}

		group.patchValue({ lookup: 'checking' })
		this.searchTimers.set(index, setTimeout(() => this.searchUsers(index, query), 250))
	}

	onParticipantSelected(index: number, event: MatAutocompleteSelectedEvent): void {
		const user = event.option.value as UserOption
		const group = this.participants.at(index)
		group.patchValue({
			displayName: user.fullName,
			email: user.email,
			userId: user.id,
			fullName: user.fullName,
			lookup: 'found',
		})
		group.get('displayName')?.setErrors(null)
		this.setOptions(index, [])
	}

	isPayeeRole(role: string | null | undefined): boolean {
		return role === 'PROVIDER' || role === 'BENEFICIARY'
	}

	onRoleChange(index: number, role: string): void {
		const group = this.participants.at(index)
		if (this.isPayeeRole(role)) {
			const current = Number(group.get('shareValue')?.value)
			group.patchValue({
				shareType: group.get('shareType')?.value || 'PERCENT_BPS',
				shareValue: current > 0 ? current : 0,
			})
		} else {
			group.patchValue({ shareType: 'PERCENT_BPS', shareValue: 0 })
		}
	}

	shareLabel(index: number): string {
		const type = this.participants.at(index).get('shareType')?.value
		return type === 'FIXED_MINOR' ? 'Amount (cents)' : 'Basis points'
	}

	sharePreview(index: number): string {
		const group = this.participants.at(index)
		const type = group.get('shareType')?.value
		const value = Number(group.get('shareValue')?.value)
		if (Number.isNaN(value)) return ''
		if (type === 'FIXED_MINOR') {
			return `= ${this.money.transform(value / 100, this.form.controls.currency.value)}`
		}
		return `= ${(value / 100).toFixed(value % 100 === 0 ? 0 : 2)}%`
	}

	submit(): void {
		if (this.form.invalid) return
		this.error.set('')
		const shareError = this.validatePayeeShares()
		if (shareError) {
			this.error.set(shareError)
			return
		}
		this.loading.set(true)
		const raw = this.form.getRawValue()
		const body = {
			title: raw.title,
			description: raw.description,
			currency: raw.currency,
			amount: raw.amount,
			conditions: raw.conditions.map((c) => ({
				title: String(c['title']),
				description: String(c['description'] || ''),
				required: Boolean(c['required']),
			})),
			participants: raw.participants.map((p) => {
				const role = String(p['role'])
				const isPayee = this.isPayeeRole(role)
				return {
					userId: String(p['userId']),
					role,
					shareType: isPayee ? String(p['shareType']) : 'PERCENT_BPS',
					shareValue: isPayee ? Number(p['shareValue']) : 0,
					canApproveRelease: Boolean(p['canApproveRelease']),
				}
			}),
		}
		this.escrows.create(body).subscribe({
			next: (escrow) => this.router.navigate(['/escrows', escrow.id]),
			error: (err: { status?: number; error?: ApiError }) => {
				this.loading.set(false)
				if (err.status === 401 || err.status === 403) {
					this.error.set(
						err.error?.message || 'Your session expired. Please sign in again.',
					)
					return
				}
				const details = err.error?.details
					?.map((d) => `${d.field}: ${d.message}`)
					.join(' · ')
				this.error.set(
					details
						|| err.error?.message
						|| 'Unable to create escrow',
				)
			},
		})
	}

	private searchUsers(index: number, query: string): void {
		this.escrows.searchUsers(query).subscribe({
			next: (users) => {
				const me = this.currentUser()?.id
				const selected = new Set(
					this.participants.controls
						.map((g, i) => (i === index ? '' : String(g.get('userId')?.value || '')))
						.filter(Boolean),
				)
				const filtered = users.filter((u) => u.id !== me && !selected.has(u.id))
				this.setOptions(index, filtered)
				const group = this.participants.at(index)
				if (group.get('lookup')?.value === 'found') return
				group.patchValue({ lookup: filtered.length ? 'idle' : 'missing' })
				if (!filtered.length) {
					group.get('displayName')?.setErrors({ notFound: true })
				}
			},
			error: () => {
				this.setOptions(index, [])
				const group = this.participants.at(index)
				group.patchValue({ lookup: 'missing' })
				group.get('displayName')?.setErrors({ notFound: true })
			},
		})
	}

	private setOptions(index: number, users: UserOption[]): void {
		this.optionsByIndex.set({ ...this.optionsByIndex(), [index]: users })
	}

	/** Provider/beneficiary percent shares must total exactly 100% (10000 bps). */
	private validatePayeeShares(): string | null {
		const payees = this.participants.controls.filter((g) =>
			this.isPayeeRole(String(g.get('role')?.value || '')),
		)
		if (payees.length === 0) {
			return 'Add at least one Provider or Beneficiary who receives funds.'
		}
		const types = new Set(payees.map((g) => String(g.get('shareType')?.value || '')))
		if (types.size > 1) {
			return 'All payees must use the same share type (percent or fixed).'
		}
		if (types.has('PERCENT_BPS')) {
			const total = payees.reduce(
				(sum, g) => sum + (Number(g.get('shareValue')?.value) || 0),
				0,
			)
			if (total !== 10000) {
				return `Provider/Beneficiary shares must add up to 100% (10000 basis points). Currently ${(total / 100).toFixed(2)}%.`
			}
		}
		return null
	}

	private refreshSummaries(): void {
		const { title, amount, currency } = this.form.getRawValue()
		const amountNumber = Number(amount)
		const hasAmount = amount !== '' && !Number.isNaN(amountNumber)
		const amountText = hasAmount ? this.money.transform(amountNumber, currency) : ''
		const people = this.participants.length
		const found = this.participants.controls.filter(
			(g) => g.get('lookup')?.value === 'found',
		).length
		const total = this.conditions.length
		const required = this.conditions.controls.filter((g) => g.get('required')?.value).length
		const percentTotal = this.participants.controls
			.filter((g) =>
				this.isPayeeRole(String(g.get('role')?.value || ''))
				&& g.get('shareType')?.value === 'PERCENT_BPS',
			)
			.reduce((sum, g) => sum + (Number(g.get('shareValue')?.value) || 0), 0)

		const parts: string[] = []
		if (amountText) parts.push(amountText)
		parts.push(`${people + 1} ${people === 0 ? 'participant' : 'participants'}`)
		parts.push(`${total} ${total === 1 ? 'condition' : 'conditions'}`)
		this.summary.set(parts.join(' · '))

		this.detailsSummary.set(
			title.trim()
				? `${title.trim()}${amountText ? ` · ${amountText}` : ''}`
				: 'Title and amount are required.',
		)

		if (people === 0) {
			this.participantsSummary.set('Only you so far. Add who receives or approves funds.')
		} else {
			const verified = `${found} of ${people} selected`
			let allocation = ''
			if (percentTotal > 0) {
				const pct = (percentTotal / 100).toFixed(percentTotal % 100 === 0 ? 0 : 2)
				allocation =
					percentTotal === 10000
						? ` · ${pct}% allocated`
						: ` · ${pct}% allocated (need 100%)`
			}
			this.participantsSummary.set(`${verified}${allocation}`)
		}

		this.conditionsSummary.set(
			`${required} required · ${total - required} optional`,
		)
	}
}

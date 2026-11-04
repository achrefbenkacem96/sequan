import { Component, OnInit, computed, signal } from '@angular/core'
import { FormsModule } from '@angular/forms'
import { RouterLink } from '@angular/router'
import { MatButtonModule } from '@angular/material/button'
import { MatFormFieldModule } from '@angular/material/form-field'
import { MatInputModule } from '@angular/material/input'
import { MatSelectModule } from '@angular/material/select'
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner'
import { EscrowService } from '../../core/services/escrow.service'
import { Escrow } from '../../core/models/api.models'
import { StatusChipComponent } from '../../shared/status-chip/status-chip.component'
import { MoneyPipe } from '../../shared/money.pipe'
import { PageHeaderComponent } from '../../shared/page-header/page-header.component'
import { DatePipe } from '@angular/common'

@Component({
	selector: 'app-escrow-list',
	standalone: true,
	imports: [
		RouterLink,
		FormsModule,
		MatButtonModule,
		MatFormFieldModule,
		MatInputModule,
		MatSelectModule,
		MatProgressSpinnerModule,
		StatusChipComponent,
		MoneyPipe,
		PageHeaderComponent,
		DatePipe,
	],
	template: `
		<section class="sq-page">
			<app-page-header eyebrow="Transactions" title="Escrows" [subtitle]="summary()">
				<a sqHeadActions mat-flat-button color="primary" routerLink="/escrows/new">
					New escrow
				</a>
			</app-page-header>

			@if (loading()) {
				<div class="center"><mat-spinner diameter="36" /></div>
			} @else if (hasError()) {
				<div class="sq-empty">
					<h2>Escrows unavailable</h2>
					<p>We could not load your escrows.</p>
					<button mat-stroked-button type="button" (click)="load()">Try again</button>
				</div>
			} @else if (escrows().length === 0) {
				<div class="sq-empty">
					<strong>No escrows yet</strong>
					<p>Create a multi-party escrow to hold funds until conditions are met.</p>
					<a mat-flat-button color="primary" routerLink="/escrows/new">
						Create your first escrow
					</a>
				</div>
			} @else {
				<div class="filters sq-panel">
					<mat-form-field appearance="outline" class="grow" subscriptSizing="dynamic">
						<mat-label>Search</mat-label>
						<input
							matInput
							[ngModel]="query()"
							(ngModelChange)="query.set($event)"
							placeholder="Title or description"
						/>
					</mat-form-field>
					<mat-form-field appearance="outline" subscriptSizing="dynamic">
						<mat-label>Status</mat-label>
						<mat-select [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)">
							<mat-option value="">All statuses</mat-option>
							@for (status of statusOptions(); track status) {
								<mat-option [value]="status">{{ humanize(status) }}</mat-option>
							}
						</mat-select>
					</mat-form-field>
					@if (isFiltering()) {
						<button mat-button type="button" class="clear" (click)="clearFilters()">
							Clear
						</button>
					}
				</div>

				@if (filtered().length === 0) {
					<div class="sq-empty">
						<strong>No matches</strong>
						<p>Try another search term or clear the status filter.</p>
						<button mat-stroked-button type="button" (click)="clearFilters()">
							Clear filters
						</button>
					</div>
				} @else {
					<div class="sq-panel table-wrap">
						<table class="sq-table">
							<thead>
								<tr>
									<th>Escrow</th>
									<th>Status</th>
									<th class="num">Amount</th>
									<th>Updated</th>
								</tr>
							</thead>
							<tbody>
								@for (escrow of filtered(); track escrow.id) {
									<tr class="clickable" [routerLink]="['/escrows', escrow.id]">
										<td>
											<a [routerLink]="['/escrows', escrow.id]">{{ escrow.title }}</a>
											@if (escrow.description) {
												<span class="desc">{{ escrow.description }}</span>
											}
										</td>
										<td><app-status-chip [status]="escrow.status" /></td>
										<td class="sq-amount num">
											{{ escrow.amountTotal | money:escrow.currency }}
										</td>
										<td class="date">
											{{ escrow.updatedAt | date:'mediumDate' }}
										</td>
									</tr>
								}
							</tbody>
						</table>
					</div>
				}
			}
		</section>
	`,
	styles: [`
		.filters {
			display: grid;
			grid-template-columns: 1fr 200px auto;
			gap: 0.65rem;
			align-items: center;
			padding: 0.7rem 0.9rem;
		}
		.grow { width: 100%; }
		.clear { white-space: nowrap; }
		.table-wrap { padding: 0.2rem 0.25rem 0.35rem; overflow-x: auto; }
		.clickable { cursor: pointer; transition: background 120ms ease; }
		.clickable:hover { background: var(--sq-surface-2); }
		.num { text-align: right; }
		.desc {
			display: block;
			color: var(--sq-muted);
			font-size: 0.8rem;
			margin-top: 0.15rem;
			max-width: 34rem;
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.date { color: var(--sq-muted); font-size: 0.82rem; white-space: nowrap; }
		.center { display: grid; place-items: center; padding: 2rem 0; }
		@media (max-width: 800px) {
			.filters { grid-template-columns: 1fr; }
		}
	`],
})
export class EscrowListComponent implements OnInit {
	readonly escrows = signal<Escrow[]>([])
	readonly query = signal('')
	readonly statusFilter = signal('')
	readonly loading = signal(true)
	readonly hasError = signal(false)

	private readonly statusOrder = [
		'DRAFT',
		'PENDING_PARTICIPANTS',
		'PENDING_FUNDING',
		'FUNDED',
		'IN_PROGRESS',
		'AWAITING_RELEASE',
		'PARTIALLY_RELEASED',
		'RELEASED',
		'REFUNDED',
		'DISPUTED',
		'CANCELLED',
		'EXPIRED',
	]

	/** Only statuses that exist in the loaded list, in lifecycle order. */
	readonly statusOptions = computed(() => {
		const present = new Set(this.escrows().map((e) => e.status))
		return this.statusOrder.filter((status) => present.has(status))
	})

	readonly isFiltering = computed(
		() => this.query().trim() !== '' || this.statusFilter() !== '',
	)

	readonly filtered = computed(() => {
		const q = this.query().trim().toLowerCase()
		const status = this.statusFilter()
		return this.escrows().filter((e) => {
			const matchesStatus = !status || e.status === status
			const matchesQuery =
				!q ||
				e.title.toLowerCase().includes(q) ||
				(e.description || '').toLowerCase().includes(q)
			return matchesStatus && matchesQuery
		})
	})

	readonly summary = computed(() => {
		if (this.loading() || this.hasError()) return ''
		const total = this.escrows().length
		if (total === 0) return 'No escrows in your workspace yet.'
		const noun = total === 1 ? 'escrow' : 'escrows'
		if (this.isFiltering()) {
			return `Showing ${this.filtered().length} of ${total} ${noun}.`
		}
		const open = this.escrows().filter(
			(e) => !['RELEASED', 'REFUNDED', 'CANCELLED', 'EXPIRED'].includes(e.status),
		).length
		return `${total} ${noun} · ${open} open.`
	})

	constructor(private readonly service: EscrowService) {}

	humanize(value: string): string {
		const text = value.replaceAll('_', ' ').toLowerCase()
		return text.charAt(0).toUpperCase() + text.slice(1)
	}

	clearFilters(): void {
		this.query.set('')
		this.statusFilter.set('')
	}

	ngOnInit(): void {
		this.load()
	}

	load(): void {
		this.loading.set(true)
		this.hasError.set(false)
		this.service.list().subscribe({
			next: (escrows) => {
				this.escrows.set(escrows)
				this.loading.set(false)
			},
			error: () => {
				this.hasError.set(true)
				this.loading.set(false)
			},
		})
	}
}

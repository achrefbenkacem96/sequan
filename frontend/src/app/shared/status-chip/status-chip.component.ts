import { Component, Input } from '@angular/core'
import { NgClass } from '@angular/common'

@Component({
	selector: 'app-status-chip',
	standalone: true,
	imports: [NgClass],
	template: `
		<span class="chip" [ngClass]="tone" [attr.aria-label]="'Status: ' + label">
			<span class="dot" aria-hidden="true"></span>
			{{ label }}
		</span>
	`,
	styles: [`
		.chip {
			display: inline-flex;
			align-items: center;
			gap: 0.35rem;
			padding: 0.2rem 0.55rem;
			border-radius: 6px;
			font-size: 0.72rem;
			font-weight: 650;
			letter-spacing: 0.02em;
			text-transform: uppercase;
			border: 1px solid transparent;
			line-height: 1.2;
			white-space: nowrap;
		}
		.dot {
			width: 6px;
			height: 6px;
			border-radius: 50%;
			background: currentColor;
			opacity: 0.85;
			flex-shrink: 0;
		}
		.neutral {
			background: #f1f5f9;
			color: #334155;
			border-color: #e2e8f0;
		}
		.warning {
			background: var(--sq-warning-soft);
			color: var(--sq-warning);
			border-color: #fed7aa;
		}
		.success {
			background: var(--sq-success-soft);
			color: var(--sq-success);
			border-color: #bbf7d0;
		}
		.danger {
			background: var(--sq-danger-soft);
			color: var(--sq-danger);
			border-color: #fecaca;
		}
		.info {
			background: var(--sq-info-soft);
			color: var(--sq-info);
			border-color: #bfdbfe;
		}
	`],
})
export class StatusChipComponent {
	@Input({ required: true }) status = ''

	get label(): string {
		return this.status.replaceAll('_', ' ')
	}

	get tone(): string {
		switch (this.status) {
			case 'FUNDED':
			case 'RELEASED':
			case 'ACCEPTED':
			case 'SATISFIED':
			case 'SUCCEEDED':
			case 'COMPLETED':
				return 'success'
			case 'PENDING_FUNDING':
			case 'PENDING_PARTICIPANTS':
			case 'AWAITING_RELEASE':
			case 'PENDING':
			case 'DRAFT':
				return 'warning'
			case 'DISPUTED':
			case 'CANCELLED':
			case 'EXPIRED':
			case 'FAILED':
			case 'REJECTED':
			case 'REFUNDED':
				return 'danger'
			case 'IN_PROGRESS':
			case 'PARTIALLY_RELEASED':
				return 'info'
			default:
				return 'neutral'
		}
	}
}

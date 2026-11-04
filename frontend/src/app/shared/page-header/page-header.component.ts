import { Component, Input } from '@angular/core'

/**
 * Consistent page title block used at the top of every workspace page.
 *
 * Content projection slots:
 * - `[sqHeadBadge]`: inline element rendered beside the title (e.g. status chip).
 * - `[sqHeadMeta]`: supplementary row rendered under the subtitle.
 * - `[sqHeadActions]`: buttons or links rendered on the trailing edge.
 *
 * @example
 * <app-page-header eyebrow="Escrows" title="Create escrow" [subtitle]="summary()">
 *   <a sqHeadActions mat-stroked-button routerLink="/escrows">Cancel</a>
 * </app-page-header>
 */
@Component({
	selector: 'app-page-header',
	standalone: true,
	template: `
		<header class="head">
			<div class="main">
				@if (eyebrow) {
					<p class="eyebrow">{{ eyebrow }}</p>
				}
				<div class="title-row">
					<h1>{{ title }}</h1>
					<ng-content select="[sqHeadBadge]" />
				</div>
				@if (subtitle) {
					<p class="subtitle">{{ subtitle }}</p>
				}
				<ng-content select="[sqHeadMeta]" />
			</div>
			<div class="actions">
				<ng-content select="[sqHeadActions]" />
			</div>
		</header>
	`,
	styles: [`
		:host { display: block; }
		.head {
			display: flex;
			justify-content: space-between;
			align-items: flex-end;
			gap: 1rem;
			flex-wrap: wrap;
			padding-bottom: 0.85rem;
			border-bottom: 1px solid var(--sq-border);
		}
		.main { min-width: 0; display: grid; gap: 0.2rem; }
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
			align-items: center;
			gap: 0.75rem;
			flex-wrap: wrap;
		}
		h1 {
			margin: 0;
			font-size: clamp(1.4rem, 2.2vw, 1.75rem);
			font-weight: 700;
			line-height: 1.15;
			letter-spacing: -0.03em;
			color: var(--sq-ink);
			overflow-wrap: anywhere;
		}
		.subtitle {
			margin: 0;
			color: var(--sq-muted);
			font-size: 0.9rem;
			max-width: 44rem;
			line-height: 1.4;
		}
		.actions {
			display: flex;
			align-items: center;
			gap: 0.6rem;
			flex-wrap: wrap;
		}
		.actions:empty { display: none; }
		@media (max-width: 700px) {
			.head { align-items: flex-start; }
			.actions { width: 100%; }
		}
	`],
})
export class PageHeaderComponent {
	@Input({ required: true }) title = ''
	@Input() eyebrow = ''
	@Input() subtitle = ''
}

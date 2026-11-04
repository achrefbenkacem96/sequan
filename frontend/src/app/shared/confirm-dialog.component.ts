import { Component, Inject } from '@angular/core'
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog'
import { MatButtonModule } from '@angular/material/button'

export interface ConfirmDialogData {
	title: string
	message: string
}

@Component({
	selector: 'app-confirm-dialog',
	standalone: true,
	imports: [MatDialogModule, MatButtonModule],
	template: `
		<div class="dlg">
			<h2 mat-dialog-title>{{ data.title }}</h2>
			<mat-dialog-content>
				<p>{{ data.message }}</p>
			</mat-dialog-content>
			<mat-dialog-actions align="end">
				<button mat-stroked-button type="button" (click)="ref.close(false)">Cancel</button>
				<button mat-flat-button color="primary" type="button" (click)="ref.close(true)">Confirm</button>
			</mat-dialog-actions>
		</div>
	`,
	styles: [`
		.dlg {
			min-width: min(420px, 86vw);
			padding: 0.25rem 0.15rem;
		}
		h2 {
			font-family: var(--sq-display);
			font-weight: 400;
			font-size: 1.45rem;
			margin: 0;
			padding: 1rem 1.1rem 0.25rem !important;
		}
		p {
			margin: 0;
			color: var(--sq-muted);
			line-height: 1.45;
		}
		mat-dialog-content {
			padding: 0.5rem 1.1rem 0.75rem !important;
		}
		mat-dialog-actions {
			padding: 0.5rem 1.1rem 1rem !important;
			gap: 0.5rem;
		}
	`],
})
export class ConfirmDialogComponent {
	constructor(
		readonly ref: MatDialogRef<ConfirmDialogComponent, boolean>,
		@Inject(MAT_DIALOG_DATA) readonly data: ConfirmDialogData,
	) {}
}

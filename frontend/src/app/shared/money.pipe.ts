import { Pipe, PipeTransform } from '@angular/core'

@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
	transform(amount: string | number | null | undefined, currency = 'USD'): string {
		if (amount === null || amount === undefined || amount === '') {
			return '—'
		}
		const value = typeof amount === 'number' ? amount : Number(amount)
		if (Number.isNaN(value)) {
			return `${amount} ${currency}`
		}
		return new Intl.NumberFormat(undefined, {
			style: 'currency',
			currency,
		}).format(value)
	}
}

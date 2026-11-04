import { MoneyPipe } from './money.pipe'

describe('MoneyPipe', () => {
	const pipe = new MoneyPipe()

	it('formats decimal amounts', () => {
		expect(pipe.transform('1000.50', 'USD')).toContain('1,000.50')
	})

	it('handles empty values', () => {
		expect(pipe.transform(null, 'USD')).toBe('—')
	})
})

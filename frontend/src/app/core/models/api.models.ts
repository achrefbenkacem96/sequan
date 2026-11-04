export interface User {
	id: string
	email: string
	fullName: string
	globalRole: string
	connectOnboardingComplete: boolean
}

export interface AuthResponse {
	accessToken: string
	refreshToken: string
	expiresInMs: number
	user: User
}

export interface Participant {
	id: string
	userId: string
	email: string
	fullName: string
	role: string
	shareType: string
	shareValue: number
	/** Major-unit decimal; only set for provider/beneficiary. */
	entitledAmount: string | null
	/** Major-unit decimal still releasable to this payee. */
	remainingShare: string | null
	participationStatus: string
	approvalStatus: string
	canApproveRelease: boolean
}

export interface Condition {
	id: string
	title: string
	description: string
	sortOrder: number
	status: string
	required: boolean
}

export interface Escrow {
	id: string
	title: string
	description: string
	currency: string
	amountTotal: string
	amountFunded: string
	amountReleased: string
	amountRefunded: string
	amountAvailable: string
	status: string
	fundingDeadline: string | null
	completionDeadline: string | null
	createdBy: string
	participants: Participant[]
	conditions: Condition[]
	availableActions: string[]
	createdAt: string
	updatedAt: string
}

export interface EscrowUpdateRequest {
	title: string
	description: string
	fundingDeadline: string | null
	completionDeadline: string | null
}

export interface TimelineEvent {
	id: string
	action: string
	actorUserId: string | null
	entityType: string
	entityId: string
	metadata: Record<string, unknown> | null
	createdAt: string
}

export interface FundResponse {
	paymentId: string
	checkoutSessionId: string
	clientSecret: string
	publishableKey: string
	checkoutUrl: string
}

export interface DashboardStats {
	countsByStatus: Record<string, number>
	totalAvailable: number
	totalEscrows: number
	actionRequired: Array<{
		id: string
		title: string
		status: string
		primaryAction: string
		actions: string[]
	}>
}

export interface MyRelease {
	id: string
	escrowId: string
	escrowTitle: string
	amount: string
	currency: string
	status: string
	role: string
	createdAt: string
}

export interface NotificationItem {
	id: string
	escrowId: string | null
	type: string
	title: string
	message: string
	readAt: string | null
	createdAt: string
}

export interface ApiError {
	timestamp: string
	status: number
	code: string
	message: string
	path: string
	details?: Array<{ field: string; message: string }>
}

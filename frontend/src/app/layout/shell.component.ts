import { Component, HostListener, OnInit, computed, signal } from '@angular/core'
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router'
import { MatButtonModule } from '@angular/material/button'
import { MatIconModule } from '@angular/material/icon'
import { MatMenuModule } from '@angular/material/menu'
import { filter } from 'rxjs'
import { AuthService } from '../core/services/auth.service'
import { EscrowService } from '../core/services/escrow.service'
import { NotificationService } from '../core/services/notification.service'
import { NotificationItem } from '../core/models/api.models'

@Component({
	selector: 'app-shell',
	standalone: true,
	imports: [
		RouterOutlet,
		RouterLink,
		RouterLinkActive,
		MatButtonModule,
		MatIconModule,
		MatMenuModule,
	],
	template: `
		<div class="shell" [class.collapsed]="collapsed()" [class.mobile-open]="mobileOpen()">
			<aside class="sidebar" aria-label="Main navigation">
				<div class="brand-row">
					<a routerLink="/dashboard" class="brand" (click)="closeMobile()">
						<span class="mark">S</span>
						<span class="name">Sequan</span>
					</a>
					<button
						type="button"
						class="icon-btn desktop-only"
						(click)="collapsed.set(!collapsed())"
						[attr.aria-label]="collapsed() ? 'Expand sidebar' : 'Collapse sidebar'"
					>
						<mat-icon>{{ collapsed() ? 'chevron_right' : 'chevron_left' }}</mat-icon>
					</button>
				</div>

				<nav>
					<a
						routerLink="/dashboard"
						routerLinkActive="active"
						[routerLinkActiveOptions]="{ exact: true }"
						(click)="closeMobile()"
					>
						<mat-icon>dashboard</mat-icon>
						<span>Dashboard</span>
					</a>
					<a routerLink="/escrows" routerLinkActive="active" (click)="closeMobile()">
						<mat-icon>account_balance_wallet</mat-icon>
						<span>Escrows</span>
					</a>
					<a routerLink="/connect" routerLinkActive="active" (click)="closeMobile()">
						<mat-icon>payments</mat-icon>
						<span>Payouts</span>
					</a>
				</nav>

				<div class="sidebar-foot">
					@if (payoutStatus() !== 'unknown') {
						<a
							routerLink="/connect"
							class="status-pill"
							[class.ready]="payoutStatus() === 'ready'"
							[title]="payoutLabel()"
							(click)="closeMobile()"
						>
							<span class="dot"></span>
							<span>{{ payoutLabel() }}</span>
						</a>
					}
				</div>
			</aside>

			@if (mobileOpen()) {
				<button type="button" class="backdrop" aria-label="Close menu" (click)="closeMobile()"></button>
			}

			<div class="workspace">
				<header class="topbar">
					<div class="top-left">
						<button
							type="button"
							class="icon-btn mobile-only"
							(click)="mobileOpen.set(true)"
							aria-label="Open menu"
						>
							<mat-icon>menu</mat-icon>
						</button>
						<nav class="crumbs" aria-label="Breadcrumb">
							@for (crumb of crumbs(); track crumb.label; let last = $last) {
								@if (last || !crumb.link) {
									<span class="crumb" [class.current]="last" [attr.aria-current]="last ? 'page' : null">
										{{ crumb.label }}
									</span>
								} @else {
									<a class="crumb" [routerLink]="crumb.link">{{ crumb.label }}</a>
								}
								@if (!last) {
									<mat-icon class="sep" aria-hidden="true">chevron_right</mat-icon>
								}
							}
						</nav>
					</div>

					<div class="top-right">
						<button
							type="button"
							class="icon-btn bell"
							[matMenuTriggerFor]="notifMenu"
							aria-label="Notifications"
						>
							<mat-icon>notifications_none</mat-icon>
							@if (unreadCount() > 0) {
								<span class="badge">{{ unreadCount() }}</span>
							}
						</button>
						<mat-menu #notifMenu="matMenu" class="sq-menu">
							@if (notifications().length === 0) {
								<div class="menu-empty">
									<strong>No notifications</strong>
									<span>Invites and release updates will show up here.</span>
								</div>
							} @else {
								@for (n of notifications(); track n.id) {
									<button
										type="button"
										mat-menu-item
										(click)="openNotification(n)"
										[class.unread]="!n.readAt"
									>
										<div class="notif">
											<strong>{{ n.title }}</strong>
											<span>{{ n.message }}</span>
										</div>
									</button>
								}
							}
						</mat-menu>

						<button type="button" class="user-chip" [matMenuTriggerFor]="userMenu">
							<span class="avatar">{{ initials() }}</span>
							<span class="user-meta">
								<strong>{{ auth.user()?.fullName }}</strong>
								<small>{{ auth.user()?.email }}</small>
							</span>
							<mat-icon>expand_more</mat-icon>
						</button>
						<mat-menu #userMenu="matMenu">
							<button type="button" mat-menu-item routerLink="/connect">Payout settings</button>
							<button type="button" mat-menu-item (click)="auth.logout()">Sign out</button>
						</mat-menu>
					</div>
				</header>

				<main class="content">
					<router-outlet />
				</main>
			</div>
		</div>
	`,
	styles: [`
		.shell {
			min-height: 100vh;
			display: grid;
			grid-template-columns: var(--sq-sidebar) 1fr;
			background: var(--sq-canvas);
		}
		.shell.collapsed {
			grid-template-columns: var(--sq-sidebar-collapsed) 1fr;
		}

		.sidebar {
			position: sticky;
			top: 0;
			height: 100vh;
			display: flex;
			flex-direction: column;
			gap: 1rem;
			padding: 1rem 0.75rem;
			background: var(--sq-ink);
			color: #e2e8f0;
			z-index: 30;
		}
		.brand-row {
			display: flex;
			align-items: center;
			justify-content: space-between;
			padding: 0.25rem 0.4rem 0.75rem;
		}
		.brand {
			display: flex;
			align-items: center;
			gap: 0.65rem;
			text-decoration: none;
			color: inherit;
			min-width: 0;
		}
		.mark {
			width: 32px;
			height: 32px;
			border-radius: 8px;
			display: grid;
			place-items: center;
			background: var(--sq-accent);
			color: white;
			font-weight: 700;
			flex-shrink: 0;
		}
		.name {
			font-family: var(--sq-display);
			font-size: 1.45rem;
			line-height: 1;
			white-space: nowrap;
		}
		.shell.collapsed .name,
		.shell.collapsed .status-pill span:last-child,
		.shell.collapsed .sidebar nav a span {
			display: none;
		}
		.shell.collapsed .sidebar nav a,
		.shell.collapsed .status-pill {
			justify-content: center;
			padding-inline: 0.65rem;
		}

		.sidebar nav {
			display: grid;
			align-content: start;
			gap: 0.25rem;
			flex: 1;
		}
		.sidebar nav a {
			display: flex;
			align-items: center;
			gap: 0.7rem;
			padding: 0.65rem 0.75rem;
			border-radius: 8px;
			color: #94a3b8;
			text-decoration: none;
			font-weight: 500;
			font-size: 0.92rem;
			transition: background 120ms ease, color 120ms ease;
		}
		.sidebar nav a mat-icon { font-size: 20px; width: 20px; height: 20px; }
		.sidebar nav a:hover { background: rgba(255,255,255,0.06); color: #f8fafc; }
		.sidebar nav a.active {
			background: rgba(15, 118, 110, 0.22);
			color: #f0fdfa;
		}

		.sidebar-foot { padding: 0.5rem 0.4rem; }
		.status-pill {
			display: flex;
			align-items: center;
			gap: 0.5rem;
			padding: 0.55rem 0.65rem;
			border-radius: 8px;
			background: rgba(255,255,255,0.04);
			border: 1px solid rgba(255,255,255,0.06);
			font-size: 0.78rem;
			font-weight: 500;
			color: #cbd5e1;
			text-decoration: none;
			transition: background 120ms ease;
		}
		.status-pill:hover { background: rgba(255,255,255,0.08); }
		.status-pill span:last-child {
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.dot {
			width: 7px;
			height: 7px;
			border-radius: 50%;
			background: #fbbf24;
			flex-shrink: 0;
		}
		.status-pill.ready .dot { background: #34d399; }

		.workspace { min-width: 0; display: flex; flex-direction: column; }
		.topbar {
			position: sticky;
			top: 0;
			z-index: 20;
			height: var(--sq-topbar);
			display: flex;
			align-items: center;
			justify-content: space-between;
			gap: 1rem;
			padding: 0 1.25rem;
			background: rgba(244, 246, 249, 0.9);
			backdrop-filter: blur(10px);
			border-bottom: 1px solid var(--sq-border);
		}
		.top-left, .top-right {
			display: flex;
			align-items: center;
			gap: 0.65rem;
			min-width: 0;
		}
		.crumbs {
			display: flex;
			align-items: center;
			gap: 0.15rem;
			min-width: 0;
			font-size: 0.88rem;
		}
		.crumb {
			color: var(--sq-muted);
			text-decoration: none;
			font-weight: 500;
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
			max-width: 220px;
		}
		a.crumb:hover { color: var(--sq-accent); }
		.crumb.current { color: var(--sq-ink); font-weight: 600; }
		.sep {
			color: var(--sq-border-strong);
			font-size: 18px;
			width: 18px;
			height: 18px;
			flex-shrink: 0;
		}
		.icon-btn {
			border: 0;
			background: transparent;
			color: var(--sq-ink-soft);
			width: 36px;
			height: 36px;
			border-radius: 8px;
			display: grid;
			place-items: center;
			cursor: pointer;
		}
		.icon-btn:hover { background: rgba(15, 23, 42, 0.05); }
		.sidebar .icon-btn { color: #94a3b8; }
		.bell { position: relative; }
		.badge {
			position: absolute;
			top: 4px;
			right: 4px;
			min-width: 16px;
			height: 16px;
			padding: 0 4px;
			border-radius: 999px;
			background: var(--sq-danger);
			color: white;
			font-size: 0.65rem;
			font-weight: 700;
			display: grid;
			place-items: center;
		}
		.user-chip {
			display: flex;
			align-items: center;
			gap: 0.55rem;
			border: 1px solid var(--sq-border);
			background: var(--sq-surface);
			border-radius: 999px;
			padding: 0.2rem 0.55rem 0.2rem 0.2rem;
			cursor: pointer;
			max-width: 240px;
		}
		.avatar {
			width: 30px;
			height: 30px;
			border-radius: 50%;
			background: var(--sq-accent-soft);
			color: var(--sq-accent);
			display: grid;
			place-items: center;
			font-size: 0.75rem;
			font-weight: 700;
			flex-shrink: 0;
		}
		.user-meta {
			display: grid;
			text-align: left;
			min-width: 0;
		}
		.user-meta strong {
			font-size: 0.8rem;
			line-height: 1.1;
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.user-meta small {
			color: var(--sq-muted);
			font-size: 0.7rem;
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.user-chip mat-icon { color: var(--sq-muted); font-size: 18px; width: 18px; height: 18px; }

		.content {
			padding: 1.15rem 1.35rem 1.75rem;
			max-width: 1120px;
			width: 100%;
			margin: 0 auto;
		}

		.menu-empty {
			display: grid;
			gap: 0.2rem;
			padding: 0.85rem 1rem;
			color: var(--sq-muted);
			font-size: 0.82rem;
			line-height: 1.4;
			max-width: 260px;
		}
		.menu-empty strong {
			color: var(--sq-ink);
			font-size: 0.88rem;
			font-weight: 600;
		}
		.notif {
			display: grid;
			gap: 0.15rem;
			max-width: 280px;
			white-space: normal;
		}
		.notif strong { font-size: 0.85rem; }
		.notif span { color: var(--sq-muted); font-size: 0.78rem; }
		.unread strong { color: var(--sq-accent); }

		.backdrop {
			display: none;
		}
		.mobile-only { display: none; }
		.desktop-only { display: grid; }

		@media (max-width: 900px) {
			.shell, .shell.collapsed {
				grid-template-columns: 1fr;
			}
			.sidebar {
				position: fixed;
				inset: 0 auto 0 0;
				width: min(280px, 86vw);
				transform: translateX(-105%);
				transition: transform 180ms ease;
			}
			.shell.mobile-open .sidebar {
				transform: translateX(0);
			}
			.backdrop {
				display: block;
				position: fixed;
				inset: 0;
				border: 0;
				background: rgba(15, 23, 42, 0.45);
				z-index: 25;
			}
			.mobile-only { display: grid; }
			.desktop-only { display: none; }
			.user-meta { display: none; }
			.content { padding: 1rem 1rem 1.5rem; }
			.shell.collapsed .name,
			.shell.collapsed .status-pill span:last-child,
			.shell.collapsed .sidebar nav a span {
				display: inline;
			}
			.shell.collapsed .sidebar nav a,
			.shell.collapsed .status-pill {
				justify-content: flex-start;
				padding-inline: 0.75rem;
			}
			.crumb { max-width: 140px; }
		}
	`],
})
export class ShellComponent implements OnInit {
	readonly collapsed = signal(false)
	readonly mobileOpen = signal(false)
	readonly currentUrl = signal('/dashboard')
	readonly notifications = signal<NotificationItem[]>([])
	readonly payoutStatus = signal<PayoutStatus>('unknown')

	readonly unreadCount = computed(
		() => this.notifications().filter((n) => !n.readAt).length,
	)

	readonly crumbs = computed(() => this.crumbsFromUrl(this.currentUrl()))

	constructor(
		readonly auth: AuthService,
		private readonly router: Router,
		private readonly notificationsApi: NotificationService,
		private readonly escrows: EscrowService,
	) {
		this.router.events
			.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
			.subscribe((e) => {
				this.currentUrl.set(e.urlAfterRedirects)
				this.closeMobile()
				if (e.urlAfterRedirects.startsWith('/connect')) {
					this.loadPayoutStatus()
				}
			})
	}

	ngOnInit(): void {
		this.currentUrl.set(this.router.url)
		this.notificationsApi.list().subscribe({
			next: (items) => this.notifications.set(items.slice(0, 8)),
			error: () => this.notifications.set([]),
		})
		this.loadPayoutStatus()
	}

	payoutLabel(): string {
		return this.payoutStatus() === 'ready'
			? 'Payouts ready'
			: 'Payout setup needed'
	}

	private loadPayoutStatus(): void {
		this.escrows.connectStatus().subscribe({
			next: (status) => this.payoutStatus.set(status.complete ? 'ready' : 'pending'),
			error: () => this.payoutStatus.set('unknown'),
		})
	}

	@HostListener('window:resize')
	onResize(): void {
		if (window.innerWidth > 900) {
			this.mobileOpen.set(false)
		}
	}

	closeMobile(): void {
		this.mobileOpen.set(false)
	}

	initials(): string {
		const name = this.auth.user()?.fullName || 'U'
		return name
			.split(/\s+/)
			.slice(0, 2)
			.map((p) => p[0]?.toUpperCase() || '')
			.join('')
	}

	openNotification(n: NotificationItem): void {
		if (!n.readAt) {
			this.notificationsApi.markRead(n.id).subscribe({
				next: () => {
					this.notifications.update((list) =>
						list.map((item) =>
							item.id === n.id ? { ...item, readAt: new Date().toISOString() } : item,
						),
					)
				},
			})
		}
		if (n.escrowId) {
			this.router.navigate(['/escrows', n.escrowId])
		}
	}

	private crumbsFromUrl(url: string): Crumb[] {
		const path = url.split('?')[0]
		const home: Crumb = { label: 'Dashboard', link: '/dashboard' }
		if (path.startsWith('/escrows/new')) {
			return [home, { label: 'Escrows', link: '/escrows' }, { label: 'New' }]
		}
		if (path.startsWith('/escrows/')) {
			return [home, { label: 'Escrows', link: '/escrows' }, { label: 'Detail' }]
		}
		if (path.startsWith('/escrows')) {
			return [home, { label: 'Escrows' }]
		}
		if (path.startsWith('/connect')) {
			return [home, { label: 'Payouts' }]
		}
		return [home]
	}
}

type PayoutStatus = 'unknown' | 'ready' | 'pending'

interface Crumb {
	label: string
	link?: string
}

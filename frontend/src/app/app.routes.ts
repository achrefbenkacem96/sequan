import { Routes } from '@angular/router'
import { authGuard, guestGuard } from './core/guards/auth.guard'
import { ShellComponent } from './layout/shell.component'
import { LoginComponent } from './features/auth/login.component'
import { RegisterComponent } from './features/auth/register.component'
import { DashboardComponent } from './features/dashboard/dashboard.component'
import { EscrowListComponent } from './features/escrows/escrow-list.component'
import { EscrowCreateComponent } from './features/escrows/escrow-create.component'
import { EscrowDetailComponent } from './features/escrows/escrow-detail.component'
import { ConnectComponent } from './features/connect/connect.component'

export const routes: Routes = [
	{ path: 'login', component: LoginComponent, canActivate: [guestGuard] },
	{ path: 'register', component: RegisterComponent, canActivate: [guestGuard] },
	{
		path: '',
		component: ShellComponent,
		canActivate: [authGuard],
		children: [
			{ path: '', pathMatch: 'full', redirectTo: 'dashboard' },
			{ path: 'dashboard', component: DashboardComponent },
			{ path: 'escrows', component: EscrowListComponent },
			{ path: 'escrows/new', component: EscrowCreateComponent },
			{ path: 'escrows/:id', component: EscrowDetailComponent },
			{ path: 'connect', component: ConnectComponent },
			{ path: 'connect/return', component: ConnectComponent },
			{ path: 'connect/refresh', component: ConnectComponent },
		],
	},
	{ path: '**', redirectTo: 'dashboard' },
]

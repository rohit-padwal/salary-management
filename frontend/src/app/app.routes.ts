import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell';
import { sessionGuard } from './auth/session-guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in | Salary Management',
    loadComponent: () => import('./auth/login-page').then((page) => page.LoginPage),
  },
  {
    path: '',
    component: AppShell,
    canActivateChild: [sessionGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'employees' },
      {
        path: 'employees',
        title: 'Employees | Salary Management',
        loadComponent: () =>
          import('./employees/employees-page').then((page) => page.EmployeesPage),
      },
      {
        path: 'analytics',
        title: 'Compensation analytics | Salary Management',
        loadComponent: () =>
          import('./analytics/analytics-page').then((page) => page.AnalyticsPage),
      },
      {
        path: '**',
        title: 'Page not found | Salary Management',
        loadComponent: () => import('./not-found/not-found-page').then((page) => page.NotFoundPage),
      },
    ],
  },
];

import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell';

export const routes: Routes = [
  {
    path: '',
    component: AppShell,
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

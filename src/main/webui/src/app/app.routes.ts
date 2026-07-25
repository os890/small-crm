/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Routes } from '@angular/router';
import { adminGuard, anonymousGuard, authGuard, passwordChangeGuard } from './core/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'change-password',
    canActivate: [passwordChangeGuard],
    loadComponent: () =>
      import('./features/login/change-password.page').then((m) => m.ChangePasswordPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'contacts',
        loadComponent: () =>
          import('./features/contacts/contacts.page').then((m) => m.ContactsPage),
      },
      {
        path: 'contacts/:id',
        loadComponent: () =>
          import('./features/contacts/contact-detail.page').then((m) => m.ContactDetailPage),
      },
      {
        path: 'companies',
        loadComponent: () =>
          import('./features/companies/companies.page').then((m) => m.CompaniesPage),
      },
      {
        path: 'deals',
        loadComponent: () => import('./features/deals/deals.page').then((m) => m.DealsPage),
      },
      {
        path: 'tasks',
        loadComponent: () => import('./features/tasks/tasks.page').then((m) => m.TasksPage),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/calendar/calendar.page').then((m) => m.CalendarPage),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

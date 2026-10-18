import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-employees-page',
  templateUrl: './employees-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeesPage {}

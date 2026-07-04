import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './utils/theme/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'wacchat';

  constructor(private themeService: ThemeService) {}
}

import { provideHttpClient } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';

import { BookingPageComponent } from './app/booking-page.component';

bootstrapApplication(BookingPageComponent, {
  providers: [provideHttpClient()],
}).catch((err) => console.error(err));
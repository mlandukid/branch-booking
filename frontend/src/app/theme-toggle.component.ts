import { Component, inject } from '@angular/core';

import { ThemeService } from './theme.service';

/**
 * A sun and moon switch. In the day the sun sits on a sky with clouds, at night the moon sits on a
 * starry sky, and the knob slides between them. It is a real switch for keyboards and screen readers.
 */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  template: `
    <button
      type="button"
      class="switch"
      role="switch"
      aria-label="Dark mode"
      [attr.aria-checked]="dark()"
      [title]="dark() ? 'Switch to day mode' : 'Switch to night mode'"
      (click)="toggle()"
    >
      <span class="sky" aria-hidden="true">
        <span class="cloud one"></span>
        <span class="cloud two"></span>
      </span>
      <span class="night" aria-hidden="true">
        <span class="star s1"></span>
        <span class="star s2"></span>
        <span class="star s3"></span>
        <span class="star s4"></span>
        <span class="star s5"></span>
        <span class="star s6"></span>
      </span>
      <span class="knob" aria-hidden="true">
        <span class="sun"></span>
        <span class="moon">
          <span class="crater k1"></span>
          <span class="crater k2"></span>
          <span class="crater k3"></span>
        </span>
      </span>
    </button>
  `,
  styles: `
    :host {
      display: inline-block;
    }
    .switch {
      position: relative;
      display: block;
      width: 78px;
      height: 36px;
      padding: 0;
      overflow: hidden;
      border: 1.5px solid var(--ink);
      border-radius: 999px;
      background: none;
      cursor: pointer;
      transition: transform 0.15s ease;
    }
    .switch:hover {
      transform: scale(1.04);
    }
    .switch:focus-visible {
      outline: 3px solid var(--forest);
      outline-offset: 3px;
    }
    .sky,
    .night {
      position: absolute;
      inset: 0;
      transition: opacity 0.45s ease;
    }
    .sky {
      background: linear-gradient(180deg, #6f7fe0 0%, #aab2ee 55%, #f3e27a 100%);
    }
    .night {
      background: linear-gradient(180deg, #171d33 0%, #3a4670 100%);
      opacity: 0;
    }
    .switch[aria-checked='true'] .sky {
      opacity: 0;
    }
    .switch[aria-checked='true'] .night {
      opacity: 1;
    }
    .cloud {
      position: absolute;
      border-radius: 999px;
      background: linear-gradient(180deg, #ffffff, #dde1f6);
    }
    .cloud::before {
      content: '';
      position: absolute;
      border-radius: 50%;
      background: #f4f6ff;
    }
    .cloud.one {
      top: 7px;
      right: 10px;
      width: 22px;
      height: 10px;
    }
    .cloud.one::before {
      top: -6px;
      left: 5px;
      width: 11px;
      height: 11px;
    }
    .cloud.two {
      top: 16px;
      right: 4px;
      width: 28px;
      height: 12px;
    }
    .cloud.two::before {
      top: -7px;
      left: 8px;
      width: 13px;
      height: 13px;
    }
    .star {
      position: absolute;
      width: 2px;
      height: 2px;
      border-radius: 50%;
      background: #ffffff;
    }
    .s1 { top: 8px; left: 9px; }
    .s2 { top: 22px; left: 17px; }
    .s3 { top: 11px; left: 27px; width: 3px; height: 3px; }
    .s4 { top: 24px; left: 36px; }
    .s5 { top: 15px; left: 14px; opacity: 0.7; }
    .s6 { top: 8px; left: 42px; opacity: 0.8; }
    .knob {
      position: absolute;
      top: 3px;
      left: 3px;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      transition: transform 0.45s cubic-bezier(0.5, 0.1, 0.2, 1);
    }
    .switch[aria-checked='true'] .knob {
      transform: translateX(43px);
    }
    .sun,
    .moon {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      transition: opacity 0.35s ease;
    }
    .sun {
      background: radial-gradient(circle at 35% 30%, #fff56e, #ffe100 60%, #f2c800);
      box-shadow: 0 0 0 4px rgba(255, 225, 0, 0.22), 0 0 0 8px rgba(255, 225, 0, 0.12);
    }
    .moon {
      background: radial-gradient(circle at 35% 30%, #f0f2fc, #cbd0ea 70%);
      box-shadow: 0 0 0 4px rgba(203, 208, 234, 0.22), 0 0 0 8px rgba(203, 208, 234, 0.1);
      opacity: 0;
    }
    .switch[aria-checked='true'] .sun {
      opacity: 0;
    }
    .switch[aria-checked='true'] .moon {
      opacity: 1;
    }
    .crater {
      position: absolute;
      border-radius: 50%;
      background: #a9a7d8;
    }
    .k1 { top: 5px; left: 13px; width: 7px; height: 7px; }
    .k2 { top: 15px; left: 5px; width: 4px; height: 4px; }
    .k3 { top: 16px; left: 14px; width: 6px; height: 6px; }
  `,
})
export class ThemeToggleComponent {
  private readonly theme = inject(ThemeService);

  protected readonly dark = this.theme.isDark;

  protected toggle(): void {
    this.theme.toggle();
  }
}
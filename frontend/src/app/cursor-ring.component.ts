import { Component, DestroyRef, ElementRef, afterNextRender, inject } from '@angular/core';

/**
 * A soft ring that trails the pointer and grows over interactive elements.
 * The real cursor stays visible. The ring is disabled on touch devices and for people who
 * have asked for reduced motion. It moves the element directly instead of through Angular,
 * so it never triggers change detection.
 */
@Component({
  selector: 'app-cursor-ring',
  standalone: true,
  template: '<div class="ring"><div class="ring-inner"></div></div>',
  styles: `
    :host {
      pointer-events: none;
    }
    .ring {
      position: fixed;
      top: 0;
      left: 0;
      z-index: 9999;
      width: 38px;
      height: 38px;
      margin: -19px 0 0 -19px;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.25s ease;
      will-change: transform;
    }
    .ring-inner {
      width: 100%;
      height: 100%;
      border: 2px solid rgba(10, 95, 216, 0.55);
      border-radius: 50%;
      background: rgba(10, 95, 216, 0.06);
      transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;
    }
    :host(.on) .ring {
      opacity: 1;
    }
    :host(.hover) .ring-inner {
      transform: scale(1.6);
      border-color: rgba(0, 179, 164, 0.75);
      background: rgba(0, 179, 164, 0.12);
    }
    :host(.press) .ring-inner {
      transform: scale(0.8);
    }
  `,
})
export class CursorRingComponent {
  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const ring = host.querySelector<HTMLElement>('.ring');
      if (!finePointer || reducedMotion || !ring) {
        host.style.display = 'none';
        return;
      }

      let targetX = 0;
      let targetY = 0;
      let x = 0;
      let y = 0;
      let frame = 0;
      let running = false;
      let active = false;

      const tick = () => {
        x += (targetX - x) * 0.18;
        y += (targetY - y) * 0.18;
        ring.style.transform = `translate3d(${x}px, ${y}px, 0)`;
        if (Math.abs(targetX - x) < 0.1 && Math.abs(targetY - y) < 0.1) {
          running = false;
          return;
        }
        frame = requestAnimationFrame(tick);
      };

      const start = () => {
        if (!running) {
          running = true;
          frame = requestAnimationFrame(tick);
        }
      };

      const move = (event: PointerEvent) => {
        targetX = event.clientX;
        targetY = event.clientY;
        if (!active) {
          x = targetX;
          y = targetY;
          active = true;
          host.classList.add('on');
        }
        const element = (event.target as Element | null)?.closest('button, a, select, input, label');
        const usable = !!element && !(element as HTMLButtonElement).disabled;
        host.classList.toggle('hover', usable);
        start();
      };
      const press = () => host.classList.add('press');
      const release = () => host.classList.remove('press');
      const leave = () => {
        active = false;
        host.classList.remove('on', 'hover', 'press');
      };

      document.addEventListener('pointermove', move, { passive: true });
      document.addEventListener('pointerdown', press);
      document.addEventListener('pointerup', release);
      document.documentElement.addEventListener('mouseleave', leave);

      destroyRef.onDestroy(() => {
        cancelAnimationFrame(frame);
        document.removeEventListener('pointermove', move);
        document.removeEventListener('pointerdown', press);
        document.removeEventListener('pointerup', release);
        document.documentElement.removeEventListener('mouseleave', leave);
      });
    });
  }
}
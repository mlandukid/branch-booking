import { Component, computed, input } from '@angular/core';

export interface GrowLetter {
  char: string;
  /** Font size in em, relative to the surrounding text. */
  size: number;
  /** Entrance delay in milliseconds. */
  delay: number;
}

/** Lays the text out word by word, with every letter a little bigger than the one before it. */
export function layoutLetters(text: string, min = 0.62, max = 1.3): GrowLetter[][] {
  const words = text.trim().split(/\s+/).filter(Boolean);
  const total = words.reduce((sum, word) => sum + [...word].length, 0);
  let index = 0;
  return words.map((word) =>
    [...word].map((char) => {
      const t = total > 1 ? index / (total - 1) : 1;
      const letter = { char, size: min + (max - min) * Math.pow(t, 1.35), delay: index * 38 };
      index += 1;
      return letter;
    }),
  );
}

/**
 * Text whose letters grow from small to large, with a pop-in. Screen readers get the plain text.
 * Words stay together so a line never breaks in the middle of a word.
 */
@Component({
  selector: 'app-grow-text',
  standalone: true,
  template: `
    <span class="sr-only">{{ label() ?? text() }}</span>
    <span aria-hidden="true">
      @for (word of words(); track $index) {
        <span class="word" [class.long]="word.length > 12">
          @for (letter of word; track $index) {
            <span
              class="letter"
              [class.bang]="letter.char === '!'"
              [style.font-size.em]="letter.size"
              [style.animation-delay.ms]="letter.delay"
              >{{ letter.char }}</span
            >
          }
        </span>
        {{ ' ' }}
      }
    </span>
  `,
  styles: `
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: -1px;
      padding: 0;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
      border: 0;
    }
    .word {
      display: inline-block;
      white-space: nowrap;
    }
    .letter {
      display: inline-block;
      line-height: 1;
      transform-origin: 50% 100%;
      animation: pop 0.5s cubic-bezier(0.2, 1.4, 0.4, 1) both;
    }
    .word.long {
      display: inline;
      white-space: normal;
    }
    .bang {
      color: var(--saffron, #e8a317);
    }
    @keyframes pop {
      from {
        opacity: 0;
        transform: translateY(0.5em) scale(0.6);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }
  `,
})
export class GrowTextComponent {
  readonly text = input.required<string>();
  /** What screen readers announce, when the displayed text is stretched or decorated. */
  readonly label = input<string>();
  protected readonly words = computed(() => layoutLetters(this.text()));
}
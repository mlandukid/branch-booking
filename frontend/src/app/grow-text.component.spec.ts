import { layoutLetters } from './grow-text.component';

describe('layoutLetters', () => {
  it('splits the text into words', () => {
    const words = layoutLetters('Skip the queue');

    expect(words.length).toBe(3);
    expect(words[0].map((letter) => letter.char).join('')).toBe('Skip');
  });

  it('grows from the smallest to the largest size', () => {
    const letters = layoutLetters('Skip the queue', 0.5, 1.5).flat();

    expect(letters[0].size).toBeCloseTo(0.5, 5);
    expect(letters[letters.length - 1].size).toBeCloseTo(1.5, 5);
  });

  it('never shrinks along the way', () => {
    const sizes = layoutLetters('You are booked!!!').flat().map((letter) => letter.size);

    for (let i = 1; i < sizes.length; i++) {
      expect(sizes[i]).toBeGreaterThanOrEqual(sizes[i - 1]);
    }
  });

  it('gives a single letter the largest size', () => {
    expect(layoutLetters('A', 0.5, 1.5)[0][0].size).toBeCloseTo(1.5, 5);
  });

  it('returns nothing for blank text', () => {
    expect(layoutLetters('   ')).toEqual([]);
  });
});
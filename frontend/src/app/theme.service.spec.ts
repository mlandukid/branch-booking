import { resolveTheme } from './theme.service';

describe('resolveTheme', () => {
  it('uses an explicit dark choice even when the device is light', () => {
    expect(resolveTheme('dark', false)).toBe('dark');
  });

  it('uses an explicit light choice even when the device is dark', () => {
    expect(resolveTheme('light', true)).toBe('light');
  });

  it('follows the device when nothing was chosen', () => {
    expect(resolveTheme(null, true)).toBe('dark');
    expect(resolveTheme(null, false)).toBe('light');
  });

  it('ignores a stored value it does not recognise', () => {
    expect(resolveTheme('purple', true)).toBe('dark');
    expect(resolveTheme('purple', false)).toBe('light');
  });
});
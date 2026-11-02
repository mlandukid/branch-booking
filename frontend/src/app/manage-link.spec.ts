import { manageHash, parseManageHash } from './manage-link';

const reference = '123e4567-e89b-12d3-a456-426614174000';
const token = 'A'.repeat(43);

describe('parseManageHash', () => {
  it('reads the reference and the token from the link', () => {
    expect(parseManageHash(`#manage=${reference}.${token}`)).toEqual({ reference, token });
  });

  it('round-trips with manageHash', () => {
    expect(parseManageHash('#' + manageHash(reference, token))).toEqual({ reference, token });
  });

  it('rejects a token of the wrong length', () => {
    expect(parseManageHash(`#manage=${reference}.${'A'.repeat(42)}`)).toBeNull();
  });

  it('rejects a reference of the wrong length', () => {
    expect(parseManageHash(`#manage=${reference}0.${token}`)).toBeNull();
  });

  it('ignores any other hash', () => {
    expect(parseManageHash('#something-else')).toBeNull();
    expect(parseManageHash('')).toBeNull();
  });
});
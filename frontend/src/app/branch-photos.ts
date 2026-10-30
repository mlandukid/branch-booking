/** A photo for a branch. Branches without an entry here simply have no photo tile. */
export interface BranchPhoto {
  src: string;
  alt: string;
  caption: string;
  /** CSS object-position, so the interesting part of a tall photo stays in view when it is cropped. */
  focus: string;
  /** Position in the photo panel. */
  order: number;
  /** Optional photographer credit, shown on the tile. */
  credit?: string;
}

/** Keyed by lower-case branch name. */
export const BRANCH_PHOTOS: Record<string, BranchPhoto> = {
  soweto: {
    src: 'branches/soweto.webp',
    alt: 'Minibus taxis queueing at traffic lights on a busy Gauteng street',
    caption: 'Minibus taxis, Gauteng',
    focus: '50% 65%',
    order: 1,
  },
  sandton: {
    src: 'branches/sandton.webp',
    alt: 'The clock tower and the Nelson Mandela statue at Nelson Mandela Square',
    caption: 'Nelson Mandela Square',
    focus: '40% 45%',
    order: 2,
  },
  rosebank: {
    src: 'branches/rosebank.webp',
    alt: 'A curved concrete wall and a wooden staircase with a small bronze figure on it',
    caption: 'Rosebank, Johannesburg',
    focus: '50% 75%',
    order: 3,
  },
};
export interface ManageLink {
  reference: string;
  token: string;
}

/** The link in the confirmation email: #manage=<booking reference>.<private token> */
const PATTERN = /^#manage=([0-9a-fA-F-]{36})\.([A-Za-z0-9_-]{43})$/;

export function parseManageHash(hash: string): ManageLink | null {
  const match = PATTERN.exec(hash);
  return match ? { reference: match[1], token: match[2] } : null;
}

export function manageHash(reference: string, token: string): string {
  return `manage=${reference}.${token}`;
}
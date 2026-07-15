/** Error whose message was authored locally and is safe to show verbatim. */
export class UserFacingError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UserFacingError';
  }
}

export function getUserFacingErrorMessage(error: unknown, fallback: string): string {
  return error instanceof UserFacingError && error.message ? error.message : fallback;
}

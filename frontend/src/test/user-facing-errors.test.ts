import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { getUserFacingErrorMessage, UserFacingError } from '../errors/user-facing-error';

describe('mensagens de erro seguras para a interface', () => {
  it('nunca reflete a mensagem recebida em um ApiError', () => {
    const error = new ApiError(
      502,
      'UPSTREAM_FAILURE',
      '<html>proxy failure: database.internal:5432</html>',
    );

    expect(getUserFacingErrorMessage(error, 'We could not complete the action.'))
      .toBe('We could not complete the action.');
  });

  it('preserva apenas mensagens locais marcadas explicitamente como seguras', () => {
    const error = new UserFacingError('Connect your Blaze account to continue.');

    expect(getUserFacingErrorMessage(error, 'We could not complete the action.'))
      .toBe('Connect your Blaze account to continue.');
  });

  it('does not trust a generic Error message either', () => {
    const error = new Error('filesystem path C:\\private\\credentials.txt');

    expect(getUserFacingErrorMessage(error, 'We could not complete the action.'))
      .toBe('We could not complete the action.');
  });
});

export function toSafeOAuthUrl(value: string): string {
  const target = new URL(value, window.location.origin);
  const isSecureRemote = target.protocol === 'https:';
  const isSameOriginLocal = target.origin === window.location.origin
    && (target.protocol === 'http:' || target.protocol === 'https:');

  if (!isSecureRemote && !isSameOriginLocal) {
    throw new Error('O endereço de autenticação recebido não é seguro.');
  }

  return target.toString();
}

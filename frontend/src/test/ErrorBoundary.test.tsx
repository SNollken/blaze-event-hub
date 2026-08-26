import { type ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from '../components/ErrorBoundary';

function Boom(): ReactNode {
  throw new Error('boom controlado para teste');
}

describe('ErrorBoundary', () => {
  const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

  afterEach(() => {
    errorSpy.mockClear();
  });

  it('renderiza os filhos quando nao ha erro', () => {
    render(
      <ErrorBoundary>
        <p>conteudo normal</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText('conteudo normal')).toBeInTheDocument();
  });

  it('quando um filho lanca erro, mostra o fallback amigavel', () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Algo deu errado ao carregar esta tela.')).toBeInTheDocument();
    expect(screen.getByText('Verifique o backend e tente novamente.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recarregar página' })).toBeInTheDocument();
  });

  it('botao do fallback recarrega a pagina', () => {
    const reload = vi.fn();
    const original = window.location;
    // jsdom nao permite spyOn/redefine de location.reload; substituir o objeto
    // inteiro (delete + assign) e suportado nesta versao do jsdom (verificado).
    // @ts-expect-error teste: substituicao temporaria de window.location
    delete window.location;
    // @ts-expect-error teste: substituicao temporaria de window.location
    window.location = { ...original, reload };
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Recarregar página' }));
    expect(reload).toHaveBeenCalledTimes(1);
    // @ts-expect-error teste: restauracao de window.location
    window.location = original;
  });

  it('fallback NAO aparece quando os filhos renderizam bem', () => {
    render(
      <ErrorBoundary>
        <p>ok</p>
      </ErrorBoundary>,
    );
    expect(screen.queryByText('Algo deu errado ao carregar esta tela.')).not.toBeInTheDocument();
  });
});

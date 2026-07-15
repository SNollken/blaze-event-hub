import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nContext';
import HelpSupport from '../pages/HelpSupport';

function renderHelp(lang: 'en' | 'pt-BR' = 'en') {
  localStorage.setItem('blaze-event-hub:language', lang);
  return render(
    <I18nProvider>
      <MemoryRouter><HelpSupport /></MemoryRouter>
    </I18nProvider>,
  );
}

describe('central de ajuda', () => {
  beforeEach(() => {
    vi.mocked(navigator.clipboard.writeText).mockResolvedValue(undefined);
  });

  it('expõe o suporte público e orientações seguras em inglês', () => {
    renderHelp();

    expect(screen.getByRole('heading', { level: 1, name: 'Run the giveaway, not the dashboard.' })).toBeInTheDocument();
    expect(screen.getAllByText('@snollken').length).toBeGreaterThan(0);
    expect(screen.getByText('Never send passwords, client secrets, access tokens, or cookies.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open Discord in a new tab' }))
      .toHaveAttribute('href', 'https://discord.com/app');
  });

  it('copia o Discord de contato sem esconder o identificador', async () => {
    renderHelp();

    fireEvent.click(screen.getByRole('button', { name: 'Copy @snollken' }));

    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('@snollken'));
    expect(screen.getByRole('button', { name: 'Copied @snollken' })).toBeInTheDocument();
    expect(screen.getAllByText('@snollken').length).toBeGreaterThan(0);
  });

  it('mantém um caminho manual quando a área de transferência falha', async () => {
    vi.mocked(navigator.clipboard.writeText).mockRejectedValueOnce(new Error('clipboard blocked'));
    renderHelp();

    fireEvent.click(screen.getByRole('button', { name: 'Copy @snollken' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Copy failed. Use @snollken manually.');
    expect(screen.getAllByText('@snollken').length).toBeGreaterThan(0);
  });

  it('mantém a página completa em português do Brasil', () => {
    renderHelp('pt-BR');

    expect(screen.getByRole('heading', { level: 1, name: 'Cuide do giveaway, não do painel.' })).toBeInTheDocument();
    expect(screen.getByText('AJUDA / 01')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Antes de pedir ajuda' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copiar @snollken' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Criado por Deerflow' })).toBeInTheDocument();
  });
});

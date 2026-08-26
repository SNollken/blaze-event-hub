import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Header } from '../components/Header';
import { initI18n } from '../i18n';

const mockSetLocale = vi.hoisted(() => vi.fn());

// setLocale real recarrega a pagina (window.location.reload); em teste
// mockamos apenas ela; t() e getLocale() continuam reais.
vi.mock('../i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../i18n')>();
  return { ...actual, setLocale: mockSetLocale };
});

describe('Header', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    mockSetLocale.mockReset();
    // modulo i18n mantém currentLocale padrao 'pt-BR' sem initI18n
  });

  it('renders title, custom actions and labelled theme/lang controls', () => {
    render(<Header title="Sorteios" actions={<button type="button">Novo sorteio</button>} />);

    expect(screen.getByRole('heading', { level: 1, name: 'Sorteios' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Novo sorteio' })).toBeInTheDocument();
    // default dark → oferece trocar para claro
    expect(screen.getByRole('button', { name: 'Alternar para modo claro' })).toBeInTheDocument();
    const lang = screen.getByRole('button', { name: 'Alternar para inglês' });
    expect(lang).toBeInTheDocument();
    expect(lang).toHaveTextContent('PT-BR');
  });

  it('theme toggle flips root class, aria-label and storage', () => {
    render(<Header title="Início" />);

    // default dark: efeito do useTheme aplica a classe no mount
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    const themeBtn = screen.getByRole('button', { name: 'Alternar para modo claro' });
    fireEvent.click(themeBtn);

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('beh-theme')).toBe('light');
    expect(screen.getByRole('button', { name: 'Alternar para modo escuro' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Alternar para modo escuro' }));
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('beh-theme')).toBe('dark');
  });

  it('lang button requests switch pt-BR → en', () => {
    render(<Header title="Início" />);
    fireEvent.click(screen.getByRole('button', { name: 'Alternar para inglês' }));
    expect(mockSetLocale).toHaveBeenCalledWith('en');
  });

  it('with stored en locale shows EN and requests switch back to pt-BR', () => {
    localStorage.setItem('i18n.locale', 'en');
    initI18n();

    render(<Header title="Home" />);

    const lang = screen.getByRole('button', { name: 'Switch to Portuguese' });
    expect(lang).toHaveTextContent('EN');
    // tema default dark continua, agora com rotulo EN
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument();

    fireEvent.click(lang);
    expect(mockSetLocale).toHaveBeenCalledWith('pt-BR');
  });
});

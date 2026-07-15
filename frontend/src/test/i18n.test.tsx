import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Layout } from '../components/Layout';
import { I18nProvider, useI18n } from '../i18n/I18nContext';
import indexHtml from '../../index.html?raw';

function LanguageProbe() {
  const { lang, setLang, t } = useI18n();
  return (
    <>
      <output aria-label="current language">{lang}:{t('navDashboard')}</output>
      <button type="button" onClick={() => setLang('pt-BR')}>Use Portuguese</button>
    </>
  );
}

describe('i18n foundation', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses English as the canonical default and synchronizes the document language', async () => {
    render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    );

    expect(screen.getByLabelText('current language')).toHaveTextContent('en:Explore');
    await waitFor(() => expect(document.documentElement.lang).toBe('en'));
  });

  it('persists an explicit Portuguese preference across provider remounts', () => {
    const firstRender = render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Use Portuguese' }));
    expect(screen.getByLabelText('current language')).toHaveTextContent('pt-BR:Explorar');

    firstRender.unmount();
    render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    );

    expect(screen.getByLabelText('current language')).toHaveTextContent('pt-BR:Explorar');
  });

  it('falls back to English when the stored preference is invalid', () => {
    window.localStorage.setItem('blaze-event-hub:language', 'invalid-locale');

    render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    );

    expect(screen.getByLabelText('current language')).toHaveTextContent('en:Explore');
  });

  it('keeps rendering in English when reading browser storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage unavailable');
    });

    expect(() => render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    )).not.toThrow();

    expect(screen.getByLabelText('current language')).toHaveTextContent('en:Explore');
  });

  it('keeps the selected language in memory when writing browser storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage unavailable');
    });
    render(
      <I18nProvider>
        <LanguageProbe />
      </I18nProvider>,
    );

    expect(() => fireEvent.click(screen.getByRole('button', { name: 'Use Portuguese' }))).not.toThrow();
    expect(screen.getByLabelText('current language')).toHaveTextContent('pt-BR:Explorar');
  });

  it('exposes an accessible language switcher in the global layout', () => {
    render(
      <I18nProvider>
        <MemoryRouter>
          <Layout><h1>Content</h1></Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    const switcher = screen.getByRole('combobox', { name: 'Language' });
    expect(switcher.closest('aside')).toBeNull();
    expect(switcher.closest('.app-language-control')).not.toBeNull();
    expect(switcher).toHaveValue('en');
    expect(screen.getByRole('option', { name: 'English' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Português (Brasil)' })).toBeInTheDocument();

    fireEvent.change(switcher, { target: { value: 'pt-BR' } });

    expect(screen.getByRole('combobox', { name: 'Idioma' })).toHaveValue('pt-BR');
    expect(screen.getByRole('link', { name: 'Explorar' })).toBeInTheDocument();
  });

  it('localizes route metadata and reacts to a language change', async () => {
    const description = document.createElement('meta');
    description.setAttribute('name', 'description');
    document.head.append(description);

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/my-events']}>
          <Layout><h1>Content</h1></Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    await waitFor(() => {
      expect(document.title).toBe('My giveaways | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Manage capture, finalization, and drawings for your giveaways.',
      );
    });

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'pt-BR' },
    });

    await waitFor(() => {
      expect(document.title).toBe('Meus giveaways | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Gerencie a captura, a finalização e os sorteios dos seus giveaways.',
      );
    });

    description.remove();
  });

  it('ships the static HTML document in the canonical English locale', () => {
    expect(indexHtml).toContain('<html lang="en">');
    expect(indexHtml).toContain(
      'content="Blaze.stream chat giveaways, from event creation to the result."',
    );
    expect(indexHtml).not.toContain('<html lang="pt-BR">');
  });

  it('localizes the global navigation shell in both supported languages', async () => {
    render(
      <I18nProvider>
        <MemoryRouter>
          <Layout><h1>Content</h1></Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(screen.getByRole('link', { name: 'Skip to main content' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open navigation' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Primary navigation' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Product sections' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close navigation' })).toBeInTheDocument();
    expect(screen.getByText('Chat giveaways, from entry to result.')).toBeInTheDocument();
    expect(screen.getByText('Giveaway control center')).toBeInTheDocument();
    expect(await screen.findByText('Blaze connected')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'pt-BR' },
    });

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abrir navegação' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Navegação principal' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Seções do produto' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fechar navegação' })).toBeInTheDocument();
    expect(screen.getByText('Giveaways do chat, do início ao resultado.')).toBeInTheDocument();
    expect(screen.getByText('Central de giveaways')).toBeInTheDocument();
    expect(screen.getByText('Blaze conectada')).toBeInTheDocument();
  });
});

import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { ToastContainer } from './Toast';

interface LayoutProps {
  children: ReactNode;
}

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

const mobileNavigationQuery = '(max-width: 720px)';

function routeMetadata(pathname: string) {
  if (pathname === '/') return ['Blaze Event Hub', 'Giveaways da Blaze.stream capturados pelo chat, do evento ao resultado.'] as const;
  if (pathname === '/events') return ['Explorar giveaways | Blaze Event Hub', 'Giveaways públicos em captura, finalizados e sorteados.'] as const;
  if (pathname === '/events/create') return ['Criar giveaway | Blaze Event Hub', 'Configure um giveaway e capture entradas pelo chat da Blaze.stream.'] as const;
  if (pathname === '/my-events') return ['Meus giveaways | Blaze Event Hub', 'Gerencie a captura, a finalização e o sorteio dos seus giveaways.'] as const;
  if (pathname === '/login') return ['Conectar Blaze | Blaze Event Hub', 'Conecte sua conta Blaze com OAuth para criar e gerenciar giveaways.'] as const;
  if (pathname === '/settings/blaze') return ['Conexão Blaze | Blaze Event Hub', 'Consulte e gerencie a conexão segura da sua conta Blaze.'] as const;
  if (pathname.endsWith('/result')) return ['Resultado do giveaway | Blaze Event Hub', 'Consulte o vencedor e o registro técnico do sorteio.'] as const;
  if (pathname.endsWith('/draw')) return ['Realizar sorteio | Blaze Event Hub', 'Área do criador para sortear o pool finalizado.'] as const;
  if (pathname.endsWith('/manage') || pathname.endsWith('/edit')) return ['Gerenciar giveaway | Blaze Event Hub', 'Controle a captura e o ciclo de vida do giveaway.'] as const;
  if (pathname.startsWith('/events/')) return ['Detalhes do giveaway | Blaze Event Hub', 'Acompanhe o estado, o comando e o resultado do giveaway.'] as const;
  return ['Página não encontrada | Blaze Event Hub', 'Blaze Event Hub.'] as const;
}

function isVisibleFocusable(element: HTMLElement) {
  if (element.closest('[hidden], [inert], [aria-hidden="true"]')) return false;
  const style = window.getComputedStyle(element);
  return style.display !== 'none' && style.visibility !== 'hidden';
}

export function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [mobileNavigation, setMobileNavigation] = useState(
    () => window.matchMedia(mobileNavigationQuery).matches,
  );
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const pageContentRef = useRef<HTMLDivElement>(null);
  const initialRoute = useRef(true);
  const restoreFocusFrame = useRef<number | null>(null);

  const closeSidebar = useCallback(() => {
    setSidebarOpen(false);
    if (restoreFocusFrame.current !== null) {
      window.cancelAnimationFrame(restoreFocusFrame.current);
    }
    restoreFocusFrame.current = window.requestAnimationFrame(() => {
      menuButtonRef.current?.focus();
      restoreFocusFrame.current = null;
    });
  }, []);

  useEffect(() => {
    const mediaQuery = window.matchMedia(mobileNavigationQuery);
    const handleChange = (event: MediaQueryListEvent) => {
      setMobileNavigation(event.matches);
      if (!event.matches) setSidebarOpen(false);
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  useEffect(() => {
    setSidebarOpen(false);
    if (pageContentRef.current) pageContentRef.current.scrollTop = 0;
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;

    const [title, description] = routeMetadata(location.pathname);
    document.title = title;
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', description);
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title);
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', description);

    if (initialRoute.current) {
      initialRoute.current = false;
      return;
    }
    if (restoreFocusFrame.current !== null) {
      window.cancelAnimationFrame(restoreFocusFrame.current);
      restoreFocusFrame.current = null;
    }
    const frame = window.requestAnimationFrame(() => mainRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [location.pathname]);

  useEffect(() => () => {
    if (restoreFocusFrame.current !== null) {
      window.cancelAnimationFrame(restoreFocusFrame.current);
    }
  }, []);

  useEffect(() => {
    if (!sidebarOpen || !mobileNavigation) return;

    const sidebar = document.getElementById('sidebar');
    const main = mainRef.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    main?.setAttribute('inert', '');
    main?.setAttribute('aria-hidden', 'true');

    const getFocusable = () => sidebar
      ? Array.from(sidebar.querySelectorAll<HTMLElement>(focusableSelector)).filter(isVisibleFocusable)
      : [];
    getFocusable()[0]?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (sidebar?.querySelector('[role="menu"]:not([hidden])')) return;
        event.preventDefault();
        closeSidebar();
        return;
      }
      const focusable = getFocusable();
      if (event.key !== 'Tab' || focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      main?.removeAttribute('inert');
      main?.removeAttribute('aria-hidden');
    };
  }, [closeSidebar, mobileNavigation, sidebarOpen]);

  return (
    <div className="app-layout">
      <a className="skip-link" href="#main-content">Pular para o conteúdo</a>
      <button
        ref={menuButtonRef}
        type="button"
        className={`mobile-menu-btn${sidebarOpen ? ' is-hidden' : ''}`}
        onClick={() => setSidebarOpen(true)}
        aria-label="Abrir navegação"
        aria-controls="sidebar"
        aria-expanded={sidebarOpen}
        aria-hidden={sidebarOpen}
        disabled={sidebarOpen}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
      </button>

      <div
        id="sidebar-overlay"
        className={sidebarOpen ? 'show' : undefined}
        onClick={closeSidebar}
        aria-hidden="true"
      />

      <Sidebar
        open={sidebarOpen}
        mobile={mobileNavigation}
        onClose={closeSidebar}
      />

      <main id="main-content" ref={mainRef} className="main-content" tabIndex={-1}>
        <div ref={pageContentRef} className="page-content">{children}</div>
      </main>
      <ToastContainer />
    </div>
  );
}

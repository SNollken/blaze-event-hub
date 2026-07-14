export const translations = {
  'pt-BR': {
    navDashboard: 'Explorar',
    navCreate: 'Criar giveaway',
    navMyEvents: 'Meus giveaways',
    navStudioChannel: 'Conexão Blaze',
    dashTitle: 'Blaze Event Hub',
    sectionOpen: 'Giveaways abertos',
    eventsTitle: 'Eventos',
    myEventsTitle: 'Meus eventos',
    appLoading: 'Carregando...',
    checkingSession: 'Verificando sessão...',
    errorBoundaryTitle: 'Algo falhou ao carregar esta tela.',
    errorBoundaryDescription: 'Recarregue a página ou verifique o backend.',
    notFoundTitle: 'Página não encontrada',
    notFoundDescription: 'Este endereço não existe ou foi movido.',
    notFoundBack: 'Voltar ao início',
    close: 'Fechar',
    closeNotification: 'Fechar notificação',
    unknownError: 'Erro desconhecido.',
    sbLogout: 'Sair da conta',
    logoutError: 'Não foi possível desconectar. Tente novamente.',
  },
  en: {
    navDashboard: 'Explore',
    navCreate: 'Create giveaway',
    navMyEvents: 'My giveaways',
    navStudioChannel: 'Blaze connection',
    dashTitle: 'Blaze Event Hub',
    sectionOpen: 'Open giveaways',
    eventsTitle: 'Events',
    myEventsTitle: 'My events',
    appLoading: 'Loading...',
    checkingSession: 'Checking session...',
    errorBoundaryTitle: 'Something failed while loading this screen.',
    errorBoundaryDescription: 'Reload the page or check the backend.',
    notFoundTitle: 'Page not found',
    notFoundDescription: 'This address does not exist or was moved.',
    notFoundBack: 'Back to home',
    close: 'Close',
    closeNotification: 'Close notification',
    unknownError: 'Unknown error.',
    sbLogout: 'Sign out',
    logoutError: 'Unable to sign out. Try again.',
  },
} as const;

export type Lang = keyof typeof translations;
export type TranslationKey = keyof typeof translations['pt-BR'];

type Assert<T extends true> = T;
export type EnglishCoversPortuguese = Assert<
  Exclude<TranslationKey, keyof typeof translations.en> extends never ? true : false
>;

import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  AlertEvent,
  AlertRule,
  AlertStatsResponse,
  BlazeEventsLogEntry,
  StatusResponse,
} from '../api/types';
import Alerts from '../pages/Alerts';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetAlertRules = vi.hoisted(() => vi.fn());
const mockGetAlertHistory = vi.hoisted(() => vi.fn());
const mockGetActiveAlerts = vi.hoisted(() => vi.fn());
const mockGetAlertStats = vi.hoisted(() => vi.fn());
const mockCreateAlertRule = vi.hoisted(() => vi.fn());
const mockDeleteAlertRule = vi.hoisted(() => vi.fn());
const mockSimulateBlazeEvent = vi.hoisted(() => vi.fn());
const mockAcknowledgeAlert = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getAlertRules: mockGetAlertRules,
    getAlertHistory: mockGetAlertHistory,
    getActiveAlerts: mockGetActiveAlerts,
    getAlertStats: mockGetAlertStats,
    createAlertRule: mockCreateAlertRule,
    deleteAlertRule: mockDeleteAlertRule,
    simulateBlazeEvent: mockSimulateBlazeEvent,
    acknowledgeAlert: mockAcknowledgeAlert,
  };
});

vi.mock('../components/Toast', () => ({
  addToast: mockAddToast,
  ToastContainer: () => null,
}));

function status(overrides: Partial<StatusResponse> = {}): StatusResponse {
  return {
    appName: 'Blaze Event Hub',
    version: 'test',
    javaVersion: '21',
    blazeOAuthConfigured: false,
    blazeApiConfigured: false,
    socketConfigured: false,
    tokenPresent: false,
    refreshCredentialPresent: false,
    monitoredChannelConfigured: false,
    eventsRunning: false,
    sessionIdPresent: false,
    activeProfilesCount: 0,
    overlaysCount: 0,
    uptimeSeconds: 1,
    oauthConnected: false,
    profilePresent: false,
    connectedAccountDisplayName: '',
    connectedAccountId: '',
    lastProfileSyncAt: null,
    nextRecommendedAction: 'CONNECT_BLAZE',
    ...overrides,
  };
}

const emptyStats: AlertStatsResponse = {
  totalRules: 0,
  enabledRules: 0,
  totalAlerts: 0,
  unacknowledgedAlerts: 0,
  acknowledgedAlerts: 0,
  rules: [],
};

const followRule: AlertRule = {
  id: 'r1',
  name: 'Follow alert',
  eventType: 'channel.follow',
  condition: 'ALWAYS',
  threshold: 0,
  template: null,
  enabled: true,
  cooldownMs: 0,
};

const followAlertEvent: AlertEvent = {
  id: 'a1',
  ruleId: 'r1',
  ruleName: 'Follow alert',
  eventType: 'channel.follow',
  triggeredAt: '2026-08-26T01:00:00Z',
  message: 'nova pessoa seguiu',
  acknowledged: false,
  metadata: {},
};

const simulatedLogEntry: BlazeEventsLogEntry = {
  id: 'log-1',
  timestamp: '2026-08-26T01:00:00Z',
  eventType: 'channel.follow',
  source: 'simulate',
  message: 'Evento simulado channel.follow',
  data: null,
};

/** Valor exibido no StatsCard cujo título é o texto informado. */
function statCardValue(title: string): string {
  const card = screen.getByText(title).closest('.glass-card');
  if (!card) throw new Error(`stats card com título "${title}" não encontrado`);
  return within(card as HTMLElement).getByText(/^\d+$/).textContent ?? '';
}

async function renderAlerts() {
  let utils!: ReturnType<typeof render>;
  // act async dá flush dos 4 fetches iniciais do usePolling (regras,
  // histórico, ativos, stats) + o getStatus do Sidebar via Layout.
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/alerts']}>
        <Alerts />
      </MemoryRouter>,
    );
  });
  return utils;
}

describe('Alerts', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status());
    mockGetAlertRules.mockReset().mockResolvedValue([]);
    mockGetAlertHistory.mockReset().mockResolvedValue([]);
    mockGetActiveAlerts.mockReset().mockResolvedValue([]);
    mockGetAlertStats.mockReset().mockResolvedValue(emptyStats);
    mockCreateAlertRule.mockReset().mockResolvedValue(followRule);
    mockDeleteAlertRule.mockReset().mockResolvedValue(undefined);
    mockSimulateBlazeEvent.mockReset().mockResolvedValue(simulatedLogEntry);
    mockAcknowledgeAlert.mockReset().mockResolvedValue(followAlertEvent);
    mockAddToast.mockReset();
  });

  it('empty: rules tab active by default, empty message and zeroed stats cards', async () => {
    await renderAlerts();

    // aba de regras ativa por padrão
    expect(screen.getByRole('button', { name: 'Regras' })).toHaveClass('active');
    expect(screen.getByRole('table', { name: 'Regras de alerta' })).toBeInTheDocument();

    // mensagem de vazio das regras (alerts.emptyRules pt-BR)
    expect(await screen.findByText('Nenhuma regra configurada.')).toBeInTheDocument();

    // os três cards de estatística mostram 0 após o load
    expect(statCardValue('Regras Ativas')).toBe('0');
    expect(statCardValue('Alertas Pendentes')).toBe('0');
    expect(statCardValue('Reconhecidos')).toBe('0');
  });

  it('renders a rule row with event badge, condition label and active status; stats card reflects enabledRules', async () => {
    mockGetAlertRules.mockResolvedValue([followRule]);
    mockGetAlertStats.mockResolvedValue({ ...emptyStats, enabledRules: 2 });

    await renderAlerts();

    expect(await screen.findByText('Follow alert')).toBeInTheDocument();
    // badge do tipo de evento
    expect(screen.getByText('channel.follow')).toBeInTheDocument();
    // rótulo da condição ALWAYS (alerts.conditionAlways pt-BR)
    expect(screen.getByText('Sempre')).toBeInTheDocument();
    // badge de status ativo (alerts.statusActive pt-BR)
    expect(screen.getByText('Ativo')).toBeInTheDocument();

    // card "Regras Ativas" usa stats.enabledRules
    expect(statCardValue('Regras Ativas')).toBe('2');
  });

  it('switches to the history tab showing the alert event and hiding the rules table', async () => {
    mockGetAlertRules.mockResolvedValue([followRule]);
    mockGetAlertHistory.mockResolvedValue([followAlertEvent]);

    await renderAlerts();

    fireEvent.click(screen.getByRole('button', { name: 'Histórico' }));

    // linha do histórico: ruleName + mensagem visíveis
    expect(await screen.findByText('nova pessoa seguiu')).toBeInTheDocument();
    expect(screen.getByText('Follow alert')).toBeInTheDocument();

    // a tabela/linha de regras saiu do documento
    expect(screen.queryByRole('table', { name: 'Regras de alerta' })).not.toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Histórico de alertas' })).toBeInTheDocument();
  });

  it('acknowledges an unacknowledged alert and toasts success', async () => {
    mockGetAlertHistory.mockResolvedValue([followAlertEvent]);

    await renderAlerts();

    fireEvent.click(screen.getByRole('button', { name: 'Histórico' }));

    const ackButton = await screen.findByRole('button', {
      name: 'Reconhecer alerta Follow alert',
    });
    fireEvent.click(ackButton);

    await waitFor(() => expect(mockAcknowledgeAlert).toHaveBeenCalledWith('a1'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Alerta reconhecido'));
    // handler completo: actionLoading volta a null e o botão reabilita
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Reconhecer alerta Follow alert' }),
      ).toBeEnabled(),
    );
  });

  it('creates a rule through the modal with the default form values', async () => {
    await renderAlerts();

    fireEvent.click(screen.getByRole('button', { name: 'Nova Regra' }));

    const dialog = await screen.findByRole('dialog', { name: 'Nova Regra' });
    expect(dialog).toBeInTheDocument();

    // criar fica desabilitado enquanto o nome está vazio
    const createButton = screen.getByRole('button', { name: 'Criar Regra' });
    expect(createButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Nome da Regra'), {
      target: { value: 'Minha regra' },
    });
    expect(createButton).toBeEnabled();

    fireEvent.click(createButton);

    await waitFor(() =>
      expect(mockCreateAlertRule).toHaveBeenCalledWith({
        name: 'Minha regra',
        eventType: 'channel.follow',
        condition: 'ALWAYS',
        threshold: 0,
        template: null,
        enabled: true,
        cooldownMs: 0,
      }),
    );
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Regra criada'));
    // modal fecha e o fluxo assíncrono termina (botão volta a habilitar)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Nova Regra' })).toBeEnabled(),
    );
  });

  it('deletes a rule and toasts success', async () => {
    mockGetAlertRules.mockResolvedValue([followRule]);

    await renderAlerts();

    fireEvent.click(
      await screen.findByRole('button', { name: 'Remover regra Follow alert' }),
    );

    await waitFor(() => expect(mockDeleteAlertRule).toHaveBeenCalledWith('r1'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Regra removida'));
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Remover regra Follow alert' }),
      ).toBeEnabled(),
    );
  });

  it('simulates a blaze event using the form event type and toasts success', async () => {
    await renderAlerts();

    fireEvent.click(screen.getByRole('button', { name: 'Simular Evento' }));

    // mensagem começa com alerts.simulateMessage pt-BR ("Evento simulado")
    await waitFor(() =>
      expect(mockSimulateBlazeEvent).toHaveBeenCalledWith(
        'channel.follow',
        expect.stringMatching(/^Evento simulado/),
      ),
    );
    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('success', 'Evento simulado enviado'),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Simular Evento' })).toBeEnabled(),
    );
  });

  it('shows the error banner when getAlertRules rejects', async () => {
    mockGetAlertRules.mockRejectedValue(new Error('regras fora'));

    await renderAlerts();

    expect(await screen.findByText('regras fora')).toBeInTheDocument();
    // usePolling também dispara toast de erro na primeira falha
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('error', 'regras fora'));
  });
});

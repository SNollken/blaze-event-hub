import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { usePolling, addToast } from '../components/Toast';
import {
  getOverlayProfiles,
  getOverlays,
  createOverlayProfile,
  deleteOverlayProfile,
  deleteOverlay,
} from '../api/client';
import type { OverlayProfile, Overlay } from '../api/types';
import {
  Layers,
  Plus,
  Trash2,
  ExternalLink,
  Copy,
  Eye,
  Settings,
  LayoutIcon,
  RefreshCw,
} from 'lucide-react';

export default function Overlays() {
  const fetchProfiles = useCallback(() => getOverlayProfiles(), []);
  const { data: profiles, loading: profilesLoading, reload: reloadProfiles } = usePolling(fetchProfiles, 20000);

  const [overlays, setOverlays] = useState<Overlay[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState<string | null>(null);
  const [showCreateProfileModal, setShowCreateProfileModal] = useState(false);
  const [selectedOverlay, setSelectedOverlay] = useState<Overlay | null>(null);

  const loadOverlays = async (profileId: string) => {
    setSelectedProfileId(profileId);
    try {
      const data = await getOverlays(profileId);
      setOverlays(data);
    } catch {
      setOverlays([]);
    }
  };

  const handleCreateProfile = async (name: string) => {
    try {
      await createOverlayProfile({ name });
      addToast('success', 'Perfil criado com sucesso');
      reloadProfiles();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao criar perfil');
    }
  };

  const handleDeleteProfile = async (id: string) => {
    try {
      await deleteOverlayProfile(id);
      addToast('success', 'Perfil removido');
      if (selectedProfileId === id) {
        setSelectedProfileId(null);
        setOverlays([]);
      }
      reloadProfiles();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao remover perfil');
    }
  };

  const handleDeleteOverlay = async (id: string) => {
    try {
      await deleteOverlay(id);
      addToast('success', 'Overlay removido');
      setOverlays(overlays.filter((o) => o.id !== id));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao remover overlay');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    addToast('success', 'URL copiada');
  };

  const getOverlayUrl = (token: string) => `${window.location.origin}/overlay/${token}`;

  const profileColumns: Column<OverlayProfile>[] = [
    { key: 'name', header: 'Nome', sortable: true },
    {
      key: 'createdAt', header: 'Criado em',
      render: (p) => <span className="mono" style={{ fontSize: 12 }}>{new Date(p.createdAt).toLocaleDateString('pt-BR')}</span>,
    },
    {
      key: 'actions', header: '', width: 100,
      render: (p) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-primary btn-sm btn-icon" aria-label={`Ver overlays de ${p.name}`} onClick={() => loadOverlays(p.id)}>
            <Eye size={12} />
          </button>
          <button className="btn btn-danger btn-sm btn-icon" aria-label={`Remover perfil ${p.name}`} onClick={() => handleDeleteProfile(p.id)}>
            <Trash2 size={12} />
          </button>
        </div>
      ),
    },
  ];

  const overlayColumns: Column<Overlay>[] = [
    { key: 'name', header: 'Nome', sortable: true },
    { key: 'type', header: 'Tipo', render: (o) => <Badge variant="neutral">{o.type}</Badge> },
    {
      key: 'enabled', header: 'Status',
      render: (o) => <Badge variant={o.enabled ? 'success' : 'neutral'} dot>{o.enabled ? 'Ativo' : 'Inativo'}</Badge>,
    },
    {
      key: 'config', header: 'Canvas',
      render: (o) => <span className="mono" style={{ fontSize: 12 }}>{o.config.canvasWidth}x{o.config.canvasHeight}</span>,
    },
    {
      key: 'publicToken', header: 'URL', width: 200,
      render: (o) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <code className="mono" style={{ fontSize: 11, color: 'var(--text-muted)', maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {o.publicToken}
          </code>
          <button className="copy-btn" aria-label={`Copiar URL da overlay ${o.name}`} onClick={() => copyToClipboard(getOverlayUrl(o.publicToken))}>
            <Copy size={10} />
          </button>
          <a href={getOverlayUrl(o.publicToken)} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm" style={{ padding: '2px 6px' }}>
            <ExternalLink size={10} />
          </a>
        </div>
      ),
    },
    {
      key: 'actions', header: '', width: 80,
      render: (o) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-secondary btn-sm btn-icon" aria-label={`Ver detalhes da overlay ${o.name}`} onClick={() => setSelectedOverlay(o)}>
            <Settings size={12} />
          </button>
          <button className="btn btn-danger btn-sm btn-icon" aria-label={`Remover overlay ${o.name}`} onClick={() => handleDeleteOverlay(o.id)}>
            <Trash2 size={12} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <Layout title="Overlays" subtitle="Configuracao de overlays para OBS">
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Perfis"
          value={profiles?.length ?? 0}
          icon={<LayoutIcon size={18} />}
          color="primary"
        />
        <StatsCard
          title="Overlays"
          value={overlays.length}
          icon={<Layers size={18} />}
          color="accent"
          subtitle={selectedProfileId ? 'No perfil selecionado' : 'Selecione um perfil'}
        />
      </div>

      {/* Profiles */}
      <div className="section-header">
        <span className="section-title">Perfis de Overlay</span>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreateProfileModal(true)}>
          <Plus size={14} />
          Novo Perfil
        </button>
      </div>
      <DataTable
        columns={profileColumns}
        data={profiles || []}
        filterable
        filterKeys={['name']}
        emptyMessage="Nenhum perfil criado."
      />

      {/* Overlays for selected profile */}
      {selectedProfileId && (
        <div style={{ marginTop: 24 }}>
          <div className="section-header">
            <span className="section-title">
              Overlays do Perfil
              <Badge variant="neutral" style={{ marginLeft: 8 } as React.CSSProperties}>{overlays.length}</Badge>
            </span>
            <button className="btn btn-secondary btn-sm" onClick={() => loadOverlays(selectedProfileId)}>
              <RefreshCw size={14} />
              Atualizar
            </button>
          </div>
          <DataTable
            columns={overlayColumns}
            data={overlays}
            filterable
            filterKeys={['name', 'type']}
            emptyMessage="Nenhum overlay neste perfil."
          />
        </div>
      )}

      {/* Create profile modal */}
      <Modal
        open={showCreateProfileModal}
        onClose={() => setShowCreateProfileModal(false)}
        title="Novo Perfil de Overlay"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateProfileModal(false)}>Cancelar</button>
            <button className="btn btn-primary" onClick={() => {
              const input = document.querySelector('.modal-body .input') as HTMLInputElement;
              if (input?.value) {
                handleCreateProfile(input.value);
                setShowCreateProfileModal(false);
              }
            }}>Criar Perfil</button>
          </>
        }
      >
        <div>
          <label>Nome do Perfil</label>
          <input className="input" placeholder="Ex: Meu Perfil OBS" />
        </div>
        <div>
          <label>Descricao (opcional)</label>
          <input className="input" placeholder="Descricao breve" />
        </div>
      </Modal>

      {/* Overlay detail */}
      <Modal
        open={!!selectedOverlay}
        onClose={() => setSelectedOverlay(null)}
        title={selectedOverlay?.name || ''}
        footer={
          <button className="btn btn-secondary" onClick={() => setSelectedOverlay(null)}>Fechar</button>
        }
      >
        {selectedOverlay && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <Badge variant="neutral">{selectedOverlay.type}</Badge>
              <Badge variant={selectedOverlay.enabled ? 'success' : 'neutral'} dot>
                {selectedOverlay.enabled ? 'Ativo' : 'Inativo'}
              </Badge>
            </div>

            <div>
              <label>Canvas</label>
              <div className="mono" style={{ fontSize: 13 }}>
                {selectedOverlay.config.canvasWidth} x {selectedOverlay.config.canvasHeight}
              </div>
            </div>

            <div>
              <label>Fundo</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 16, height: 16, borderRadius: 4, background: selectedOverlay.config.backgroundColor, border: '1px solid var(--border)' }} />
                <span className="mono" style={{ fontSize: 12 }}>{selectedOverlay.config.backgroundColor}</span>
                <Badge variant="neutral">{selectedOverlay.config.transparent ? 'Transparente' : 'Solido'}</Badge>
              </div>
            </div>

            {selectedOverlay.layers.length > 0 && (
              <div>
                <label>Camadas ({selectedOverlay.layers.length})</label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
                  {selectedOverlay.layers.map((layer) => (
                    <div key={layer.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-secondary)' }}>
                      <Badge variant="neutral">{layer.type}</Badge>
                      <span>{layer.text || layer.assetId || `#${layer.id}`}</span>
                      <span className="mono">z:{layer.zIndex}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div>
              <label>URL Publica</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
                <code className="mono" style={{ flex: 1, fontSize: 11, padding: '4px 8px', background: 'var(--bg-base)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  {getOverlayUrl(selectedOverlay.publicToken)}
                </code>
                <button className="copy-btn" aria-label={`Copiar URL da overlay ${selectedOverlay.name}`} onClick={() => copyToClipboard(getOverlayUrl(selectedOverlay.publicToken))}>
                  <Copy size={10} />
                  Copiar
                </button>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  );
}

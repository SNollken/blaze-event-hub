import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
import { t } from '../i18n';
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
  const { data: profiles, loading: profilesLoading, error: profilesError, reload: reloadProfiles } = usePolling(fetchProfiles, 20000);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [overlays, setOverlays] = useState<Overlay[]>([]);
  const [selectedProfileId, setSelectedProfileId] = useState<string | null>(null);
  const [showCreateProfileModal, setShowCreateProfileModal] = useState(false);
  const [selectedOverlay, setSelectedOverlay] = useState<Overlay | null>(null);
  const [profileName, setProfileName] = useState('');
  const [profileDescription, setProfileDescription] = useState('');

  const loadOverlays = async (profileId: string) => {
    setSelectedProfileId(profileId);
    try {
      const data = await getOverlays(profileId);
      setOverlays(data);
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('overlays.loadOverlaysError'));
      setOverlays([]);
    }
  };

  const handleCreateProfile = async (name: string, description: string) => {
    setActionLoading('create-profile');
    try {
      await createOverlayProfile({ name, description: description || undefined });
      addToast('success', t('overlays.createProfileSuccess'));
      setProfileName('');
      setProfileDescription('');
      reloadProfiles();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('overlays.createProfileError'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteProfile = async (id: string) => {
    setActionLoading('delete-profile');
    try {
      await deleteOverlayProfile(id);
      addToast('success', t('overlays.deleteProfileSuccess'));
      if (selectedProfileId === id) {
        setSelectedProfileId(null);
        setOverlays([]);
      }
      reloadProfiles();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('overlays.deleteProfileError'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteOverlay = async (id: string) => {
    setActionLoading('delete-overlay');
    try {
      await deleteOverlay(id);
      addToast('success', t('overlays.deleteOverlaySuccess'));
      setOverlays(overlays.filter((o) => o.id !== id));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('overlays.deleteOverlayError'));
    } finally {
      setActionLoading(null);
    }
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      addToast('success', t('overlays.copyUrlSuccess'));
    } catch {
      addToast('error', t('overlays.copyUrlError'));
    }
  };

  const getOverlayUrl = (token: string) => `${window.location.origin}/overlay/${token}`;

  const profileColumns: Column<OverlayProfile>[] = [
    { key: 'name', header: t('common.name'), sortable: true },
    {
      key: 'createdAt', header: t('overlays.colCreated'),
      render: (p) => <span className="mono" style={{ fontSize: 12 }}>{new Date(p.createdAt).toLocaleDateString('pt-BR')}</span>,
    },
    {
      key: 'actions', header: '', width: 100,
      render: (p) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-primary btn-sm btn-icon" aria-label={`${t('overlays.viewOverlays')} ${p.name}`} onClick={() => loadOverlays(p.id)} disabled={actionLoading !== null}>
            <Eye size={12} />
          </button>
          <button className="btn btn-danger btn-sm btn-icon" aria-label={`${t('overlays.removeProfile')} ${p.name}`} onClick={() => handleDeleteProfile(p.id)} disabled={actionLoading !== null}>
            <Trash2 size={12} />
          </button>
        </div>
      ),
    },
  ];

  const overlayColumns: Column<Overlay>[] = [
    { key: 'name', header: t('common.name'), sortable: true },
    { key: 'type', header: t('alerts.colEvent'), render: (o) => <Badge variant="neutral">{o.type}</Badge> },
    {
      key: 'enabled', header: t('common.status'),
      render: (o) => <Badge variant={o.enabled ? 'success' : 'neutral'} dot>{o.enabled ? t('common.connected') : t('common.disconnected')}</Badge>,
    },
    {
      key: 'config', header: t('overlays.canvas'),
      render: (o) => <span className="mono" style={{ fontSize: 12 }}>{o.config.canvasWidth}x{o.config.canvasHeight}</span>,
    },
    {
      key: 'publicToken', header: t('overlays.publicUrl'), width: 200,
      render: (o) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <code className="mono" style={{ fontSize: 11, color: 'var(--text-muted)', maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {o.publicToken}
          </code>
          <button className="copy-btn" aria-label={`${t('overlays.copyUrl')} ${o.name}`} onClick={() => copyToClipboard(getOverlayUrl(o.publicToken))}>
            <Copy size={10} />
          </button>
          <a href={getOverlayUrl(o.publicToken)} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm" style={{ padding: '2px 6px' }} aria-label={`${t('overlays.viewOverlay')} ${o.name} ${t('overlays.newTab')}`}>
            <ExternalLink size={10} aria-hidden="true" />
          </a>
        </div>
      ),
    },
    {
      key: 'actions', header: '', width: 80,
      render: (o) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-secondary btn-sm btn-icon" aria-label={`${t('overlays.viewDetails')} ${o.name}`} onClick={() => setSelectedOverlay(o)} disabled={actionLoading !== null}>
            <Settings size={12} />
          </button>
          <button className="btn btn-danger btn-sm btn-icon" aria-label={`${t('overlays.removeOverlay')} ${o.name}`} onClick={() => handleDeleteOverlay(o.id)} disabled={actionLoading !== null}>
            <Trash2 size={12} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <Layout title={t('nav.overlays')} subtitle={t('overlays.subtitle')}>
      {profilesError && <ErrorBanner error={profilesError} onRetry={reloadProfiles} />}
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title={t('overlays.profiles')}
          value={profiles?.length ?? 0}
          icon={<LayoutIcon size={18} />}
          color="primary"
        />
        <StatsCard
          title={t('nav.overlays')}
          value={overlays.length}
          icon={<Layers size={18} />}
          color="accent"
          subtitle={selectedProfileId ? `${t('overlays.profileSelected')} ${selectedProfileId}` : t('overlays.noProfileSelected')}
        />
      </div>

      {/* Profiles */}
      <div className="section-header">
        <span className="section-title">{t('overlays.profileSection')}</span>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreateProfileModal(true)} disabled={actionLoading !== null}>
          <Plus size={14} />
          {t('overlays.newProfile')}
        </button>
      </div>
      {profilesLoading && !profiles ? (
        <div className="skeleton-list" />
      ) : (
        <DataTable
          columns={profileColumns}
          data={profiles || []}
          filterable
          filterKeys={['name']}
          emptyMessage={t('overlays.emptyProfiles')}
        />
      )}

      {/* Overlays for selected profile */}
      {selectedProfileId && (
        <div style={{ marginTop: 24 }}>
          <div className="section-header">
            <span className="section-title">
              {t('overlays.overlaysSection')}
              <Badge variant="neutral" style={{ marginLeft: 8 } as React.CSSProperties}>{overlays.length}</Badge>
            </span>
            <button className="btn btn-secondary btn-sm" onClick={() => loadOverlays(selectedProfileId)} disabled={actionLoading !== null}>
              <RefreshCw size={14} />
              {t('common.refresh')}
            </button>
          </div>
          <DataTable
            columns={overlayColumns}
            data={overlays}
            filterable
            filterKeys={['name', 'type']}
            emptyMessage={t('overlays.emptyOverlays')}
          />
        </div>
      )}

      {/* Create profile modal */}
      <Modal
        open={showCreateProfileModal}
        onClose={() => setShowCreateProfileModal(false)}
        title={t('overlays.newProfileTitle')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateProfileModal(false)} disabled={actionLoading !== null}>{t('common.cancel')}</button>
            <button className="btn btn-primary" onClick={() => {
              if (profileName.trim()) {
                handleCreateProfile(profileName.trim(), profileDescription.trim());
                setShowCreateProfileModal(false);
              }
            }} disabled={actionLoading !== null || !profileName.trim()}>
              {actionLoading === 'create-profile' ? t('overlays.creating') : t('overlays.createProfile')}
            </button>
          </>
        }
      >
        <div>
          <label>{t('overlays.profileNameLabel')}</label>
          <input className="input" placeholder={t('overlays.profileNamePlaceholder')} value={profileName} onChange={e => setProfileName(e.target.value)} />
        </div>
        <div>
          <label>{t('overlays.descriptionLabel')}</label>
          <input className="input" placeholder={t('overlays.descriptionPlaceholder')} value={profileDescription} onChange={e => setProfileDescription(e.target.value)} />
        </div>
      </Modal>

      {/* Overlay detail */}
      <Modal
        open={!!selectedOverlay}
        onClose={() => setSelectedOverlay(null)}
        title={selectedOverlay?.name || ''}
        footer={
          <button className="btn btn-secondary" onClick={() => setSelectedOverlay(null)} disabled={actionLoading !== null}>{t('common.close')}</button>
        }
      >
        {selectedOverlay && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <Badge variant="neutral">{selectedOverlay.type}</Badge>
              <Badge variant={selectedOverlay.enabled ? 'success' : 'neutral'} dot>
                {selectedOverlay.enabled ? t('common.connected') : t('common.disconnected')}
              </Badge>
            </div>

            <div>
              <label>{t('overlays.canvas')}</label>
              <div className="mono" style={{ fontSize: 13 }}>
                {selectedOverlay.config.canvasWidth} x {selectedOverlay.config.canvasHeight}
              </div>
            </div>

            <div>
              <label>{t('overlays.background')}</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 16, height: 16, borderRadius: 4, background: selectedOverlay.config.backgroundColor, border: '1px solid var(--border)' }} />
                <span className="mono" style={{ fontSize: 12 }}>{selectedOverlay.config.backgroundColor}</span>
                <Badge variant="neutral">{selectedOverlay.config.transparent ? t('overlays.transparent') : t('overlays.solid')}</Badge>
              </div>
            </div>

            {selectedOverlay.layers.length > 0 && (
              <div>
                <label>{t('overlays.layers')} ({selectedOverlay.layers.length})</label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
                  {selectedOverlay.layers.map((layer) => (
                    <div key={layer.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-secondary)' }}>
                      <Badge variant="neutral">{layer.type}</Badge>
                      <span>{layer.text || layer.assetId || `#${layer.id}`}</span>
                      <span className="mono">{t('overlays.zIndex')}{layer.zIndex}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div>
              <label>{t('overlays.publicUrl')}</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
                <code className="mono" style={{ flex: 1, fontSize: 11, padding: '4px 8px', background: 'var(--bg-base)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  {getOverlayUrl(selectedOverlay.publicToken)}
                </code>
                <button className="copy-btn" aria-label={`${t('overlays.copyUrl')} ${selectedOverlay.name}`} onClick={() => copyToClipboard(getOverlayUrl(selectedOverlay.publicToken))}>
                  <Copy size={10} />
                  {t('common.copy')}
                </button>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  );
}

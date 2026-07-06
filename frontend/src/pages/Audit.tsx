import { Layout } from '../components/Layout';
import { ClipboardList } from 'lucide-react';

export default function Audit() {
  return (
    <Layout title="Auditoria" subtitle="Historico de acoes do sistema">
      <div className="empty-state" style={{ minHeight: 300 }}>
        <ClipboardList size={48} />
        <div style={{ fontSize: 15, fontWeight: 600 }}>Modulo em construcao</div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          O endpoint de auditoria ainda nao esta disponivel no backend.
        </div>
      </div>
    </Layout>
  );
}

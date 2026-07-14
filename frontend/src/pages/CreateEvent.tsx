import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ArrowRight, CheckCircle2, Radio, Search } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { createEvent, getMe, resolveBlazeChannel } from '../api/client';
import type { BlazeChannelResponse, MemberProfile } from '../api/types';
import { addToast } from '../components/Toast';

type FieldName = 'title' | 'prize' | 'entryCommand' | 'channel';
type FieldErrors = Partial<Record<FieldName, string>>;

function toIsoDate(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function normalizeChannelSlug(value: string): string {
  const withoutOrigin = value.trim().replace(/^(?:https?:\/\/)?(?:www\.)?blaze\.stream\//i, '');
  return withoutOrigin.split(/[/?#]/, 1)[0].replace(/^@/, '').trim();
}

export default function CreateEvent() {
  const navigate = useNavigate();
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [title, setTitle] = useState('');
  const [prize, setPrize] = useState('');
  const [description, setDescription] = useState('');
  const [entryCommand, setEntryCommand] = useState('!participar');
  const [channelSlug, setChannelSlug] = useState('');
  const [resolvedChannel, setResolvedChannel] = useState<BlazeChannelResponse | null>(null);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [isResolving, setIsResolving] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [channelError, setChannelError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  useEffect(() => {
    let active = true;
    getMe()
      .then((profile) => {
        if (active) setMember(profile);
      })
      .catch(() => {
        if (active) setMember(null);
      });
    return () => {
      active = false;
    };
  }, []);

  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? 'O encerramento precisa acontecer depois do início.'
      : '';
  }, [endsAt, startsAt]);

  const resolveChannel = async () => {
    const slug = normalizeChannelSlug(channelSlug);
    if (!slug) {
      setResolvedChannel(null);
      setChannelError('Informe o slug do canal na Blaze.');
      setFieldErrors((current) => ({ ...current, channel: 'Informe e localize o canal da transmissão.' }));
      return;
    }

    setIsResolving(true);
    setChannelError('');
    setResolvedChannel(null);
    try {
      const channel = await resolveBlazeChannel(slug);
      setChannelSlug(channel.slug);
      setResolvedChannel(channel);
      setFieldErrors((current) => ({ ...current, channel: undefined }));
    } catch (resolveError) {
      setChannelError(resolveError instanceof Error
        ? resolveError.message
        : 'Não foi possível localizar esse canal na Blaze.');
    } finally {
      setIsResolving(false);
    }
  };

  const validateFields = (): FieldErrors => {
    const next: FieldErrors = {};
    if (!title.trim()) next.title = 'Dê um título ao giveaway.';
    if (!prize.trim()) next.prize = 'Descreva o prêmio que será sorteado.';
    if (!/^![\p{L}\p{N}][\p{L}\p{N}_-]{0,78}$/u.test(entryCommand.trim())) {
      next.entryCommand = 'Use ! seguido de letras, números, _ ou -.';
    }
    if (!resolvedChannel) next.channel = 'Resolva e confirme o canal da transmissão antes de criar.';
    return next;
  };

  const clearFieldError = (field: FieldName) => {
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    const invalidFields = validateFields();
    setFieldErrors(invalidFields);
    if (Object.keys(invalidFields).length > 0 || dateError) {
      setError('Revise os campos destacados antes de criar o giveaway.');
      return;
    }
    if (!resolvedChannel) return;

    setIsSubmitting(true);
    setError('');
    try {
      const created = await createEvent({
        title: title.trim(),
        prize: prize.trim(),
        description: description.trim() || undefined,
        entryCommand: entryCommand.trim(),
        creatorChannelSlug: resolvedChannel.slug,
        startsAt: toIsoDate(startsAt),
        endsAt: toIsoDate(endsAt),
      });
      addToast('success', 'Giveaway criado como rascunho.');
      navigate(`/events/${created.id}/manage`);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Não foi possível criar o giveaway.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className="section-label">Novo giveaway</span>
          <h1 className="page-title">Prepare a entrada antes de entrar ao vivo</h1>
          <p>Defina o prêmio, o comando e o canal. A captura só começa quando você abrir o evento.</p>
        </div>
        {member && (
          <div className="control-card creator-card" aria-label="Criador conectado">
            <span className="section-label">Criador conectado</span>
            <div className="creator-identity">
              <strong>{member.displayName || member.blazeUsername}</strong>
              <span className="creator-handle">@{member.blazeUsername}</span>
            </div>
          </div>
        )}
      </header>

      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      <form className="control-grid" onSubmit={handleSubmit} noValidate>
        <section className="control-card">
          <div className="section-label">Informações públicas</div>
          <div className="form-group">
            <label htmlFor="event-title">Título</label>
            <input
              id="event-title"
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
                clearFieldError('title');
              }}
              aria-invalid={Boolean(fieldErrors.title)}
              aria-describedby={fieldErrors.title ? 'event-title-error' : undefined}
              maxLength={140}
              placeholder="Ex.: Sorteio de setup para a comunidade"
              disabled={isSubmitting}
              required
            />
            {fieldErrors.title && <span id="event-title-error" className="form-helper form-helper--err" role="alert">{fieldErrors.title}</span>}
          </div>
          <div className="form-group">
            <label htmlFor="event-prize">Prêmio</label>
            <input
              id="event-prize"
              value={prize}
              onChange={(event) => {
                setPrize(event.target.value);
                clearFieldError('prize');
              }}
              aria-invalid={Boolean(fieldErrors.prize)}
              aria-describedby={fieldErrors.prize ? 'event-prize-error' : undefined}
              maxLength={180}
              placeholder="Ex.: Gift card de R$ 200"
              disabled={isSubmitting}
              required
            />
            {fieldErrors.prize && <span id="event-prize-error" className="form-helper form-helper--err" role="alert">{fieldErrors.prize}</span>}
          </div>
          <div className="form-group">
            <label htmlFor="event-description">Descrição</label>
            <textarea
              id="event-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={2_000}
              placeholder="Explique o giveaway e qualquer informação importante para a comunidade."
              disabled={isSubmitting}
            />
          </div>
        </section>

        <section className="control-card">
          <div className="section-label">Sinal de entrada</div>
          <p>Quando o evento estiver aberto, cada usuário entra uma única vez ao enviar exatamente este comando no chat.</p>
          <div className="form-group">
            <label htmlFor="event-command">Comando do chat</label>
            <input
              id="event-command"
              className="signal-command"
              value={entryCommand}
              onChange={(event) => {
                setEntryCommand(event.target.value);
                clearFieldError('entryCommand');
              }}
              aria-invalid={Boolean(fieldErrors.entryCommand)}
              aria-describedby={fieldErrors.entryCommand ? 'event-command-error' : undefined}
              maxLength={80}
              autoComplete="off"
              spellCheck={false}
              disabled={isSubmitting}
              required
            />
            {fieldErrors.entryCommand && <span id="event-command-error" className="form-helper form-helper--err" role="alert">{fieldErrors.entryCommand}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="event-channel">Canal na Blaze</label>
            <div className="form-row">
              <input
                id="event-channel"
                value={channelSlug}
                onChange={(event) => {
                  setChannelSlug(event.target.value);
                  setResolvedChannel(null);
                  setChannelError('');
                  clearFieldError('channel');
                }}
                aria-invalid={Boolean(channelError || fieldErrors.channel)}
                aria-describedby={channelError || fieldErrors.channel ? 'event-channel-error' : undefined}
                placeholder="slug-do-canal ou URL da Blaze"
                maxLength={180}
                autoComplete="off"
                disabled={isSubmitting || isResolving}
              />
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => void resolveChannel()}
                disabled={isSubmitting || isResolving || !channelSlug.trim()}
              >
                <Search size={16} aria-hidden="true" />
                {isResolving ? 'Localizando...' : 'Localizar canal'}
              </button>
            </div>
            {(channelError || fieldErrors.channel) && (
              <span id="event-channel-error" className="form-helper form-helper--err" role="alert">
                {channelError || fieldErrors.channel}
              </span>
            )}
          </div>

          {resolvedChannel && (
            <div className="control-card" role="status">
              <CheckCircle2 size={18} aria-hidden="true" />
              <div>
                <strong>{resolvedChannel.displayName}</strong>
                <span>@{resolvedChannel.slug}</span>
                <code>{resolvedChannel.id}</code>
              </div>
            </div>
          )}
        </section>

        <section className="control-card">
          <div className="section-label">Agenda opcional</div>
          <p>Essas datas informam a comunidade. A captura continua sob seu controle manual.</p>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="event-starts-at">Início previsto</label>
              <input
                id="event-starts-at"
                type="datetime-local"
                value={startsAt}
                onChange={(event) => setStartsAt(event.target.value)}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
            <div className="form-group">
              <label htmlFor="event-ends-at">Encerramento previsto</label>
              <input
                id="event-ends-at"
                type="datetime-local"
                value={endsAt}
                onChange={(event) => setEndsAt(event.target.value)}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
          </div>
          {dateError && <span id="event-date-error" className="form-helper form-helper--err" role="alert">{dateError}</span>}
        </section>

        <section className="control-card">
          <div className="section-label">Como funciona</div>
          <ol className="lifecycle">
            <li className="is-current"><Radio aria-hidden="true" /><span><strong>Abra a captura</strong> quando a live estiver pronta.</span></li>
            <li><span><strong>Receba participantes</strong> automaticamente pelo comando.</span></li>
            <li><span><strong>Finalize o evento</strong> para congelar o pool antes do sorteio.</span></li>
            <li><span><strong>Sorteie uma pessoa</strong> com chances iguais e publique o resultado.</span></li>
          </ol>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={isSubmitting}>
              {isSubmitting ? 'Criando rascunho...' : 'Criar giveaway'}
              {!isSubmitting && <ArrowRight size={17} aria-hidden="true" />}
            </button>
            <button type="button" className="btn btn-secondary btn-lg" onClick={() => navigate(-1)} disabled={isSubmitting}>
              Voltar
            </button>
          </div>
        </section>
      </form>
    </div>
  );
}

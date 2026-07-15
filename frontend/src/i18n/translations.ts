import { commonEn } from './locales/en/common';
import { creatorGiveawaysEn } from './locales/en/creatorGiveaways';
import { dashboardEn } from './locales/en/dashboard';
import { loginEn } from './locales/en/login';
import { publicGiveawaysEn } from './locales/en/publicGiveaways';
import { commonPtBR } from './locales/pt-BR/common';
import { creatorGiveawaysPtBR } from './locales/pt-BR/creatorGiveaways';
import { dashboardPtBR } from './locales/pt-BR/dashboard';
import { loginPtBR } from './locales/pt-BR/login';
import { publicGiveawaysPtBR } from './locales/pt-BR/publicGiveaways';

const en = {
  ...commonEn,
  ...creatorGiveawaysEn,
  ...dashboardEn,
  ...loginEn,
  ...publicGiveawaysEn,
} as const;

const ptBR = {
  ...commonPtBR,
  ...creatorGiveawaysPtBR,
  ...dashboardPtBR,
  ...loginPtBR,
  ...publicGiveawaysPtBR,
} as const satisfies Record<keyof typeof en, string>;

export const translations = { en, 'pt-BR': ptBR } as const;

export type Lang = keyof typeof translations;
export type TranslationKey = keyof typeof en;

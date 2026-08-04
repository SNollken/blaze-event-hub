/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Dark mode (default)
        bg: {
          base: '#0B0D0E',
          elevated: '#1A1C1F',
          card: '#15181B',
          input: '#0E0E16',
          sidebar: '#0D0D14',
        },
        border: {
          DEFAULT: '#1E1E2E',
          subtle: '#16162A',
          focus: '#FF6B4A',
        },
        primary: {
          DEFAULT: '#FF6B4A',
          hover: '#E85A3D',
          subtle: 'rgba(255, 107, 74, 0.15)',
        },
        accent: {
          DEFAULT: '#4DB6AC',
          hover: '#3AA89A',
          subtle: 'rgba(77, 182, 172, 0.15)',
        },
        success: {
          DEFAULT: '#4DB6AC',
          subtle: 'rgba(77, 182, 172, 0.15)',
        },
        error: {
          DEFAULT: '#E85A3D',
          subtle: 'rgba(232, 90, 61, 0.15)',
        },
        warning: {
          DEFAULT: '#E8A04A',
          subtle: 'rgba(232, 160, 74, 0.15)',
        },
        text: {
          primary: '#E8E5DE',
          secondary: '#9CA0A6',
          muted: '#64748B',
          inverse: '#0A0A0F',
        },
        // Light mode (via .light class or media query)
        light: {
          bg: {
            base: '#FAFAF7',
            elevated: '#FFFFFF',
            card: '#FFFFFF',
            input: '#F5F5F0',
          },
          border: {
            DEFAULT: '#E5E5E0',
            subtle: '#EDEDED',
            focus: '#E85A3D',
          },
          primary: {
            DEFAULT: '#E85A3D',
            hover: '#D64A2D',
            subtle: 'rgba(232, 90, 61, 0.15)',
          },
          accent: {
            DEFAULT: '#3AA89A',
            subtle: 'rgba(58, 168, 154, 0.15)',
          },
          success: {
            DEFAULT: '#3AA89A',
            subtle: 'rgba(58, 168, 154, 0.15)',
          },
          error: {
            DEFAULT: '#E85A3D',
            subtle: 'rgba(232, 90, 61, 0.15)',
          },
          warning: {
            DEFAULT: '#E8A04A',
            subtle: 'rgba(232, 160, 74, 0.15)',
          },
          text: {
            primary: '#1A1C1F',
            secondary: '#6B6F76',
            muted: '#9CA0A6',
            inverse: '#FAFAF7',
          },
        },
      },
      fontFamily: {
        display: ['Funnel Display', 'system-ui', 'sans-serif'],
        body: ['Funnel Sans', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      fontSize: {
        'display-xl': ['clamp(2.5rem, 5vw, 4rem)', { lineHeight: '1.1', letterSpacing: '-0.02em' }],
        'display-lg': ['clamp(2rem, 4vw, 3rem)', { lineHeight: '1.15', letterSpacing: '-0.01em' }],
        'display-md': ['clamp(1.75rem, 3vw, 2.25rem)', { lineHeight: '1.2' }],
        'display-sm': ['clamp(1.5rem, 2.5vw, 1.75rem)', { lineHeight: '1.25' }],
        'heading-xl': ['1.875rem', { lineHeight: '1.2', letterSpacing: '-0.01em' }],
        'heading-lg': ['1.5rem', { lineHeight: '1.25', letterSpacing: '-0.01em' }],
        'heading-md': ['1.25rem', { lineHeight: '1.3' }],
        'heading-sm': ['1.125rem', { lineHeight: '1.35' }],
        'body-lg': ['1.125rem', { lineHeight: '1.6' }],
        'body': ['1rem', { lineHeight: '1.6' }],
        'body-sm': ['0.875rem', { lineHeight: '1.5' }],
        'caption': ['0.75rem', { lineHeight: '1.5' }],
      },
      spacing: {
        '0': '0',
        '1': '0.25rem',
        '2': '0.5rem',
        '3': '0.75rem',
        '4': '1rem',
        '5': '1.25rem',
        '6': '1.5rem',
        '8': '2rem',
        '10': '2.5rem',
        '12': '3rem',
        '16': '4rem',
        '20': '5rem',
        '24': '6rem',
      },
      borderRadius: {
        'sm': '4px',
        DEFAULT: '8px',
        'lg': '12px',
        'xl': '16px',
        '2xl': '24px',
        'full': '9999px',
      },
      boxShadow: {
        'sm': '0 1px 2px rgba(0, 0, 0, 0.3)',
        DEFAULT: '0 2px 8px rgba(0, 0, 0, 0.4)',
        'lg': '0 8px 32px rgba(0, 0, 0, 0.5)',
        'inner': 'inset 0 2px 4px rgba(0, 0, 0, 0.1)',
      },
      transitionDuration: {
        'instant': '50ms',
        'fast': '150ms',
        'normal': '250ms',
        'slow': '400ms',
      },
      transitionTimingFunction: {
        'standard': 'cubic-bezier(0.2, 0, 0, 1)',
        'emphasized': 'cubic-bezier(0.3, 0, 0, 1)',
      },
      animation: {
        'fade-in': 'fade-in 200ms ease-out',
        'slide-up': 'slide-up 300ms ease-out',
        'slide-down': 'slide-down 200ms ease-out',
        'scale-in': 'scale-in 200ms ease-out',
        'spin': 'spin 1s linear infinite',
        'pulse-soft': 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-down': {
          '0%': { opacity: '0', transform: 'translateY(-10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'scale-in': {
          '0%': { opacity: '0', transform: 'scale(0.95)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        'spin': {
          '0%': { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
      },
    },
  },
  plugins: [],
}
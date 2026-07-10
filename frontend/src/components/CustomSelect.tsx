import { useState, useRef, useEffect, type ReactNode } from 'react';

export interface SelectOption {
  value: string;
  title: string;
  desc?: string;
}

interface CustomSelectProps {
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  width?: number | string;
  children?: ReactNode;
}

export function CustomSelect({ options, value, onChange, width, children }: CustomSelectProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, []);

  const selected = options.find((o) => o.value === value);

  return (
    <div className={`custom-select${open ? ' open' : ''}`} ref={ref} style={width ? { width } : undefined}>
      <button
        type="button"
        className="custom-select__trigger"
        onClick={(e) => { e.stopPropagation(); setOpen(!open); }}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="custom-select__value">{selected?.title || value}</span>
        <svg className="custom-select__chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <div className="custom-select__menu" role="listbox">
        {children ? (
          // For rule-type selects that need custom children
          <>
            {options.map((opt) => (
              <div
                key={opt.value}
                className={`custom-select__option${opt.value === value ? ' selected' : ''}`}
                role="option"
                onClick={() => { onChange(opt.value); setOpen(false); }}
              >
                <span className="custom-select__opt-title">{opt.title}</span>
                {opt.desc && <span className="custom-select__opt-desc">{opt.desc}</span>}
              </div>
            ))}
          </>
        ) : (
          options.map((opt) => (
            <div
              key={opt.value}
              className={`custom-select__option${opt.value === value ? ' selected' : ''}`}
              role="option"
              onClick={() => { onChange(opt.value); setOpen(false); }}
            >
              <span className="custom-select__opt-title">{opt.title}</span>
              {opt.desc && <span className="custom-select__opt-desc">{opt.desc}</span>}
            </div>
          ))
        )}
      </div>
    </div>
  );
}

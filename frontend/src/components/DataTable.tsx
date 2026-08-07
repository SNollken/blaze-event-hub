import { useState, useMemo, ReactNode } from 'react';
import { ArrowUpDown, ArrowUp, ArrowDown, Search } from 'lucide-react';
import { t, getLocale } from '../i18n';

export interface Column<T = unknown> {
  key: string;
  header: string;
  sortable?: boolean;
  render?: (row: T) => ReactNode;
  width?: string | number;
}

interface DataTableProps<T = unknown> {
  columns: Column<T>[];
  data: T[];
  filterable?: boolean;
  filterPlaceholder?: string;
  filterKeys?: string[];
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  ariaLabel?: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function getField(row: any, key: string): unknown {
  if (row != null && typeof row === 'object') return row[key];
  return undefined;
}

export function DataTable<T>({
  columns,
  data,
  filterable = false,
  filterPlaceholder = t('table.filter'),
  filterKeys,
  emptyMessage = t('table.empty'),
  onRowClick,
  ariaLabel,
}: DataTableProps<T>) {
  const [filter, setFilter] = useState('');
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const filtered = useMemo(() => {
    if (!filter) return data;
    const term = filter.toLowerCase();
    const keys = filterKeys || columns.map((c) => c.key);
    return data.filter((row) =>
      keys.some((k) => {
        const val = getField(row, k);
        return val != null && String(val).toLowerCase().includes(term);
      }),
    );
  }, [data, filter, filterKeys, columns]);

  const sorted = useMemo(() => {
    if (!sortKey) return filtered;
    return [...filtered].sort((a, b) => {
      const av = getField(a, sortKey);
      const bv = getField(b, sortKey);
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      const cmp = String(av).localeCompare(String(bv), getLocale(), { numeric: true });
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [filtered, sortKey, sortDir]);

  return (
    <div>
      {filterable && (
        <div className="search-input-wrapper">
          <Search size={14} />
          <input
            className="input !pl-8"
            aria-label={filterPlaceholder}
            placeholder={filterPlaceholder}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
      )}
      <div className="data-table-wrapper">
        <table className="data-table" aria-label={ariaLabel}>
          <thead>
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={col.sortable ? 'cursor-pointer select-none' : undefined}
                  style={{ width: col.width }}
                  onClick={col.sortable ? () => handleSort(col.key) : undefined}
                  tabIndex={col.sortable ? 0 : undefined}
                  onKeyDown={col.sortable ? (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleSort(col.key);
                    }
                  } : undefined}
                  aria-sort={col.sortable ? (sortKey === col.key ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none') : undefined}
                >
                  <span className="inline-flex items-center gap-1">
                    {col.header}
                    {col.sortable && (
                      sortKey === col.key ? (
                        sortDir === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />
                      ) : (
                        <ArrowUpDown size={12} className="opacity-40" />
                      )
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sorted.length === 0 ? (
              <tr>
                <td colSpan={columns.length}>
                  <div className="empty-state">
                    <Search size={24} aria-hidden="true" />
                    {emptyMessage}
                  </div>
                </td>
              </tr>
            ) : (
              sorted.map((row) => {
                const originalIndex = filtered.indexOf(row);
                return (
                <tr
                  key={originalIndex >= 0 ? `row-${originalIndex}` : `row-${row}`}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={onRowClick ? 'cursor-pointer' : undefined}
                >
                  {columns.map((col) => (
                    <td key={col.key}>
                      {col.render
                        ? col.render(row)
                        : String(getField(row, col.key) ?? '')}
                    </td>
                  ))}
                </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

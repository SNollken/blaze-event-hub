import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DataTable, type Column } from '../components/DataTable';

interface Row {
  name: string;
  viewers: number | null;
}

const columns: Column<Row>[] = [
  { key: 'name', header: 'Nome', sortable: true },
  { key: 'viewers', header: 'Espectadores', sortable: true },
];

const data: Row[] = [
  { name: 'zeta', viewers: 30 },
  { name: 'alfa', viewers: null },
  { name: 'meio', viewers: 5 },
];

describe('DataTable', () => {
  it('renderiza cabecalhos e linhas', () => {
    render(<DataTable columns={columns} data={data} ariaLabel="tabela" />);
    expect(screen.getByText('Nome')).toBeInTheDocument();
    expect(screen.getByText('zeta')).toBeInTheDocument();
    expect(screen.getByText('alfa')).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'tabela' });
    expect(within(table).getAllByRole('row')).toHaveLength(4); // header + 3 linhas
  });

  it('estado vazio mostra emptyMessage', () => {
    render(<DataTable columns={columns} data={[]} emptyMessage="Nada aqui" />);
    expect(screen.getByText('Nada aqui')).toBeInTheDocument();
  });

  it('filtro reduz linhas (case-insensitive)', () => {
    render(<DataTable columns={columns} data={data} filterable />);
    const input = screen.getByLabelText('Filtrar...');
    fireEvent.change(input, { target: { value: 'ZET' } });
    expect(screen.getByText('zeta')).toBeInTheDocument();
    expect(screen.queryByText('alfa')).not.toBeInTheDocument();
    expect(screen.queryByText('meio')).not.toBeInTheDocument();
  });

  it('filtro sem correspondencia mostra estado vazio', () => {
    render(<DataTable columns={columns} data={data} filterable emptyMessage="Nada aqui" />);
    fireEvent.change(screen.getByLabelText('Filtrar...'), { target: { value: 'xyz123' } });
    expect(screen.getByText('Nada aqui')).toBeInTheDocument();
  });

  it('click no cabecalho ordena asc, depois desc (aria-sort acompanha)', () => {
    render(<DataTable columns={columns} data={data} />);
    const header = screen.getByText('Nome').closest('th') as HTMLElement;
    expect(header).toHaveAttribute('aria-sort', 'none');

    fireEvent.click(header);
    let rows = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(rows).toEqual(['alfa', 'meio', 'zeta']);
    expect(header).toHaveAttribute('aria-sort', 'ascending');

    fireEvent.click(header);
    rows = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(rows).toEqual(['zeta', 'meio', 'alfa']);
    expect(header).toHaveAttribute('aria-sort', 'descending');
  });

  it('nulls vao para o fim na ordenacao', () => {
    render(<DataTable columns={columns} data={data} />);
    fireEvent.click(screen.getByText('Espectadores').closest('th') as HTMLElement);
    const rows = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);
    // 5, 30 asc; alfa (null) por ultimo
    expect(rows).toEqual(['meio', 'zeta', 'alfa']);
  });

  it('ordenacao numerica (numeric: true) nao usa ordem lexicografica', () => {
    const rows: Row[] = [
      { name: 'b', viewers: 100 },
      { name: 'a', viewers: 20 },
    ];
    render(<DataTable columns={columns} data={rows} />);
    fireEvent.click(screen.getByText('Espectadores').closest('th') as HTMLElement);
    const names = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(names).toEqual(['a', 'b']); // 20 < 100 (lexicografico daria 100 < 20)
  });

  it('Enter no cabecalho sortable ordena (keyboard)', () => {
    render(<DataTable columns={columns} data={data} />);
    const header = screen.getByText('Nome').closest('th') as HTMLElement;
    fireEvent.keyDown(header, { key: 'Enter' });
    const rows = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(rows).toEqual(['alfa', 'meio', 'zeta']);
  });

  it('cabecalho sem sortable nao reage a click', () => {
    const cols: Column<Row>[] = [{ key: 'name', header: 'Nome' }];
    render(<DataTable columns={cols} data={data} />);
    const header = screen.getByText('Nome').closest('th') as HTMLElement;
    fireEvent.click(header);
    expect(header).not.toHaveAttribute('aria-sort');
  });

  it('onRowClick recebe a linha clicada', () => {
    const onRowClick = vi.fn();
    render(<DataTable columns={columns} data={data} onRowClick={onRowClick} />);
    fireEvent.click(screen.getByText('meio'));
    expect(onRowClick).toHaveBeenCalledWith({ name: 'meio', viewers: 5 });
  });

  it('render custom da coluna e usado', () => {
    const cols: Column<Row>[] = [
      { key: 'name', header: 'Nome', render: (row) => <strong>{`**${row.name}**`}</strong> },
    ];
    render(<DataTable columns={cols} data={data} />);
    expect(screen.getByText('**zeta**')).toBeInTheDocument();
  });

  it('filterKeys restringe as colunas buscadas', () => {
    render(<DataTable columns={columns} data={data} filterable filterKeys={['name']} />);
    // '30' existe em viewers mas nao deve casar quando filterKeys so tem name
    fireEvent.change(screen.getByLabelText('Filtrar...'), { target: { value: '30' } });
    expect(screen.getByText('Nenhum registro encontrado.')).toBeInTheDocument();
  });
});

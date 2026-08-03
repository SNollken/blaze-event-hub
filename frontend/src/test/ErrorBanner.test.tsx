import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorBanner } from '../components/ErrorBanner';

describe('ErrorBanner', () => {
	it('renderiza nada quando nao ha erro', () => {
		const { container } = render(<ErrorBanner error={null} />);
		expect(container.firstChild).toBeNull();
	});

	it('mostra mensagem de erro e botao de retry', () => {
		const onRetry = vi.fn();
		render(<ErrorBanner error="Erro de conexao" onRetry={onRetry} />);
		expect(screen.getByText('Erro de conexao')).toBeInTheDocument();
		const retryBtn = screen.getByText('Tentar novamente');
		expect(retryBtn).toBeInTheDocument();
		fireEvent.click(retryBtn);
		expect(onRetry).toHaveBeenCalledOnce();
	});

	it('nao mostra botao de retry quando onRetry nao fornecido', () => {
		render(<ErrorBanner error="Erro" />);
		expect(screen.queryByText('Tentar novamente')).not.toBeInTheDocument();
	});
});

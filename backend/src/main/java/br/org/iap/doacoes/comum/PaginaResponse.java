package br.org.iap.doacoes.comum;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Página de resultados no formato estável da API. */
public record PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

	public static final int TAMANHO_MAXIMO = 100;

	public static <T> PaginaResponse<T> de(Page<T> page) {
		return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	public static Pageable pageable(int pagina, int tamanho, Sort sort) {
		int tamanhoValido = Math.clamp(tamanho, 1, TAMANHO_MAXIMO);
		return PageRequest.of(Math.max(pagina, 0), tamanhoValido, sort);
	}
}

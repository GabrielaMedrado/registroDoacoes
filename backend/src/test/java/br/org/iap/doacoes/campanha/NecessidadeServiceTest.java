package br.org.iap.doacoes.campanha;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.RegraNegocioException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NecessidadeServiceTest {

	private final NecessidadeRepository repositorio = mock(NecessidadeRepository.class);
	private final CampanhaService campanhas = mock(CampanhaService.class);
	private final NecessidadeService service = new NecessidadeService(repositorio, campanhas);

	private final Necessidade arroz = new Necessidade(1L, "Arroz", Unidade.KG, new BigDecimal("50"), null);

	@Test
	void criarEmCampanhaInexistenteLanca404() {
		when(campanhas.obter(99L)).thenThrow(new NaoEncontradoException("Campanha não encontrada."));

		assertThatThrownBy(() -> service.criar(99L,
				new NecessidadeRequest("Arroz", Unidade.KG, new BigDecimal("50"), null)))
				.isInstanceOf(NaoEncontradoException.class);
		verify(repositorio, never()).save(any());
	}

	@Test
	void excluirComDoacoesLanca409() {
		when(repositorio.findById(10L)).thenReturn(Optional.of(arroz));
		when(repositorio.possuiDoacoes(10L)).thenReturn(true);

		assertThatThrownBy(() -> service.excluir(10L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage("Esta necessidade já recebeu doações e não pode ser excluída.");
		verify(repositorio, never()).delete(any());
	}

	@Test
	void excluirSemDoacoesRemove() {
		when(repositorio.findById(10L)).thenReturn(Optional.of(arroz));
		when(repositorio.possuiDoacoes(10L)).thenReturn(false);

		service.excluir(10L);

		verify(repositorio).delete(arroz);
	}
}

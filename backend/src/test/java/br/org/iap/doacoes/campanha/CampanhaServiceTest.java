package br.org.iap.doacoes.campanha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampanhaServiceTest {

	private final CampanhaRepository repositorio = mock(CampanhaRepository.class);
	private final CampanhaService service = new CampanhaService(repositorio);

	@BeforeEach
	void salvarDevolveOMesmo() {
		when(repositorio.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static CampanhaRequest req(String inicio, String fim) {
		return new CampanhaRequest("Inverno solidário", "Agasalhos", LocalDate.parse(inicio),
				fim == null ? null : LocalDate.parse(fim), new BigDecimal("500.00"));
	}

	@Test
	void criarComecaComoRascunhoERegistraAutor() {
		CampanhaResponse criada = service.criar(req("2026-10-20", "2026-11-30"), 7L);

		assertThat(criada.status()).isEqualTo(StatusCampanha.RASCUNHO);
		assertThat(criada.titulo()).isEqualTo("Inverno solidário");
		assertThat(criada.metaValor()).isEqualByComparingTo("500");
	}

	@Test
	void dataFimAntesDoInicioFalhaNoCampoDataFim() {
		assertThatThrownBy(() -> service.criar(req("2026-10-20", "2026-10-19"), 7L))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("A data de término deve ser igual ou posterior à de início.")
				.extracting("campo").isEqualTo("dataFim");
	}

	@Test
	void dataFimIgualAoInicioEhPermitida() {
		assertThatCode(() -> service.criar(req("2026-10-20", "2026-10-20"), 7L)).doesNotThrowAnyException();
	}

	@Test
	void atualizarInexistenteLanca404() {
		when(repositorio.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.atualizar(99L, req("2026-10-20", null)))
				.isInstanceOf(NaoEncontradoException.class)
				.hasMessage("Campanha não encontrada.");
	}

	@Test
	void alterarStatusMudaStatus() {
		Campanha campanha = new Campanha("C", null, LocalDate.parse("2026-10-20"), null, null, 7L);
		when(repositorio.findById(1L)).thenReturn(Optional.of(campanha));

		assertThat(service.alterarStatus(1L, StatusCampanha.ATIVA).status()).isEqualTo(StatusCampanha.ATIVA);
		assertThat(service.alterarStatus(1L, StatusCampanha.ENCERRADA).status()).isEqualTo(StatusCampanha.ENCERRADA);
		assertThat(service.alterarStatus(1L, StatusCampanha.ATIVA).status()).isEqualTo(StatusCampanha.ATIVA);
	}
}

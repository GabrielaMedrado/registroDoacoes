package br.org.iap.doacoes.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.campanha.CampanhaRepository;
import br.org.iap.doacoes.campanha.StatusCampanha;
import br.org.iap.doacoes.campanha.Unidade;
import br.org.iap.doacoes.doacao.DoacaoMontador;
import br.org.iap.doacoes.doacao.DoacaoRepository;
import br.org.iap.doacoes.progresso.CalculoProgresso;
import br.org.iap.doacoes.progresso.NecessidadeProgresso;
import br.org.iap.doacoes.progresso.ProgressoCampanhaResponse;
import br.org.iap.doacoes.progresso.ProgressoService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendenciasTextoTest {

	private final ProgressoService progresso = mock(ProgressoService.class);
	private final CampanhaRepository campanhas = mock(CampanhaRepository.class);
	private RelatorioService service;

	@BeforeEach
	void preparar() {
		service = new RelatorioService(progresso, mock(DoacaoRepository.class), mock(DoacaoMontador.class), campanhas);
		when(campanhas.findByStatusOrderByTituloAsc(StatusCampanha.ATIVA)).thenReturn(List.of());
	}

	private static NecessidadeProgresso necessidade(String item, Unidade unidade, String meta, String arrecadado) {
		BigDecimal m = new BigDecimal(meta);
		BigDecimal a = new BigDecimal(arrecadado);
		return new NecessidadeProgresso(1L, item, unidade, m, null, a, CalculoProgresso.pendente(m, a),
				CalculoProgresso.excedente(m, a), CalculoProgresso.percentual(m, a));
	}

	private static ProgressoCampanhaResponse campanha(String titulo, NecessidadeProgresso... necessidades) {
		return new ProgressoCampanhaResponse(1L, titulo, StatusCampanha.ATIVA, null, BigDecimal.ZERO, null, null,
				List.of(necessidades));
	}

	private void campanhasAtivas(ProgressoCampanhaResponse... lista) {
		when(progresso.dasCampanhas(anyList())).thenReturn(List.of(lista));
	}

	@Test
	void textoNoFormatoFixado() {
		campanhasAtivas(
				campanha("Campanha do Agasalho", necessidade("Feijão", Unidade.KG, "5", "2.5"),
						necessidade("Arroz", Unidade.KG, "10", "5")),
				campanha("Natal Solidário", necessidade("Brinquedo", Unidade.UNIDADE, "20", "0")));

		assertThat(service.pendencias(null)).isEqualTo("""
				*Campanha do Agasalho*
				Ainda precisamos de:
				• Arroz: 5 kg
				• Feijão: 2,5 kg

				*Natal Solidário*
				Ainda precisamos de:
				• Brinquedo: 20 unidade(s)""");
	}

	@Test
	void campanhaSemPendenciaFicaDeFora() {
		campanhasAtivas(
				campanha("Completa", necessidade("Arroz", Unidade.KG, "10", "12")),
				campanha("Natal", necessidade("Óleo", Unidade.LITRO, "4", "1")));

		assertThat(service.pendencias(null)).isEqualTo("*Natal*\nAinda precisamos de:\n• Óleo: 3 litro(s)");
	}

	@Test
	void semPendenciasMostraMensagem() {
		campanhasAtivas(campanha("Completa", necessidade("Arroz", Unidade.KG, "10", "10")), campanha("Vazia"));

		assertThat(service.pendencias(null)).isEqualTo("Nenhuma pendência nas campanhas ativas.");
	}
}

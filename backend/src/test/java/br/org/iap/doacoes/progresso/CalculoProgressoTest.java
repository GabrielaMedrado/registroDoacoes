package br.org.iap.doacoes.progresso;

import static org.assertj.core.api.Assertions.assertThat;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculoProgressoTest {

	private static BigDecimal bd(String valor) {
		return new BigDecimal(valor);
	}

	private static NecessidadeProgresso necessidade(String meta, String arrecadado) {
		BigDecimal m = bd(meta);
		BigDecimal a = bd(arrecadado);
		return new NecessidadeProgresso(1L, "Arroz", Unidade.KG, m, null, a, CalculoProgresso.pendente(m, a),
				CalculoProgresso.excedente(m, a), CalculoProgresso.percentual(m, a));
	}

	@Test
	void metadeDaMeta() {
		assertThat(CalculoProgresso.pendente(bd("10"), bd("5"))).isEqualByComparingTo("5");
		assertThat(CalculoProgresso.excedente(bd("10"), bd("5"))).isEqualByComparingTo("0");
		assertThat(CalculoProgresso.percentual(bd("10"), bd("5"))).isEqualTo(50);
	}

	@Test
	void acimaDaMetaLimitaEm100() {
		assertThat(CalculoProgresso.pendente(bd("10"), bd("15"))).isEqualByComparingTo("0");
		assertThat(CalculoProgresso.excedente(bd("10"), bd("15"))).isEqualByComparingTo("5");
		assertThat(CalculoProgresso.percentual(bd("10"), bd("15"))).isEqualTo(100);
	}

	@Test
	void quaseCompletoNaoArredondaPara100() {
		assertThat(CalculoProgresso.percentual(bd("10"), bd("9.99"))).isEqualTo(99);
	}

	@Test
	void fracaoArredondaParaBaixo() {
		assertThat(CalculoProgresso.percentual(bd("3"), bd("2"))).isEqualTo(66);
	}

	@Test
	void semDoacoes() {
		assertThat(CalculoProgresso.pendente(bd("10"), BigDecimal.ZERO)).isEqualByComparingTo("10");
		assertThat(CalculoProgresso.percentual(bd("10"), BigDecimal.ZERO)).isZero();
	}

	@Test
	void geralEhMediaDasFracoes() {
		assertThat(CalculoProgresso.percentualGeral(List.of(necessidade("10", "5"), necessidade("4", "6"))))
				.isEqualTo(75);
		// Média das frações (1/3 e 2/3 → 50), não dos inteiros já arredondados (33 e 66 → 49).
		assertThat(CalculoProgresso.percentualGeral(List.of(necessidade("3", "1"), necessidade("3", "2"))))
				.isEqualTo(50);
	}

	@Test
	void geralSemNecessidadesEhNulo() {
		assertThat(CalculoProgresso.percentualGeral(List.of())).isNull();
	}

	@Test
	void percentualValor() {
		assertThat(CalculoProgresso.percentualValor(bd("200"), bd("50"))).isEqualTo(25);
		assertThat(CalculoProgresso.percentualValor(bd("200"), bd("300"))).isEqualTo(100);
		assertThat(CalculoProgresso.percentualValor(null, bd("50"))).isNull();
	}
}

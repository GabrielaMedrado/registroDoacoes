package br.org.iap.doacoes.comum;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FormatosTest {

	@Test
	void quantidade() {
		assertThat(Formatos.quantidade(new BigDecimal("2.50"))).isEqualTo("2,5");
		assertThat(Formatos.quantidade(new BigDecimal("1500.00"))).isEqualTo("1500");
		assertThat(Formatos.quantidade(new BigDecimal("0.25"))).isEqualTo("0,25");
	}

	@Test
	void moeda() {
		assertThat(Formatos.moeda(new BigDecimal("1234.5"))).isEqualTo("R$ 1.234,50");
		assertThat(Formatos.moeda(BigDecimal.ZERO)).isEqualTo("R$ 0,00");
	}

	@Test
	void numero() {
		assertThat(Formatos.numero(new BigDecimal("75.5"))).isEqualTo("75,50");
		assertThat(Formatos.numero(new BigDecimal("1234.5"))).isEqualTo("1234,50");
	}

	@Test
	void data() {
		assertThat(Formatos.data(LocalDate.of(2026, 10, 5))).isEqualTo("05/10/2026");
	}
}

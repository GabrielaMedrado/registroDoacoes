package br.org.iap.doacoes.comum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

	private static String texto(CsvWriter csv) {
		byte[] bytes = csv.paraBytes();
		return new String(Arrays.copyOfRange(bytes, 3, bytes.length), StandardCharsets.UTF_8);
	}

	@Test
	void comecaComBom() {
		byte[] bytes = new CsvWriter().linha("a").paraBytes();

		assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
	}

	@Test
	void separaPorPontoEVirgulaComCrlf() {
		assertThat(texto(new CsvWriter().linha("a", "b").linha("c", null))).isEqualTo("a;b\r\nc;\r\n");
	}

	@Test
	void escapaPontoEVirgulaAspasEQuebra() {
		assertThat(texto(new CsvWriter().linha("Silva; \"Zé\"", "a\nb")))
				.isEqualTo("\"Silva; \"\"Zé\"\"\";\"a\nb\"\r\n");
	}

	@Test
	void neutralizaFormula() {
		assertThat(texto(new CsvWriter().linha("=HYPERLINK(x)", "+1", "-1", "@a")))
				.isEqualTo("'=HYPERLINK(x);'+1;'-1;'@a\r\n");
	}

	@Test
	void acentosSobrevivem() {
		assertThat(texto(new CsvWriter().linha("Feijão", "Doação"))).isEqualTo("Feijão;Doação\r\n");
	}
}

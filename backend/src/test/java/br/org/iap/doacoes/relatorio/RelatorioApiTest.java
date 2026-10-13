package br.org.iap.doacoes.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.progresso.CenarioApiTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelatorioApiTest extends CenarioApiTestSupport {

	private long agasalho;
	private long arroz;
	private long feijao;

	@BeforeEach
	void preparar() throws Exception {
		agasalho = criarCampanha("Agasalho", "200");
		mudarStatus(agasalho, "ATIVA");
		arroz = criarNecessidade(agasalho, "Arroz", "KG", "10");
		feijao = criarNecessidade(agasalho, "Feijão", "KG", "4");
	}

	private String csv(String url) throws Exception {
		byte[] corpo = consultar(url, voluntario).andReturn().getResponse().getContentAsByteArray();
		return new String(corpo, 3, corpo.length - 3, StandardCharsets.UTF_8);
	}

	@Test
	void relatorioDaCampanhaEmJson() throws Exception {
		doarItem(agasalho, arroz, "5");

		consultar("/api/relatorios/campanha/" + agasalho, voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Agasalho"))
				.andExpect(jsonPath("$.necessidades[0].pendente").value(5.0));
	}

	@Test
	void relatorioDaCampanhaEmCsv() throws Exception {
		doarItem(agasalho, arroz, "5");
		doarItem(agasalho, feijao, "2.5");
		doarDinheiro(agasalho, "50", "PIX");
		doarDinheiro(agasalho, "25.50", "ESPECIE");

		consultar("/api/relatorios/campanha/" + agasalho + "?formato=csv", voluntario)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", startsWith("text/csv")))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"campanha-" + agasalho + ".csv\""));
		assertThat(csv("/api/relatorios/campanha/" + agasalho + "?formato=csv"))
				.isEqualTo("Campanha;Agasalho\r\n"
						+ "Item;Unidade;Meta;Arrecadado;Pendente;Excedente;Percentual\r\n"
						+ "Arroz;kg;10,00;5,00;5,00;0,00;50\r\n"
						+ "Feijão;kg;4,00;2,50;1,50;0,00;62\r\n"
						+ "Total em dinheiro;75,50\r\n"
						+ "Meta em dinheiro;200,00\r\n");
	}

	@Test
	void campanhaInexistenteRetorna404() throws Exception {
		consultar("/api/relatorios/campanha/999999", voluntario).andExpect(status().isNotFound());
	}

	@Test
	void relatorioDeDoacoesFiltraEOrdena() throws Exception {
		doarItem(agasalho, arroz, "1", "2026-09-01");
		doarItem(agasalho, arroz, "2", "2026-09-20");
		doarItem(agasalho, feijao, "3", "2026-09-10");
		doarItem(agasalho, arroz, "4", "2026-09-10");
		doarDinheiro(agasalho, "50", "PIX", null, "2026-09-05");

		consultar("/api/relatorios/doacoes?de=2026-09-05&ate=2026-09-20", voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.de").value("2026-09-05"))
				.andExpect(jsonPath("$.ate").value("2026-09-20"))
				.andExpect(jsonPath("$.quantidade").value(4))
				.andExpect(jsonPath("$.doacoes[*].dataDoacao")
						.value(contains("2026-09-05", "2026-09-10", "2026-09-10", "2026-09-20")))
				.andExpect(jsonPath("$.doacoes[0].descricao").value("PIX"))
				.andExpect(jsonPath("$.doacoes[0].valor").value(50.0))
				.andExpect(jsonPath("$.doacoes[1].descricao").value("Feijão 3 kg"))
				.andExpect(jsonPath("$.totalDinheiro").value(50.0))
				.andExpect(jsonPath("$.totaisItens[*].item").value(contains("Arroz", "Feijão")))
				.andExpect(jsonPath("$.totaisItens[0].quantidade").value(6.0))
				.andExpect(jsonPath("$.totaisItens[0].unidade").value("KG"));
	}

	@Test
	void relatorioDeDoacoesFiltraPorCampanhaETipo() throws Exception {
		long natal = criarCampanhaAtiva("Natal");
		doarDinheiro(natal, "10", "PIX");
		doarDinheiro(agasalho, "20", "PIX");
		doarItem(agasalho, arroz, "1");

		consultar("/api/relatorios/doacoes?de=2026-10-01&ate=2026-10-31&campanhaId=" + agasalho
				+ "&tipo=DINHEIRO", voluntario)
				.andExpect(jsonPath("$.quantidade").value(1))
				.andExpect(jsonPath("$.totalDinheiro").value(20.0))
				.andExpect(jsonPath("$.totaisItens.length()").value(0));
	}

	@Test
	void relatorioDeDoacoesAnonimaMostraAnonimo() throws Exception {
		doarItem(agasalho, arroz, "1");
		long maria = criarDoador("Maria");
		doarDinheiro(agasalho, "10", "OUTRO", maria, DATA);

		consultar("/api/relatorios/doacoes?de=" + DATA + "&ate=" + DATA, voluntario)
				.andExpect(jsonPath("$.doacoes[*].doadorNome").value(contains("Anônimo", "Maria")))
				.andExpect(jsonPath("$.doacoes[1].descricao").value("Outro"))
				.andExpect(jsonPath("$.doacoes[0].campanhaTitulo").value("Agasalho"));
	}

	@Test
	void relatorioDeDoacoesEmCsv() throws Exception {
		doarItem(agasalho, arroz, "5");
		long silva = criarDoador("Silva; \"Zé\"");
		doarDinheiro(agasalho, "50", "PIX", silva, DATA);

		consultar("/api/relatorios/doacoes?de=" + DATA + "&ate=" + DATA + "&formato=csv", voluntario)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", startsWith("text/csv")))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"doacoes-2026-10-10-a-2026-10-10.csv\""));
		assertThat(csv("/api/relatorios/doacoes?de=" + DATA + "&ate=" + DATA + "&formato=csv"))
				.isEqualTo("Data;Campanha;Doador;Tipo;Descrição;Valor\r\n"
						+ "10/10/2026;Agasalho;Anônimo;Itens;Arroz 5 kg;\r\n"
						+ "10/10/2026;Agasalho;\"Silva; \"\"Zé\"\"\";Dinheiro;PIX;50,00\r\n"
						+ "Total em dinheiro;;;;;50,00\r\n");
	}

	@Test
	void periodoObrigatorio() throws Exception {
		consultar("/api/relatorios/doacoes?ate=2026-10-10", voluntario)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("de"))
				.andExpect(jsonPath("$.erros[0].mensagem").value("Informe a data inicial."));
		consultar("/api/relatorios/doacoes?de=2026-10-10", voluntario)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("ate"))
				.andExpect(jsonPath("$.erros[0].mensagem").value("Informe a data final."));
	}

	@Test
	void periodoInvertidoRetorna400() throws Exception {
		consultar("/api/relatorios/doacoes?de=2026-10-20&ate=2026-10-10", voluntario)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("ate"))
				.andExpect(jsonPath("$.erros[0].mensagem").value("A data final não pode ser anterior à inicial."));
	}

	@Test
	void pendenciasEmTextoPuro() throws Exception {
		doarItem(agasalho, arroz, "5");

		consultar("/api/relatorios/pendencias", voluntario)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
				.andExpect(content().string(containsString("*Agasalho*\nAinda precisamos de:\n• Arroz: 5 kg\n")));
	}

	@Test
	void pendenciasFiltraPorCampanha() throws Exception {
		long natal = criarCampanhaAtiva("Natal");
		criarNecessidade(natal, "Brinquedo", "UNIDADE", "20");

		consultar("/api/relatorios/pendencias?campanhaId=" + natal, voluntario)
				.andExpect(content().string("*Natal*\nAinda precisamos de:\n• Brinquedo: 20 unidade(s)"));
	}

	@Test
	void campanhaEncerradaNaoTemPendencias() throws Exception {
		mudarStatus(agasalho, "ENCERRADA");

		consultar("/api/relatorios/pendencias?campanhaId=" + agasalho, voluntario)
				.andExpect(content().string("Nenhuma pendência nas campanhas ativas."));
	}

	@Test
	void csvNaoQuebraComTituloDeFormula() throws Exception {
		long formula = criarCampanhaAtiva("=HYPERLINK(1)");

		assertThat(csv("/api/relatorios/campanha/" + formula + "?formato=csv"))
				.startsWith("Campanha;'=HYPERLINK(1)\r\n");
		consultar("/api/relatorios/campanha/" + formula + "?formato=csv", voluntario)
				.andExpect(content().string(containsString("Total em dinheiro;0,00")));
	}
}

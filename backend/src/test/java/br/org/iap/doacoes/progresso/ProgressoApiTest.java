package br.org.iap.doacoes.progresso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgressoApiTest extends CenarioApiTestSupport {

	private long agasalho;
	private long arroz;
	private long feijao;

	@BeforeEach
	void preparar() throws Exception {
		agasalho = criarCampanhaAtiva("Agasalho");
		arroz = criarNecessidade(agasalho, "Arroz", "KG", "10");
		feijao = criarNecessidade(agasalho, "Feijão", "KG", "4");
	}

	private String url(long campanha) {
		return "/api/campanhas/" + campanha + "/progresso";
	}

	@Test
	void progressoSomaItensEDinheiro() throws Exception {
		doarItem(agasalho, arroz, "3");
		doarItem(agasalho, arroz, "2");
		doarItem(agasalho, feijao, "6");
		doarDinheiro(agasalho, "50", "PIX");
		doarDinheiro(agasalho, "25.50", "ESPECIE");

		consultar(url(agasalho), voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.campanhaId").value(agasalho))
				.andExpect(jsonPath("$.titulo").value("Agasalho"))
				.andExpect(jsonPath("$.status").value("ATIVA"))
				.andExpect(jsonPath("$.necessidades[0].item").value("Arroz"))
				.andExpect(jsonPath("$.necessidades[0].arrecadado").value(5.0))
				.andExpect(jsonPath("$.necessidades[0].pendente").value(5.0))
				.andExpect(jsonPath("$.necessidades[0].percentual").value(50))
				.andExpect(jsonPath("$.necessidades[1].item").value("Feijão"))
				.andExpect(jsonPath("$.necessidades[1].pendente").value(0.0))
				.andExpect(jsonPath("$.necessidades[1].excedente").value(2.0))
				.andExpect(jsonPath("$.necessidades[1].percentual").value(100))
				.andExpect(jsonPath("$.percentualGeral").value(75))
				.andExpect(jsonPath("$.totalDinheiro").value(75.5));
	}

	@Test
	void necessidadeSemDoacaoVemComZero() throws Exception {
		consultar(url(agasalho), voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.necessidades[0].arrecadado").value(0))
				.andExpect(jsonPath("$.necessidades[0].pendente").value(10.0))
				.andExpect(jsonPath("$.necessidades[0].unidade").value("KG"))
				.andExpect(jsonPath("$.necessidades[0].quantidadeMeta").value(10.0))
				.andExpect(jsonPath("$.percentualGeral").value(0));
	}

	@Test
	void campanhaSemNecessidadesNaoTemPercentualGeral() throws Exception {
		long vazia = criarCampanhaAtiva("Vazia");

		consultar(url(vazia), voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.percentualGeral").value((Object) null))
				.andExpect(jsonPath("$.necessidades.length()").value(0))
				.andExpect(jsonPath("$.totalDinheiro").value(0))
				.andExpect(jsonPath("$.percentualValor").value((Object) null));
	}

	@Test
	void doacoesDeOutraCampanhaNaoContam() throws Exception {
		long natal = criarCampanhaAtiva("Natal");
		long arrozNatal = criarNecessidade(natal, "Arroz", "KG", "10");
		doarItem(natal, arrozNatal, "7");
		doarDinheiro(natal, "100", "PIX");

		consultar(url(agasalho), voluntario)
				.andExpect(jsonPath("$.necessidades[0].arrecadado").value(0))
				.andExpect(jsonPath("$.totalDinheiro").value(0));
	}

	@Test
	void percentualValorUsaMetaDaCampanha() throws Exception {
		long comMeta = criarCampanha("Reforma", "200");
		mudarStatus(comMeta, "ATIVA");
		doarDinheiro(comMeta, "50", "PIX");

		consultar(url(comMeta), voluntario)
				.andExpect(jsonPath("$.metaValor").value(200.0))
				.andExpect(jsonPath("$.percentualValor").value(25));
	}

	@Test
	void campanhaInexistenteRetorna404() throws Exception {
		consultar(url(999_999), voluntario).andExpect(status().isNotFound());
	}

	@Test
	void semLoginRetorna401() throws Exception {
		mvc.perform(get(url(agasalho))).andExpect(status().isUnauthorized());
	}
}

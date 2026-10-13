package br.org.iap.doacoes.progresso;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class PainelApiTest extends CenarioApiTestSupport {

	@Test
	void painelListaSoCampanhasAtivasEmOrdemDeTitulo() throws Exception {
		criarCampanhaAtiva("Natal");
		criarCampanhaAtiva("Agasalho");
		criarCampanha("Páscoa", null);
		long inverno = criarCampanhaAtiva("Inverno");
		mudarStatus(inverno, "ENCERRADA");

		consultar("/api/painel", voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.campanhasAtivas[*].titulo").value(contains("Agasalho", "Natal")));
	}

	@Test
	void maioresPendenciasPorMenorPercentual() throws Exception {
		long agasalho = criarCampanhaAtiva("Agasalho");
		criarNecessidade(agasalho, "Arroz", "KG", "10");
		long feijao = criarNecessidade(agasalho, "Feijão", "KG", "4");
		long oleo = criarNecessidade(agasalho, "Óleo", "LITRO", "2");
		doarItem(agasalho, feijao, "2");
		doarItem(agasalho, oleo, "2");

		consultar("/api/painel", voluntario)
				.andExpect(jsonPath("$.maioresPendencias[*].item").value(contains("Arroz", "Feijão")))
				.andExpect(jsonPath("$.maioresPendencias[0].campanhaTitulo").value("Agasalho"))
				.andExpect(jsonPath("$.maioresPendencias[0].campanhaId").value(agasalho))
				.andExpect(jsonPath("$.maioresPendencias[0].pendente").value(10.0))
				.andExpect(jsonPath("$.maioresPendencias[0].unidade").value("KG"))
				.andExpect(jsonPath("$.maioresPendencias[1].percentual").value(50))
				.andExpect(jsonPath("$.campanhasAtivas[0].percentualGeral").value(50));
	}

	@Test
	void maioresPendenciasLimitadasA10() throws Exception {
		long agasalho = criarCampanhaAtiva("Agasalho");
		for (int i = 1; i <= 12; i++) {
			criarNecessidade(agasalho, "Item " + i, "UNIDADE", "5");
		}

		consultar("/api/painel", voluntario).andExpect(jsonPath("$.maioresPendencias.length()").value(10));
	}

	@Test
	void pendenciasDeCampanhaEncerradaNaoAparecem() throws Exception {
		long inverno = criarCampanhaAtiva("Inverno");
		criarNecessidade(inverno, "Cobertor", "UNIDADE", "5");
		mudarStatus(inverno, "ENCERRADA");

		consultar("/api/painel", voluntario).andExpect(jsonPath("$.maioresPendencias.length()").value(0));
	}

	@Test
	void painelVazio() throws Exception {
		consultar("/api/painel", voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.campanhasAtivas.length()").value(0))
				.andExpect(jsonPath("$.maioresPendencias.length()").value(0));
	}
}

package br.org.iap.doacoes.campanha;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class CampanhaApiTest extends ApiTestSupport {

	private static final String CAMPANHA =
			"{\"titulo\":\"Inverno solidário\",\"descricao\":\"Agasalhos\",\"dataInicio\":\"2026-10-20\","
					+ "\"dataFim\":\"2026-11-30\",\"metaValor\":500.00}";

	private MockHttpSession admin;
	private MockHttpSession voluntario;

	@BeforeEach
	void preparar() throws Exception {
		limparBanco();
		criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
		admin = login("ana@igreja.org");
		voluntario = login("vitor@igreja.org");
	}

	private long criarCampanha(String json) throws Exception {
		String corpo = enviar(post("/api/campanhas"), admin, json)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	@Test
	void voluntarioListaEConsultaCampanhas() throws Exception {
		long id = criarCampanha(CAMPANHA);

		consultar("/api/campanhas", voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1));
		consultar("/api/campanhas/" + id, voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Inverno solidário"));
	}

	@Test
	void voluntarioNaoCriaNemEditaNemMudaStatus() throws Exception {
		long id = criarCampanha(CAMPANHA);

		enviar(post("/api/campanhas"), voluntario, CAMPANHA).andExpect(status().isForbidden());
		enviar(put("/api/campanhas/" + id), voluntario, CAMPANHA).andExpect(status().isForbidden());
		enviar(patch("/api/campanhas/" + id + "/status"), voluntario, "{\"status\":\"ATIVA\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCriaCampanha() throws Exception {
		enviar(post("/api/campanhas"), admin, CAMPANHA)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.metaValor").value(500.0))
				.andExpect(jsonPath("$.dataInicio").value("2026-10-20"))
				.andExpect(jsonPath("$.dataFim").value("2026-11-30"));
	}

	@Test
	void filtraPorStatus() throws Exception {
		long ativa = criarCampanha(CAMPANHA);
		criarCampanha(CAMPANHA);
		enviar(patch("/api/campanhas/" + ativa + "/status"), admin, "{\"status\":\"ATIVA\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ATIVA"));

		consultar("/api/campanhas?status=ATIVA", voluntario)
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].id").value(ativa));
	}

	@Test
	void tituloEmBrancoRetorna400() throws Exception {
		enviar(post("/api/campanhas"), admin, CAMPANHA.replace("Inverno solidário", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("titulo"));
	}

	@Test
	void metaNegativaRetorna400() throws Exception {
		enviar(post("/api/campanhas"), admin, CAMPANHA.replace("500.00", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("metaValor"));
	}

	@Test
	void dataFimAntesDoInicioRetorna400() throws Exception {
		enviar(post("/api/campanhas"), admin, CAMPANHA.replace("2026-11-30", "2026-10-19"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("dataFim"));
	}

	@Test
	void campanhaInexistenteRetorna404() throws Exception {
		consultar("/api/campanhas/999999", voluntario)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Campanha não encontrada."));
	}

	@Test
	void statusInvalidoRetorna400() throws Exception {
		long id = criarCampanha(CAMPANHA);

		enviar(patch("/api/campanhas/" + id + "/status"), admin, "{\"status\":\"XYZ\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Requisição inválida."));
	}
}

package br.org.iap.doacoes.campanha;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class NecessidadeApiTest extends ApiTestSupport {

	private MockHttpSession admin;
	private MockHttpSession voluntario;
	private Usuario ana;
	private long campanhaId;

	@BeforeEach
	void preparar() throws Exception {
		limparBanco();
		ana = criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
		admin = login("ana@igreja.org");
		voluntario = login("vitor@igreja.org");
		String corpo = enviar(post("/api/campanhas"), admin,
				"{\"titulo\":\"Cesta básica\",\"dataInicio\":\"2026-10-20\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		campanhaId = ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private static String necessidade(String item, String unidade, String quantidade) {
		return "{\"item\":\"" + item + "\",\"unidade\":\"" + unidade + "\",\"quantidadeMeta\":" + quantidade + "}";
	}

	private long criarNecessidade(String item) throws Exception {
		String corpo = enviar(post("/api/campanhas/" + campanhaId + "/necessidades"), admin,
				necessidade(item, "KG", "50"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	@Test
	void adminCriaEListaNecessidadesOrdenadasPorItem() throws Exception {
		criarNecessidade("Feijão");
		criarNecessidade("Arroz");

		consultar("/api/campanhas/" + campanhaId + "/necessidades", voluntario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].item").value("Arroz"))
				.andExpect(jsonPath("$[0].quantidadeMeta").value(50.0))
				.andExpect(jsonPath("$[0].unidade").value("KG"))
				.andExpect(jsonPath("$[0].campanhaId").value(campanhaId));
	}

	@Test
	void voluntarioListaMasNaoAltera() throws Exception {
		long id = criarNecessidade("Arroz");

		consultar("/api/campanhas/" + campanhaId + "/necessidades", voluntario).andExpect(status().isOk());
		enviar(post("/api/campanhas/" + campanhaId + "/necessidades"), voluntario, necessidade("X", "KG", "1"))
				.andExpect(status().isForbidden());
		enviar(put("/api/necessidades/" + id), voluntario, necessidade("X", "KG", "1"))
				.andExpect(status().isForbidden());
		enviar(delete("/api/necessidades/" + id), voluntario, null).andExpect(status().isForbidden());
	}

	@Test
	void adminEditaNecessidade() throws Exception {
		long id = criarNecessidade("Arroz");

		enviar(put("/api/necessidades/" + id), admin, necessidade("Arroz integral", "PACOTE", "2.5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.item").value("Arroz integral"))
				.andExpect(jsonPath("$.unidade").value("PACOTE"))
				.andExpect(jsonPath("$.quantidadeMeta").value(2.5));
	}

	@Test
	void quantidadeZeroRetorna400() throws Exception {
		enviar(post("/api/campanhas/" + campanhaId + "/necessidades"), admin, necessidade("Arroz", "KG", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("quantidadeMeta"));
	}

	@Test
	void unidadeInvalidaRetorna400() throws Exception {
		enviar(post("/api/campanhas/" + campanhaId + "/necessidades"), admin, necessidade("Arroz", "TONELADA", "1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void excluirNecessidadeComDoacaoRetorna409() throws Exception {
		long id = criarNecessidade("Arroz");
		Long doacaoId = jdbc.queryForObject(
				"insert into doacao (campanha_id, tipo, data_doacao, registrado_por) "
						+ "values (?, 'ITEM', current_date, ?) returning id",
				Long.class, campanhaId, ana.getId());
		jdbc.update("insert into item_doacao (doacao_id, necessidade_id, quantidade) values (?, ?, 5)",
				doacaoId, id);

		enviar(delete("/api/necessidades/" + id), admin, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Esta necessidade já recebeu doações e não pode ser excluída."));
	}

	@Test
	void excluirNecessidadeSemDoacaoRetorna204() throws Exception {
		long id = criarNecessidade("Arroz");

		enviar(delete("/api/necessidades/" + id), admin, null).andExpect(status().isNoContent());

		consultar("/api/campanhas/" + campanhaId + "/necessidades", admin).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void necessidadesDeCampanhaInexistenteRetorna404() throws Exception {
		consultar("/api/campanhas/999999/necessidades", voluntario)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Campanha não encontrada."));
	}
}

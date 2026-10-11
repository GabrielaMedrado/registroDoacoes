package br.org.iap.doacoes.doacao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class DoacaoApiTest extends ApiTestSupport {

	protected MockHttpSession admin;
	protected MockHttpSession voluntario;
	protected long campanhaId;
	protected long arrozId;
	protected long feijaoId;
	protected final String hoje = LocalDate.now().toString();

	@BeforeEach
	void preparar() throws Exception {
		limparBanco();
		criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
		admin = login("ana@igreja.org");
		voluntario = login("vitor@igreja.org");
		campanhaId = criarCampanhaAtiva("Cesta básica");
		arrozId = criarNecessidade(campanhaId, "Arroz");
		feijaoId = criarNecessidade(campanhaId, "Feijão");
	}

	protected long id(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	protected long criarCampanhaAtiva(String titulo) throws Exception {
		long id = id(enviar(post("/api/campanhas"), admin,
				"{\"titulo\":\"" + titulo + "\",\"dataInicio\":\"2026-01-01\"}").andExpect(status().isCreated()));
		mudarStatus(id, "ATIVA");
		return id;
	}

	protected void mudarStatus(long campanha, String status) throws Exception {
		enviar(patch("/api/campanhas/" + campanha + "/status"), admin, "{\"status\":\"" + status + "\"}")
				.andExpect(status().isOk());
	}

	protected long criarNecessidade(long campanha, String item) throws Exception {
		return id(enviar(post("/api/campanhas/" + campanha + "/necessidades"), admin,
				"{\"item\":\"" + item + "\",\"unidade\":\"KG\",\"quantidadeMeta\":50}")
				.andExpect(status().isCreated()));
	}

	protected String doacaoDeItens(long campanha, String data, long necessidade, String quantidade) {
		return "{\"campanhaId\":" + campanha + ",\"tipo\":\"ITEM\",\"dataDoacao\":\"" + data
				+ "\",\"itens\":[{\"necessidadeId\":" + necessidade + ",\"quantidade\":" + quantidade + "}]}";
	}

	protected String doacaoEmDinheiro(long campanha, String data, Long doador, String valor) {
		return "{\"campanhaId\":" + campanha + ",\"doadorId\":" + doador + ",\"tipo\":\"DINHEIRO\",\"valor\":" + valor
				+ ",\"formaPagamento\":\"PIX\",\"dataDoacao\":\"" + data + "\"}";
	}

	protected ResultActions registrar(MockHttpSession sessao, String json) throws Exception {
		return enviar(post("/api/doacoes"), sessao, json);
	}

	@Test
	void voluntarioRegistraDoacaoAnonimaDeItens() throws Exception {
		registrar(voluntario, "{\"campanhaId\":" + campanhaId + ",\"tipo\":\"ITEM\",\"dataDoacao\":\"" + hoje
				+ "\",\"itens\":[{\"necessidadeId\":" + arrozId + ",\"quantidade\":5},"
				+ "{\"necessidadeId\":" + feijaoId + ",\"quantidade\":2.5}]}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.doadorId").doesNotExist())
				.andExpect(jsonPath("$.itens[0].item").value("Arroz"))
				.andExpect(jsonPath("$.itens[1].quantidade").value(2.5))
				.andExpect(jsonPath("$.registradoPorNome").value("Vitor"))
				.andExpect(jsonPath("$.campanhaTitulo").value("Cesta básica"))
				.andExpect(jsonPath("$.podeAlterar").value(true));
	}

	@Test
	void registraDoacaoEmDinheiroDeDoadorCadastrado() throws Exception {
		long maria = id(enviar(post("/api/doadores"), voluntario, "{\"nome\":\"Maria\"}"));

		registrar(voluntario, doacaoEmDinheiro(campanhaId, hoje, maria, "50"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.valor").value(50.0))
				.andExpect(jsonPath("$.formaPagamento").value("PIX"))
				.andExpect(jsonPath("$.doadorNome").value("Maria"))
				.andExpect(jsonPath("$.itens.length()").value(0));
	}

	@Test
	void campanhaEncerradaRetorna409() throws Exception {
		mudarStatus(campanhaId, "ENCERRADA");

		registrar(voluntario, doacaoDeItens(campanhaId, hoje, arrozId, "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(DoacaoServiceTest.MSG_NAO_ATIVA));
	}

	@Test
	void itemDeOutraCampanhaRetorna400() throws Exception {
		long outra = criarCampanhaAtiva("Inverno");
		long agasalho = criarNecessidade(outra, "Agasalho");

		registrar(voluntario, doacaoDeItens(campanhaId, hoje, agasalho, "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("itens"));
	}

	@Test
	void quantidadeZeroRetorna400() throws Exception {
		registrar(voluntario, doacaoDeItens(campanhaId, hoje, arrozId, "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("itens[0].quantidade"));
	}

	@Test
	void doacaoGravaNoBancoComCheckRespeitado() throws Exception {
		long doacao = id(registrar(voluntario, doacaoDeItens(campanhaId, hoje, arrozId, "3"))
				.andExpect(status().isCreated()));

		Map<String, Object> linha = jdbc.queryForMap("select valor, forma_pagamento from doacao where id = ?", doacao);
		Assertions.assertThat(linha.get("valor")).isNull();
		Assertions.assertThat(linha.get("forma_pagamento")).isNull();
	}
}

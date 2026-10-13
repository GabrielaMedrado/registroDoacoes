package br.org.iap.doacoes.progresso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Monta campanhas, necessidades e doações pela API para os testes de progresso, painel e relatórios. */
public abstract class CenarioApiTestSupport extends ApiTestSupport {

	protected static final String DATA = "2026-10-10";

	protected MockHttpSession admin;
	protected MockHttpSession voluntario;

	@BeforeEach
	void prepararUsuarios() throws Exception {
		limparBanco();
		criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
		admin = login("ana@igreja.org");
		voluntario = login("vitor@igreja.org");
	}

	protected long id(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	protected long criarCampanha(String titulo, String metaValor) throws Exception {
		String meta = metaValor == null ? "" : ",\"metaValor\":" + metaValor;
		return id(enviar(post("/api/campanhas"), admin,
				"{\"titulo\":\"" + titulo + "\",\"dataInicio\":\"2026-01-01\"" + meta + "}")
				.andExpect(status().isCreated()));
	}

	protected long criarCampanhaAtiva(String titulo) throws Exception {
		long id = criarCampanha(titulo, null);
		mudarStatus(id, "ATIVA");
		return id;
	}

	protected void mudarStatus(long campanha, String status) throws Exception {
		enviar(patch("/api/campanhas/" + campanha + "/status"), admin, "{\"status\":\"" + status + "\"}")
				.andExpect(status().isOk());
	}

	protected long criarNecessidade(long campanha, String item, String unidade, String meta) throws Exception {
		return id(enviar(post("/api/campanhas/" + campanha + "/necessidades"), admin,
				"{\"item\":\"" + item + "\",\"unidade\":\"" + unidade + "\",\"quantidadeMeta\":" + meta + "}")
				.andExpect(status().isCreated()));
	}

	protected long doarItem(long campanha, long necessidade, String quantidade, String data) throws Exception {
		return id(enviar(post("/api/doacoes"), voluntario, "{\"campanhaId\":" + campanha
				+ ",\"tipo\":\"ITEM\",\"dataDoacao\":\"" + data + "\",\"itens\":[{\"necessidadeId\":" + necessidade
				+ ",\"quantidade\":" + quantidade + "}]}").andExpect(status().isCreated()));
	}

	protected long doarItem(long campanha, long necessidade, String quantidade) throws Exception {
		return doarItem(campanha, necessidade, quantidade, DATA);
	}

	protected long doarDinheiro(long campanha, String valor, String forma, Long doador, String data)
			throws Exception {
		return id(enviar(post("/api/doacoes"), voluntario, "{\"campanhaId\":" + campanha + ",\"doadorId\":" + doador
				+ ",\"tipo\":\"DINHEIRO\",\"valor\":" + valor + ",\"formaPagamento\":\"" + forma
				+ "\",\"dataDoacao\":\"" + data + "\"}").andExpect(status().isCreated()));
	}

	protected long doarDinheiro(long campanha, String valor, String forma) throws Exception {
		return doarDinheiro(campanha, valor, forma, null, DATA);
	}

	protected long criarDoador(String nome) throws Exception {
		return id(enviar(post("/api/doadores"), voluntario, "{\"nome\":\"" + nome.replace("\"", "\\\"") + "\"}")
				.andExpect(status().isCreated()));
	}
}

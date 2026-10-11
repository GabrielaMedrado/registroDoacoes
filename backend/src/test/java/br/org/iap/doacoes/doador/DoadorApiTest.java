package br.org.iap.doacoes.doador;

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

class DoadorApiTest extends ApiTestSupport {

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

	protected long criarDoador(MockHttpSession sessao, String nome, String telefone) throws Exception {
		String corpo = enviar(post("/api/doadores"), sessao,
				"{\"nome\":\"" + nome + "\",\"telefone\":\"" + telefone + "\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	@Test
	void voluntarioCadastraEEditaDoador() throws Exception {
		long id = criarDoador(voluntario, "Maria Souza", "(47) 99999-1234");

		enviar(put("/api/doadores/" + id), voluntario,
				"{\"nome\":\"Maria S. Souza\",\"email\":\"maria@exemplo.org\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Maria S. Souza"))
				.andExpect(jsonPath("$.email").value("maria@exemplo.org"));
	}

	@Test
	void nomeEmBrancoRetorna400() throws Exception {
		enviar(post("/api/doadores"), voluntario, "{\"nome\":\" \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("nome"));
	}

	@Test
	void buscaPorNomeIgnoraCaixa() throws Exception {
		criarDoador(voluntario, "Maria Souza", "");
		criarDoador(voluntario, "José Lima", "");

		consultar("/api/doadores?busca=maria", voluntario)
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].nome").value("Maria Souza"));
	}

	@Test
	void buscaPorTelefoneIgnoraFormatacao() throws Exception {
		criarDoador(voluntario, "Maria Souza", "(47) 99999-1234");
		criarDoador(voluntario, "José Lima", "(47) 98888-0000");

		consultar("/api/doadores?busca=47 99999-1234", voluntario).andExpect(jsonPath("$.total").value(1));
		consultar("/api/doadores?busca=999991234", voluntario)
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].nome").value("Maria Souza"));
	}

	@Test
	void voluntarioNaoAnonimiza() throws Exception {
		long id = criarDoador(voluntario, "Maria Souza", "(47) 99999-1234");

		enviar(post("/api/doadores/" + id + "/anonimizar"), voluntario, null).andExpect(status().isForbidden());
	}

	@Test
	void adminAnonimizaDoador() throws Exception {
		long id = criarDoador(voluntario, "Maria Souza", "(47) 99999-1234");

		enviar(post("/api/doadores/" + id + "/anonimizar"), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Anônimo"))
				.andExpect(jsonPath("$.telefone").doesNotExist());
	}

	@Test
	void doadorAnonimizadoNaoPodeSerEditado() throws Exception {
		long id = criarDoador(voluntario, "Maria Souza", "(47) 99999-1234");
		enviar(post("/api/doadores/" + id + "/anonimizar"), admin, null)
				.andExpect(jsonPath("$.anonimizado").value(true));

		enviar(put("/api/doadores/" + id), voluntario, "{\"nome\":\"Maria Souza\",\"telefone\":\"(47) 99999-1234\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Este doador foi anonimizado e não pode ser alterado."));
		consultar("/api/doadores/" + id, voluntario).andExpect(jsonPath("$.nome").value("Anônimo"));
	}
}

package br.org.iap.doacoes.usuario;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuarioApiTest extends ApiTestSupport {

	private Usuario ana;
	private Usuario vitor;

	@BeforeEach
	void preparar() {
		limparBanco();
		ana = criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		vitor = criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
	}

	private static String atualizacao(String nome, String email, String papel, boolean ativo) {
		return "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"papel\":\"" + papel
				+ "\",\"ativo\":" + ativo + "}";
	}

	@Test
	void voluntarioNaoListaUsuarios() throws Exception {
		consultar("/api/usuarios", login("vitor@igreja.org")).andExpect(status().isForbidden());
	}

	@Test
	void voluntarioNaoCriaUsuario() throws Exception {
		enviar(post("/api/usuarios"), login("vitor@igreja.org"),
				"{\"nome\":\"X\",\"email\":\"x@igreja.org\",\"papel\":\"ADMIN\",\"senha\":\"senha-forte-1\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void adminListaUsuariosPaginadoPorNome() throws Exception {
		consultar("/api/usuarios", login("ana@igreja.org"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itens[0].nome").value("Ana"))
				.andExpect(jsonPath("$.itens[1].nome").value("Vitor"))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.itens[0]", not(hasKey("senhaHash"))));
	}

	@Test
	void adminCriaUsuarioQuePodeEntrar() throws Exception {
		enviar(post("/api/usuarios"), login("ana@igreja.org"),
				"{\"nome\":\"Bia\",\"email\":\"Bia@Igreja.org\",\"papel\":\"VOLUNTARIO\",\"senha\":\"senha-da-bia\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("bia@igreja.org"))
				.andExpect(jsonPath("$.ativo").value(true));

		login("bia@igreja.org", "senha-da-bia");
	}

	@Test
	void emailDuplicadoComCaixaDiferenteRetorna400() throws Exception {
		enviar(post("/api/usuarios"), login("ana@igreja.org"),
				"{\"nome\":\"Outro\",\"email\":\"Vitor@Igreja.ORG\",\"papel\":\"VOLUNTARIO\","
						+ "\"senha\":\"senha-forte-1\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("email"))
				.andExpect(jsonPath("$.erros[0].mensagem").value("Já existe um usuário com este e-mail."));
	}

	@Test
	void adminDesativandoASiMesmoRetorna409() throws Exception {
		enviar(put("/api/usuarios/" + ana.getId()), login("ana@igreja.org"),
				atualizacao("Ana", "ana@igreja.org", "ADMIN", false))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail")
						.value("Você não pode desativar a própria conta nem alterar o próprio papel."));
	}

	@Test
	void usuarioDesativadoNaoConsegueEntrar() throws Exception {
		enviar(put("/api/usuarios/" + vitor.getId()), login("ana@igreja.org"),
				atualizacao("Vitor", "vitor@igreja.org", "VOLUNTARIO", false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ativo").value(false));

		tentarLogin("vitor@igreja.org", SENHA).andExpect(status().isUnauthorized());
	}

	@Test
	void redefinirSenhaPermiteLoginComANova() throws Exception {
		enviar(post("/api/usuarios/" + vitor.getId() + "/redefinir-senha"), login("ana@igreja.org"),
				"{\"novaSenha\":\"nova-senha-vitor\"}")
				.andExpect(status().isNoContent());

		login("vitor@igreja.org", "nova-senha-vitor");
	}

	@Test
	void tamanhoAcimaDe100ELimitado() throws Exception {
		consultar("/api/usuarios?tamanho=500", login("ana@igreja.org"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tamanho").value(100));
	}
}

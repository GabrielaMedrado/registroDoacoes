package br.org.iap.doacoes.auth;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiTest extends ApiTestSupport {

	private static final String MSG_INVALIDO = "E-mail ou senha inválidos.";

	@BeforeEach
	void prepararUsuarios() {
		limparBanco();
		criarUsuario("Maria", "maria@igreja.org", Papel.VOLUNTARIO);
		Usuario inativo = criarUsuario("Inativo", "inativo@igreja.org", Papel.VOLUNTARIO);
		inativo.setAtivo(false);
		usuarios.save(inativo);
	}

	private static String corpoLogin(String email, String senha) {
		return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
	}

	private ResultActions trocarSenha(MockHttpSession sessao, String atual, String nova) throws Exception {
		return mvc.perform(put("/api/auth/minha-senha").session(sessao).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}"));
	}

	@Test
	void csrfDevolveCookieXsrf() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isNoContent())
				.andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void meSemSessaoRetorna401() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginSemCsrfRetorna403() throws Exception {
		mvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoLogin("maria@igreja.org", SENHA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void loginValidoRetornaUsuarioEAbreSessao() throws Exception {
		MockHttpSession sessao = (MockHttpSession) tentarLogin("maria@igreja.org", SENHA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("maria@igreja.org"))
				.andExpect(jsonPath("$.papel").value("VOLUNTARIO"))
				.andExpect(jsonPath("$", not(hasKey("senha"))))
				.andExpect(jsonPath("$", not(hasKey("senhaHash"))))
				.andReturn().getRequest().getSession(false);

		mvc.perform(get("/api/auth/me").session(sessao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Maria"));
	}

	@Test
	void loginAceitaEmailComCaixaEEspacos() throws Exception {
		tentarLogin(" Maria@Igreja.ORG ", SENHA).andExpect(status().isOk());
	}

	@Test
	void loginSenhaErradaRetorna401Generico() throws Exception {
		tentarLogin("maria@igreja.org", "errada-123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MSG_INVALIDO));
	}

	@Test
	void loginEmailInexistenteRetornaMesmaMensagem() throws Exception {
		tentarLogin("ninguem@igreja.org", SENHA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MSG_INVALIDO));
	}

	@Test
	void loginUsuarioInativoRetornaMesmaMensagem() throws Exception {
		tentarLogin("inativo@igreja.org", SENHA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MSG_INVALIDO));
	}

	@Test
	void loginEmailMalformadoRetorna400ComErros() throws Exception {
		tentarLogin("abc", SENHA)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("email"));
	}

	@Test
	void bloqueiaAposCincoFalhasMesmoComSenhaCorreta() throws Exception {
		for (int i = 0; i < 5; i++) {
			tentarLogin("maria@igreja.org", "errada-123").andExpect(status().isUnauthorized());
		}

		tentarLogin("maria@igreja.org", SENHA)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 15 minutos."));
	}

	@Test
	void logoutInvalidaSessao() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		mvc.perform(post("/api/auth/logout").session(sessao).with(csrf()))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
	}

	@Test
	void minhaSenhaComSenhaAtualErradaRetorna400() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		trocarSenha(sessao, "errada-123", "nova-senha-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("senhaAtual"))
				.andExpect(jsonPath("$.erros[0].mensagem").value("Senha atual incorreta."));
	}

	@Test
	void minhaSenhaCurtaRetorna400() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		trocarSenha(sessao, SENHA, "1234567")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("novaSenha"));
	}

	@Test
	void minhaSenhaComMaisDe72BytesRetorna400() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		// 40 caracteres acentuados = 80 bytes em UTF-8, acima do limite do BCrypt
		trocarSenha(sessao, SENHA, "á".repeat(40))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erros[0].campo").value("novaSenha"));
	}

	@Test
	void loginComSenhaMaiorQue72BytesRetorna401Generico() throws Exception {
		tentarLogin("maria@igreja.org", "a".repeat(100))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(MSG_INVALIDO));
	}

	@Test
	void minhaSenhaTrocaEPermiteNovoLogin() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		trocarSenha(sessao, SENHA, "nova-senha-1").andExpect(status().isNoContent());

		tentarLogin("maria@igreja.org", "nova-senha-1").andExpect(status().isOk());
		tentarLogin("maria@igreja.org", SENHA).andExpect(status().isUnauthorized());
	}

	@Test
	void trocarAPropriaSenhaMantemASessaoAtual() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		trocarSenha(sessao, SENHA, "nova-senha-1").andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isOk());
	}

	@Test
	void minhaSenhaSemSessaoRetorna401() throws Exception {
		mvc.perform(put("/api/auth/minha-senha").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"a\",\"novaSenha\":\"12345678\"}"))
				.andExpect(status().isUnauthorized());
	}
}

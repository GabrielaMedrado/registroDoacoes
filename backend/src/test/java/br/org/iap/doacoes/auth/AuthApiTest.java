package br.org.iap.doacoes.auth;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.TestcontainersConfiguration;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

	private static final String SENHA = "senha-forte-1";
	private static final String MSG_INVALIDO = "E-mail ou senha inválidos.";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private LoginAttemptService tentativas;

	@BeforeEach
	void prepararUsuarios() {
		usuarios.deleteAll();
		usuarios.save(new Usuario("Maria", "maria@igreja.org", encoder.encode(SENHA), Papel.VOLUNTARIO));
		Usuario inativo = new Usuario("Inativo", "inativo@igreja.org", encoder.encode(SENHA), Papel.VOLUNTARIO);
		inativo.setAtivo(false);
		usuarios.save(inativo);
		tentativas.registrarSucesso("maria@igreja.org");
	}

	/**
	 * Faz o mesmo que o frontend: obtém o cookie XSRF-TOKEN e o devolve no cabeçalho X-XSRF-TOKEN.
	 * (O csrf() do spring-security-test troca o repositório do filtro compartilhado e mascara o cookie real.)
	 */
	private RequestPostProcessor csrf() throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return request -> {
			request.setCookies(token);
			request.addHeader("X-XSRF-TOKEN", token.getValue());
			return request;
		};
	}

	private static String corpoLogin(String email, String senha) {
		return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
	}

	private ResultActions tentarLogin(String email, String senha) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoLogin(email, senha)));
	}

	private MockHttpSession login(String email, String senha) throws Exception {
		return (MockHttpSession) tentarLogin(email, senha)
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
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
	void minhaSenhaTrocaEPermiteNovoLogin() throws Exception {
		MockHttpSession sessao = login("maria@igreja.org", SENHA);

		trocarSenha(sessao, SENHA, "nova-senha-1").andExpect(status().isNoContent());

		tentarLogin("maria@igreja.org", "nova-senha-1").andExpect(status().isOk());
		tentarLogin("maria@igreja.org", SENHA).andExpect(status().isUnauthorized());
	}

	@Test
	void minhaSenhaSemSessaoRetorna401() throws Exception {
		mvc.perform(put("/api/auth/minha-senha").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"senhaAtual\":\"a\",\"novaSenha\":\"12345678\"}"))
				.andExpect(status().isUnauthorized());
	}
}

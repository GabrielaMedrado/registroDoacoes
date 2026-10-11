package br.org.iap.doacoes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.auth.LoginAttemptService;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Base dos testes de API: banco real (Testcontainers), login por sessão e CSRF como o frontend faz. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

	protected static final String SENHA = "senha-forte-1";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected UsuarioRepository usuarios;

	@Autowired
	protected PasswordEncoder encoder;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected LoginAttemptService tentativas;

	/** Apaga todos os dados respeitando as chaves estrangeiras. */
	protected void limparBanco() {
		jdbc.execute("truncate item_doacao, doacao, doador, necessidade, campanha, usuario restart identity cascade");
	}

	protected Usuario criarUsuario(String nome, String email, Papel papel) {
		tentativas.registrarSucesso(email);
		return usuarios.save(new Usuario(nome, email, encoder.encode(SENHA), papel));
	}

	/**
	 * Faz o mesmo que o frontend: obtém o cookie XSRF-TOKEN e o devolve no cabeçalho X-XSRF-TOKEN.
	 * (O csrf() do spring-security-test troca o repositório do filtro compartilhado e mascara o cookie real.)
	 */
	protected RequestPostProcessor csrf() throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return request -> {
			request.setCookies(token);
			request.addHeader("X-XSRF-TOKEN", token.getValue());
			return request;
		};
	}

	protected ResultActions tentarLogin(String email, String senha) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
	}

	protected MockHttpSession login(String email, String senha) throws Exception {
		return (MockHttpSession) tentarLogin(email, senha)
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}

	protected MockHttpSession login(String email) throws Exception {
		return login(email, SENHA);
	}

	/** Executa uma requisição autenticada que altera dados, com CSRF e corpo JSON (ou sem corpo se null). */
	protected ResultActions enviar(MockHttpServletRequestBuilder requisicao, MockHttpSession sessao, String json)
			throws Exception {
		requisicao.session(sessao).with(csrf());
		if (json != null) {
			requisicao.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(requisicao);
	}

	protected ResultActions consultar(String url, MockHttpSession sessao) throws Exception {
		return mvc.perform(get(url).session(sessao));
	}
}

package br.org.iap.doacoes.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** O build do React fica em classpath:/static; as fixtures de teste fazem esse papel aqui. */
class SpaConfigTest extends ApiTestSupport {

	private static final String RAIZ_REACT = "id=\"root\"";

	@BeforeEach
	void preparar() {
		limparBanco();
		criarUsuario("Vera", "vera@exemplo.org", Papel.VOLUNTARIO);
	}

	@Test
	void raizServeOIndex() throws Exception {
		// Na raiz quem responde é a página de boas-vindas do Boot, com forward (o MockMvc não o segue).
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));
	}

	@Test
	void rotaDoReactServeOIndex() throws Exception {
		mvc.perform(get("/campanhas/5"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString(RAIZ_REACT)));
	}

	@Test
	void rotaAninhadaServeOIndex() throws Exception {
		mvc.perform(get("/doacoes/3/editar"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(RAIZ_REACT)));
	}

	@Test
	void arquivoExistenteEServido() throws Exception {
		mvc.perform(get("/assets/app.js"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", containsString("javascript")))
				.andExpect(content().string(containsString("console.log('ok')")));
	}

	@Test
	void arquivoInexistenteDa404() throws Exception {
		mvc.perform(get("/assets/antigo-123.js")).andExpect(status().isNotFound());
	}

	@Test
	void apiInexistenteNaoServeOIndex() throws Exception {
		consultar("/api/nao-existe", login("vera@exemplo.org"))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString(RAIZ_REACT))));
	}

	@Test
	void apiSemSessaoContinua401() throws Exception {
		mvc.perform(get("/api/painel")).andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorNaoExpostoNaoServeOIndex() throws Exception {
		mvc.perform(get("/actuator/env"))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString(RAIZ_REACT))));
	}
}

package br.org.iap.doacoes.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("prod")
class OpenApiProdTest extends ApiTestSupport {

	@Test
	void apiDocsDesligadoEmProducao() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiDesligadoEmProducao() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}
}

package br.org.iap.doacoes.config;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiTest extends ApiTestSupport {

	@Test
	void especificacaoPublicaContemOsEndpoints() throws Exception {
		String json = mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("API de Gestão de Doações — IAP Joinville"))
				.andExpect(jsonPath("$.paths", hasKey("/api/doacoes")))
				.andExpect(jsonPath("$.paths", hasKey("/api/campanhas/{id}/progresso")))
				.andExpect(jsonPath("$.paths", hasKey("/api/painel")))
				.andExpect(jsonPath("$.paths", hasKey("/api/relatorios/pendencias")))
				.andExpect(jsonPath("$.components.securitySchemes.sessao.in").value("cookie"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// Publicado pela release (Plano 5) no GitHub Pages.
		Files.writeString(Path.of("target", "openapi.json"), json, StandardCharsets.UTF_8);
	}

	@Test
	void swaggerUiDesligadoForaDoDev() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}
}

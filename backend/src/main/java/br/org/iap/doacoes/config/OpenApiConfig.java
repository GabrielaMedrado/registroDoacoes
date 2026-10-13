package br.org.iap.doacoes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentação da API (springdoc). Swagger UI só no perfil dev; JSON desligado em prod. */
@Configuration
public class OpenApiConfig {

	private static final String DESCRICAO = """
			API do sistema de gestão de doações e necessidades das campanhas sociais da Igreja Adventista \
			da Promessa de Joinville-SC.

			Autenticação por sessão: faça `POST /api/auth/login` e use o cookie `JSESSIONID` devolvido. \
			Requisições que alteram dados (POST, PUT, PATCH, DELETE) precisam do cabeçalho `X-XSRF-TOKEN` \
			com o valor do cookie `XSRF-TOKEN` (obtido em `GET /api/auth/csrf`).

			Erros seguem o ProblemDetail (RFC 9457); erros de validação trazem `erros: [{campo, mensagem}]`.""";

	@Bean
	public OpenAPI openApi(ObjectProvider<BuildProperties> build) {
		BuildProperties propriedades = build.getIfAvailable();
		String versao = propriedades == null ? "dev" : propriedades.getVersion();
		return new OpenAPI()
				.info(new Info().title("API de Gestão de Doações — IAP Joinville").version(versao)
						.description(DESCRICAO))
				.components(new Components().addSecuritySchemes("sessao", new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")))
				.addSecurityItem(new SecurityRequirement().addList("sessao"));
	}
}

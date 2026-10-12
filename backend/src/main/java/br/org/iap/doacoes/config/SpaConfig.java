package br.org.iap.doacoes.config;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o build do React embutido em {@code classpath:/static}. As rotas do React (ex.: {@code /campanhas/5})
 * só existem no navegador: ao recarregar a página nelas, o servidor devolve o {@code index.html} e o React Router
 * assume. Arquivos inexistentes (com extensão) e caminhos da API continuam dando 404, para um JS antigo ou uma URL
 * de API errada nunca receberem o HTML no lugar.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

	private static final List<String> PREFIXOS_RESERVADOS = List.of("api/", "actuator/", "v3/", "swagger-ui");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new IndexComoPadrao());
	}

	private static final class IndexComoPadrao extends PathResourceResolver {

		@Override
		protected Resource getResource(String caminho, Resource local) throws IOException {
			Resource arquivo = local.createRelative(caminho);
			if (!caminho.isEmpty() && arquivo.isReadable()) {
				return arquivo;
			}
			if (temExtensao(caminho) || PREFIXOS_RESERVADOS.stream().anyMatch(caminho::startsWith)) {
				return null;
			}
			Resource index = local.createRelative("index.html");
			return index.isReadable() ? index : null;
		}

		private static boolean temExtensao(String caminho) {
			return caminho.substring(caminho.lastIndexOf('/') + 1).contains(".");
		}
	}
}

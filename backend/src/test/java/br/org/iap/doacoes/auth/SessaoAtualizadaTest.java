package br.org.iap.doacoes.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.org.iap.doacoes.ApiTestSupport;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Desativar ou mudar o papel de alguém vale na próxima requisição, sem esperar novo login. */
class SessaoAtualizadaTest extends ApiTestSupport {

	private Usuario vitor;
	private Usuario bia;

	@BeforeEach
	void preparar() {
		limparBanco();
		criarUsuario("Ana", "ana@igreja.org", Papel.ADMIN);
		vitor = criarUsuario("Vitor", "vitor@igreja.org", Papel.VOLUNTARIO);
		bia = criarUsuario("Bia", "bia@igreja.org", Papel.ADMIN);
	}

	private void alterarDiretoNoBanco(Usuario usuario, Papel papel, boolean ativo) {
		usuario.setPapel(papel);
		usuario.setAtivo(ativo);
		usuarios.save(usuario);
	}

	@Test
	void usuarioDesativadoPerdeASessaoNaProximaRequisicao() throws Exception {
		MockHttpSession sessao = login("vitor@igreja.org");

		alterarDiretoNoBanco(vitor, Papel.VOLUNTARIO, false);

		consultar("/api/campanhas", sessao).andExpect(status().isUnauthorized());
		consultar("/api/auth/me", sessao).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioPromovidoAAdminGanhaAcessoSemNovoLogin() throws Exception {
		MockHttpSession sessao = login("vitor@igreja.org");
		consultar("/api/usuarios", sessao).andExpect(status().isForbidden());

		alterarDiretoNoBanco(vitor, Papel.ADMIN, true);

		consultar("/api/usuarios", sessao).andExpect(status().isOk());
	}

	@Test
	void adminRebaixadoPerdeAcessoImediatamente() throws Exception {
		MockHttpSession sessao = login("bia@igreja.org");
		consultar("/api/usuarios", sessao).andExpect(status().isOk());

		alterarDiretoNoBanco(bia, Papel.VOLUNTARIO, true);

		consultar("/api/usuarios", sessao).andExpect(status().isForbidden());
	}
}

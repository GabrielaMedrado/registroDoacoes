package br.org.iap.doacoes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminInicialRunnerTest {

	private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	private AdminInicialRunner runner(String email, String senha) {
		return new AdminInicialRunner(repositorio, encoder, email, senha);
	}

	@Test
	void criaAdminQuandoBancoVazio() throws Exception {
		when(repositorio.count()).thenReturn(0L);

		runner("Admin@Igreja.org", "segredo123").run(null);

		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(repositorio).save(captor.capture());
		Usuario admin = captor.getValue();
		assertThat(admin.getEmail()).isEqualTo("admin@igreja.org");
		assertThat(admin.getNome()).isEqualTo("Administrador");
		assertThat(admin.getPapel()).isEqualTo(Papel.ADMIN);
		assertThat(admin.isAtivo()).isTrue();
		assertThat(encoder.matches("segredo123", admin.getSenhaHash())).isTrue();
	}

	@Test
	void naoFazNadaQuandoJaExisteUsuario() throws Exception {
		when(repositorio.count()).thenReturn(1L);

		runner("admin@igreja.org", "segredo123").run(null);

		verify(repositorio, never()).save(any());
	}

	@Test
	void naoQuebraQuandoVariaveisAusentes() {
		when(repositorio.count()).thenReturn(0L);

		assertThatCode(() -> runner("", "").run(null)).doesNotThrowAnyException();
		verify(repositorio, never()).save(any());
	}

	@Test
	void falhaComMensagemClaraSeSenhaCurta() {
		when(repositorio.count()).thenReturn(0L);

		assertThatThrownBy(() -> runner("admin@igreja.org", "1234567").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("ADMIN_SENHA deve ter ao menos 8 caracteres");
		verify(repositorio, never()).save(any());
	}
}

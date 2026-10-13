package br.org.iap.doacoes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.RegraNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioServiceTest {

	private static final String MSG_PROPRIA_CONTA =
			"Você não pode desativar a própria conta nem alterar o próprio papel.";

	private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();
	private final UsuarioService service = new UsuarioService(repositorio, encoder);

	private Usuario admin;

	@BeforeEach
	void prepararAdmin() {
		admin = new Usuario("Ana", "ana@igreja.org", encoder.encode("senha-forte-1"), Papel.ADMIN);
		ReflectionTestUtils.setField(admin, "id", 5L);
		when(repositorio.findById(5L)).thenReturn(Optional.of(admin));
		when(repositorio.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void criarNormalizaEmailESalvaHash() {
		UsuarioResponse criado = service.criar(
				new CriarUsuarioRequest("João", " Joao@Igreja.org ", Papel.VOLUNTARIO, "senha-forte-1"));

		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(repositorio).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("joao@igreja.org");
		assertThat(encoder.matches("senha-forte-1", captor.getValue().getSenhaHash())).isTrue();
		assertThat(criado.email()).isEqualTo("joao@igreja.org");
		assertThat(criado.ativo()).isTrue();
	}

	@Test
	void criarComEmailExistenteFalhaNoCampoEmail() {
		when(repositorio.existsByEmail("maria@igreja.org")).thenReturn(true);

		assertThatThrownBy(() -> service.criar(
				new CriarUsuarioRequest("Maria", "Maria@Igreja.org", Papel.VOLUNTARIO, "senha-forte-1")))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Já existe um usuário com este e-mail.")
				.extracting("campo").isEqualTo("email");
		verify(repositorio, never()).save(any());
	}

	@Test
	void criarComSenhaCurtaFalhaNoCampoSenha() {
		assertThatThrownBy(() -> service.criar(
				new CriarUsuarioRequest("João", "joao@igreja.org", Papel.VOLUNTARIO, "1234567")))
				.isInstanceOf(CampoInvalidoException.class)
				.extracting("campo").isEqualTo("senha");
	}

	@Test
	void atualizarComEmailDeOutroUsuarioFalha() {
		when(repositorio.existsByEmailAndIdNot("maria@igreja.org", 5L)).thenReturn(true);

		assertThatThrownBy(() -> service.atualizar(5L,
				new AtualizarUsuarioRequest("Ana", "maria@igreja.org", Papel.ADMIN, true), 5L))
				.isInstanceOf(CampoInvalidoException.class)
				.extracting("campo").isEqualTo("email");
	}

	@Test
	void adminNaoPodeDesativarASiMesmo() {
		assertThatThrownBy(() -> service.atualizar(5L,
				new AtualizarUsuarioRequest("Ana", "ana@igreja.org", Papel.ADMIN, false), 5L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(MSG_PROPRIA_CONTA);
	}

	@Test
	void adminNaoPodeTirarOProprioPapel() {
		assertThatThrownBy(() -> service.atualizar(5L,
				new AtualizarUsuarioRequest("Ana", "ana@igreja.org", Papel.VOLUNTARIO, true), 5L))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessage(MSG_PROPRIA_CONTA);
	}

	@Test
	void adminPodeEditarOProprioNome() {
		UsuarioResponse atualizado = service.atualizar(5L,
				new AtualizarUsuarioRequest("Ana Paula", "ana@igreja.org", Papel.ADMIN, true), 5L);

		assertThat(atualizado.nome()).isEqualTo("Ana Paula");
	}

	@Test
	void atualizarInexistenteLanca404() {
		when(repositorio.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.atualizar(99L,
				new AtualizarUsuarioRequest("X", "x@igreja.org", Papel.VOLUNTARIO, true), 5L))
				.isInstanceOf(NaoEncontradoException.class)
				.hasMessage("Usuário não encontrado.");
	}

	@Test
	void redefinirSenhaGravaNovoHash() {
		service.redefinirSenha(5L, "nova-senha-1");

		assertThat(encoder.matches("nova-senha-1", admin.getSenhaHash())).isTrue();
	}

	@Test
	void redefinirSenhaLongaDemaisFalhaNoCampoNovaSenha() {
		assertThatThrownBy(() -> service.redefinirSenha(5L, "á".repeat(40)))
				.isInstanceOf(CampoInvalidoException.class)
				.extracting("campo").isEqualTo("novaSenha");
	}
}

package br.org.iap.doacoes.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioDetailsServiceTest {

	private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
	private final UsuarioDetailsService service = new UsuarioDetailsService(repositorio);

	private Usuario usuario(boolean ativo) {
		Usuario u = new Usuario("Maria", "maria@igreja.org", "hash", Papel.VOLUNTARIO);
		ReflectionTestUtils.setField(u, "id", 7L);
		u.setAtivo(ativo);
		return u;
	}

	@Test
	void carregaPorEmailNormalizado() {
		when(repositorio.findByEmail("maria@igreja.org")).thenReturn(Optional.of(usuario(true)));

		UserDetails u = service.loadUserByUsername("  Maria@Igreja.org ");

		assertThat(u.getUsername()).isEqualTo("maria@igreja.org");
		assertThat(u.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_VOLUNTARIO");
		assertThat(((UsuarioAutenticado) u).getId()).isEqualTo(7L);
		assertThat(((UsuarioAutenticado) u).getNome()).isEqualTo("Maria");
		assertThat(u.isEnabled()).isTrue();
	}

	@Test
	void usuarioInativoFicaDesabilitado() {
		when(repositorio.findByEmail("maria@igreja.org")).thenReturn(Optional.of(usuario(false)));

		assertThat(service.loadUserByUsername("maria@igreja.org").isEnabled()).isFalse();
	}

	@Test
	void emailInexistenteLancaUsernameNotFound() {
		when(repositorio.findByEmail("x@igreja.org")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("x@igreja.org"))
				.isInstanceOf(UsernameNotFoundException.class);
	}
}

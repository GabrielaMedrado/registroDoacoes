package br.org.iap.doacoes.doador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.comum.NaoEncontradoException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoadorServiceTest {

	private final DoadorRepository repositorio = mock(DoadorRepository.class);
	private final DoadorService service = new DoadorService(repositorio);

	@BeforeEach
	void salvarDevolveOMesmo() {
		when(repositorio.save(any(Doador.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void criarGravaCamposVaziosComoNulo() {
		DoadorResponse criado = service.criar(new DoadorRequest(" Maria Souza ", " ", "", null));

		assertThat(criado.nome()).isEqualTo("Maria Souza");
		assertThat(criado.telefone()).isNull();
		assertThat(criado.email()).isNull();
		assertThat(criado.observacao()).isNull();
	}

	@Test
	void anonimizarApagaDadosPessoais() {
		Doador maria = new Doador("Maria", "(47) 99999-1234", "maria@exemplo.org", "Prefere PIX");
		when(repositorio.findById(3L)).thenReturn(Optional.of(maria));

		DoadorResponse anonimo = service.anonimizar(3L);

		assertThat(anonimo.nome()).isEqualTo("Anônimo");
		assertThat(anonimo.telefone()).isNull();
		assertThat(anonimo.email()).isNull();
		assertThat(anonimo.observacao()).isNull();
	}

	@Test
	void buscarInexistenteLanca404() {
		when(repositorio.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.buscar(99L))
				.isInstanceOf(NaoEncontradoException.class)
				.hasMessage("Doador não encontrado.");
	}
}

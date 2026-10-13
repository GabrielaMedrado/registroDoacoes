package br.org.iap.doacoes.comum;

import static org.assertj.core.api.Assertions.assertThat;

import br.org.iap.doacoes.doacao.Doacao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class GlobalExceptionHandlerTest {

	@Test
	void conflitoDeVersaoViraConflito() {
		ResponseEntity<ProblemDetail> resposta = new GlobalExceptionHandler()
				.conflitoDeVersao(new ObjectOptimisticLockingFailureException(Doacao.class, 1L));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(resposta.getBody().getDetail())
				.isEqualTo("Este registro foi alterado por outra pessoa. Recarregue a página e tente de novo.");
	}
}

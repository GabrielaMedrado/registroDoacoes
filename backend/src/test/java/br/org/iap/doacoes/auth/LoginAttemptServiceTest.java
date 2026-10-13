package br.org.iap.doacoes.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

	private final RelogioAjustavel relogio = new RelogioAjustavel();
	private final LoginAttemptService service = new LoginAttemptService(relogio);

	private void falhar(String email, int vezes) {
		for (int i = 0; i < vezes; i++) {
			service.registrarFalha(email);
		}
	}

	@Test
	void quatroFalhasNaoBloqueiam() {
		falhar("a@x.com", 4);

		assertThat(service.estaBloqueado("a@x.com")).isFalse();
	}

	@Test
	void quintaFalhaBloqueia() {
		falhar("a@x.com", 5);

		assertThat(service.estaBloqueado("a@x.com")).isTrue();
	}

	@Test
	void bloqueioExpiraApos15Minutos() {
		falhar("a@x.com", 5);

		relogio.avancar(Duration.ofMinutes(14).plusSeconds(59));
		assertThat(service.estaBloqueado("a@x.com")).isTrue();

		relogio.avancar(Duration.ofSeconds(1));
		assertThat(service.estaBloqueado("a@x.com")).isFalse();
	}

	@Test
	void aposExpirarContadorRecomeca() {
		falhar("a@x.com", 5);
		relogio.avancar(Duration.ofMinutes(15));

		falhar("a@x.com", 1);

		assertThat(service.estaBloqueado("a@x.com")).isFalse();
	}

	@Test
	void falhaDuranteBloqueioMantemBloqueio() {
		falhar("a@x.com", 5);

		falhar("a@x.com", 1);

		assertThat(service.estaBloqueado("a@x.com")).isTrue();
	}

	@Test
	void sucessoZeraContador() {
		falhar("a@x.com", 4);
		service.registrarSucesso("a@x.com");

		falhar("a@x.com", 4);

		assertThat(service.estaBloqueado("a@x.com")).isFalse();
	}

	@Test
	void emailComCaixaEEspacosDiferentesEOMesmo() {
		falhar("A@x.com", 3);
		falhar(" a@X.COM ", 2);

		assertThat(service.estaBloqueado("a@x.com")).isTrue();
	}

	@Test
	void emailsDiferentesSaoIndependentes() {
		falhar("a@x.com", 5);

		assertThat(service.estaBloqueado("b@x.com")).isFalse();
	}

	private static final class RelogioAjustavel extends Clock {

		private Instant agora = Instant.parse("2026-10-09T12:00:00Z");

		void avancar(Duration duracao) {
			agora = agora.plus(duracao);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}
	}
}

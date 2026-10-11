package br.org.iap.doacoes.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

	@Test
	void relogioUsaOFusoDeBrasiliaMesmoComServidorEmUtc() {
		// Em Docker a JVM costuma rodar em UTC; das 21h à meia-noite isso já seria "amanhã".
		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
			assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
		} finally {
			TimeZone.setDefault(original);
		}
	}
}

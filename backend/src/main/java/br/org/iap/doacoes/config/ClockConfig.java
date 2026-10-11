package br.org.iap.doacoes.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	/** "Hoje" é sempre o de Joinville: em Docker a JVM roda em UTC, que já vira o dia às 21h. */
	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("America/Sao_Paulo"));
	}
}

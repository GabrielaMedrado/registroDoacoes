package br.org.iap.doacoes;

import org.springframework.boot.SpringApplication;

public class TestDoacoesApplication {

	public static void main(String[] args) {
		SpringApplication.from(DoacoesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

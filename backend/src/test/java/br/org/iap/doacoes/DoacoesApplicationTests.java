package br.org.iap.doacoes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DoacoesApplicationTests {

	@Test
	void contextLoads() {
	}

}

package br.org.iap.doacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MigracaoFlywayTest {

	@Autowired
	private JdbcTemplate jdbc;

	private Long usuarioId;
	private Long campanhaId;

	@BeforeEach
	void criarDadosMinimos() {
		usuarioId = jdbc.queryForObject(
				"insert into usuario (nome, email, senha_hash, papel, ativo) "
						+ "values ('Teste', 'teste@igreja.org', 'x', 'ADMIN', true) returning id",
				Long.class);
		campanhaId = jdbc.queryForObject(
				"insert into campanha (titulo, data_inicio, status, criado_por) "
						+ "values ('Campanha', current_date, 'ATIVA', ?) returning id",
				Long.class, usuarioId);
	}

	@Test
	void criaTodasAsTabelasDoModelo() {
		List<String> tabelas = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'",
				String.class);
		assertThat(tabelas).contains("usuario", "campanha", "necessidade", "doador", "doacao", "item_doacao");
	}

	@Test
	void rejeitaDoacaoEmDinheiroSemValor() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into doacao (campanha_id, tipo, data_doacao, registrado_por) "
						+ "values (?, 'DINHEIRO', current_date, ?)",
				campanhaId, usuarioId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejeitaQuantidadeZeroEmItemDoacao() {
		Long necessidadeId = jdbc.queryForObject(
				"insert into necessidade (campanha_id, item, unidade, quantidade_meta) "
						+ "values (?, 'Arroz', 'KG', 10) returning id",
				Long.class, campanhaId);
		Long doacaoId = jdbc.queryForObject(
				"insert into doacao (campanha_id, tipo, data_doacao, registrado_por) "
						+ "values (?, 'ITEM', current_date, ?) returning id",
				Long.class, campanhaId, usuarioId);
		assertThatThrownBy(() -> jdbc.update(
				"insert into item_doacao (doacao_id, necessidade_id, quantidade) values (?, ?, 0)",
				doacaoId, necessidadeId)).isInstanceOf(DataIntegrityViolationException.class);
	}
}

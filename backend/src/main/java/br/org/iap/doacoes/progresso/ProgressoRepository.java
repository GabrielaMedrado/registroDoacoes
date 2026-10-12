package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas agregadas (R6): os totais são sempre calculados, nunca gravados. */
@Repository
public class ProgressoRepository {

	private final JdbcClient jdbc;

	public ProgressoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<ArrecadadoNecessidade> arrecadadoPorNecessidade(Collection<Long> campanhaIds) {
		if (campanhaIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				select n.campanha_id, n.id, n.item, n.unidade, n.quantidade_meta, n.observacao,
				       coalesce(sum(i.quantidade), 0) as arrecadado
				from necessidade n
				left join item_doacao i on i.necessidade_id = n.id
				where n.campanha_id in (:ids)
				group by n.id
				order by n.campanha_id, n.item, n.id
				""")
				.param("ids", campanhaIds)
				.query((rs, linha) -> new ArrecadadoNecessidade(rs.getLong("campanha_id"), rs.getLong("id"),
						rs.getString("item"), Unidade.valueOf(rs.getString("unidade")),
						rs.getBigDecimal("quantidade_meta"), rs.getString("observacao"),
						rs.getBigDecimal("arrecadado")))
				.list();
	}

	/** Total em R$ das doações em dinheiro; campanha sem doação em dinheiro fica fora do mapa. */
	public Map<Long, BigDecimal> totalDinheiroPorCampanha(Collection<Long> campanhaIds) {
		Map<Long, BigDecimal> totais = new HashMap<>();
		if (campanhaIds.isEmpty()) {
			return totais;
		}
		jdbc.sql("""
				select campanha_id, sum(valor) as total
				from doacao
				where tipo = 'DINHEIRO' and campanha_id in (:ids)
				group by campanha_id
				""")
				.param("ids", campanhaIds)
				.query(rs -> {
					totais.put(rs.getLong("campanha_id"), rs.getBigDecimal("total"));
				});
		return totais;
	}
}

package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Campanha;
import br.org.iap.doacoes.campanha.CampanhaService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Progresso das campanhas, reutilizado pelo detalhe, pelo painel e pelos relatórios. */
@Service
public class ProgressoService {

	private final ProgressoRepository repositorio;
	private final CampanhaService campanhas;

	public ProgressoService(ProgressoRepository repositorio, CampanhaService campanhas) {
		this.repositorio = repositorio;
		this.campanhas = campanhas;
	}

	@Transactional(readOnly = true)
	public ProgressoCampanhaResponse daCampanha(Long campanhaId) {
		return dasCampanhas(List.of(campanhas.obter(campanhaId))).getFirst();
	}

	/** Progresso de várias campanhas com duas consultas no total, na ordem recebida. */
	@Transactional(readOnly = true)
	public List<ProgressoCampanhaResponse> dasCampanhas(List<Campanha> lista) {
		List<Long> ids = lista.stream().map(Campanha::getId).toList();
		Map<Long, List<NecessidadeProgresso>> porCampanha = repositorio.arrecadadoPorNecessidade(ids).stream()
				.collect(Collectors.groupingBy(ArrecadadoNecessidade::campanhaId,
						Collectors.mapping(NecessidadeProgresso::de, Collectors.toList())));
		Map<Long, BigDecimal> totais = repositorio.totalDinheiroPorCampanha(ids);

		return lista.stream().map(c -> {
			List<NecessidadeProgresso> necessidades = porCampanha.getOrDefault(c.getId(), List.of());
			BigDecimal total = totais.getOrDefault(c.getId(), BigDecimal.ZERO);
			return new ProgressoCampanhaResponse(c.getId(), c.getTitulo(), c.getStatus(),
					CalculoProgresso.percentualGeral(necessidades), total, c.getMetaValor(),
					CalculoProgresso.percentualValor(c.getMetaValor(), total), necessidades);
		}).toList();
	}
}

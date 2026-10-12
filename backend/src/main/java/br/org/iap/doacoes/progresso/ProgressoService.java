package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Campanha;
import br.org.iap.doacoes.campanha.CampanhaRepository;
import br.org.iap.doacoes.campanha.CampanhaService;
import br.org.iap.doacoes.campanha.StatusCampanha;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Progresso das campanhas, reutilizado pelo detalhe, pelo painel e pelos relatórios. */
@Service
public class ProgressoService {

	static final int LIMITE_PENDENCIAS = 10;

	/**
	 * Mais urgentes primeiro: menor percentual (quantidades em unidades diferentes não se comparam),
	 * depois maior pendente, campanha e item.
	 */
	private static final Comparator<PendenciaResponse> URGENCIA = Comparator
			.comparingInt(PendenciaResponse::percentual)
			.thenComparing(PendenciaResponse::pendente, Comparator.reverseOrder())
			.thenComparing(PendenciaResponse::campanhaTitulo)
			.thenComparing(PendenciaResponse::item);

	private final ProgressoRepository repositorio;
	private final CampanhaService campanhas;
	private final CampanhaRepository campanhaRepository;

	public ProgressoService(ProgressoRepository repositorio, CampanhaService campanhas,
			CampanhaRepository campanhaRepository) {
		this.repositorio = repositorio;
		this.campanhas = campanhas;
		this.campanhaRepository = campanhaRepository;
	}

	/** Campanhas ativas (por título) e as necessidades com maior pendência entre elas. */
	@Transactional(readOnly = true)
	public PainelResponse painel() {
		List<ProgressoCampanhaResponse> ativas = dasCampanhas(
				campanhaRepository.findByStatusOrderByTituloAsc(StatusCampanha.ATIVA));
		List<PendenciaResponse> pendencias = ativas.stream()
				.flatMap(c -> c.necessidades().stream()
						.filter(n -> n.pendente().signum() > 0)
						.map(n -> new PendenciaResponse(c.campanhaId(), c.titulo(), n.id(), n.item(), n.unidade(),
								n.pendente(), n.percentual())))
				.sorted(URGENCIA)
				.limit(LIMITE_PENDENCIAS)
				.toList();
		return new PainelResponse(ativas, pendencias);
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

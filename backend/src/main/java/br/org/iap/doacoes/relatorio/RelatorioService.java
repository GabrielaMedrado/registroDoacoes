package br.org.iap.doacoes.relatorio;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.CsvWriter;
import br.org.iap.doacoes.comum.Formatos;
import br.org.iap.doacoes.doacao.DoacaoFiltros;
import br.org.iap.doacoes.doacao.DoacaoMontador;
import br.org.iap.doacoes.doacao.DoacaoRepository;
import br.org.iap.doacoes.doacao.DoacaoResponse;
import br.org.iap.doacoes.doacao.ItemDoacaoResponse;
import br.org.iap.doacoes.doacao.TipoDoacao;
import br.org.iap.doacoes.progresso.NecessidadeProgresso;
import br.org.iap.doacoes.progresso.ProgressoCampanhaResponse;
import br.org.iap.doacoes.progresso.ProgressoService;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatórios para prestação de contas: em JSON para a tela e em CSV para planilha. */
@Service
public class RelatorioService {

	private static final Collator ALFABETICA = Collator.getInstance(Locale.of("pt", "BR"));

	private final ProgressoService progresso;
	private final DoacaoRepository doacoes;
	private final DoacaoMontador montador;

	public RelatorioService(ProgressoService progresso, DoacaoRepository doacoes, DoacaoMontador montador) {
		this.progresso = progresso;
		this.doacoes = doacoes;
		this.montador = montador;
	}

	public ProgressoCampanhaResponse campanha(Long id) {
		return progresso.daCampanha(id);
	}

	public byte[] campanhaCsv(Long id) {
		ProgressoCampanhaResponse p = progresso.daCampanha(id);
		CsvWriter csv = new CsvWriter()
				.linha("Campanha", p.titulo())
				.linha("Item", "Unidade", "Meta", "Arrecadado", "Pendente", "Excedente", "Percentual");
		for (NecessidadeProgresso n : p.necessidades()) {
			csv.linha(n.item(), n.unidade().rotulo(), Formatos.numero(n.quantidadeMeta()),
					Formatos.numero(n.arrecadado()), Formatos.numero(n.pendente()), Formatos.numero(n.excedente()),
					String.valueOf(n.percentual()));
		}
		return csv.linha("Total em dinheiro", Formatos.numero(p.totalDinheiro()))
				.linha("Meta em dinheiro", p.metaValor() == null ? null : Formatos.numero(p.metaValor()))
				.paraBytes();
	}

	@Transactional(readOnly = true)
	public RelatorioDoacoesResponse doacoes(LocalDate de, LocalDate ate, Long campanhaId, TipoDoacao tipo,
			UsuarioAutenticado usuario) {
		validarPeriodo(de, ate);
		Sort cronologica = Sort.by(Sort.Order.asc("dataDoacao"), Sort.Order.asc("id"));
		List<DoacaoResponse> lista = montador.montar(
				doacoes.findAll(DoacaoFiltros.de(campanhaId, de, ate, tipo, null), cronologica), usuario);

		BigDecimal totalDinheiro = lista.stream()
				.filter(d -> d.tipo() == TipoDoacao.DINHEIRO)
				.map(DoacaoResponse::valor)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		List<LinhaRelatorioDoacao> linhas = lista.stream()
				.map(d -> new LinhaRelatorioDoacao(d.id(), d.dataDoacao(), d.campanhaTitulo(),
						d.doadorNome() == null ? "Anônimo" : d.doadorNome(), d.tipo(), descricao(d), d.valor()))
				.toList();
		return new RelatorioDoacoesResponse(de, ate, lista.size(), totalDinheiro, totaisPorItem(lista), linhas);
	}

	@Transactional(readOnly = true)
	public byte[] doacoesCsv(LocalDate de, LocalDate ate, Long campanhaId, TipoDoacao tipo,
			UsuarioAutenticado usuario) {
		RelatorioDoacoesResponse r = doacoes(de, ate, campanhaId, tipo, usuario);
		CsvWriter csv = new CsvWriter().linha("Data", "Campanha", "Doador", "Tipo", "Descrição", "Valor");
		for (LinhaRelatorioDoacao l : r.doacoes()) {
			csv.linha(Formatos.data(l.dataDoacao()), l.campanhaTitulo(), l.doadorNome(),
					l.tipo() == TipoDoacao.ITEM ? "Itens" : "Dinheiro", l.descricao(),
					l.valor() == null ? null : Formatos.numero(l.valor()));
		}
		return csv.linha("Total em dinheiro", null, null, null, null, Formatos.numero(r.totalDinheiro()))
				.paraBytes();
	}

	private static void validarPeriodo(LocalDate de, LocalDate ate) {
		if (de == null) {
			throw new CampoInvalidoException("de", "Informe a data inicial.");
		}
		if (ate == null) {
			throw new CampoInvalidoException("ate", "Informe a data final.");
		}
		if (ate.isBefore(de)) {
			throw new CampoInvalidoException("ate", "A data final não pode ser anterior à inicial.");
		}
	}

	/** "Arroz 5 kg, Feijão 2,5 kg" para itens; a forma de pagamento para dinheiro. */
	private static String descricao(DoacaoResponse d) {
		if (d.tipo() == TipoDoacao.DINHEIRO) {
			return d.formaPagamento() == null ? "" : d.formaPagamento().rotulo();
		}
		return d.itens().stream()
				.map(i -> i.item() + " " + Formatos.quantidade(i.quantidade()) + " " + i.unidade().rotulo())
				.collect(Collectors.joining(", "));
	}

	/** Soma por item (sem diferenciar maiúsculas) e unidade, em ordem alfabética. */
	private static List<TotalItem> totaisPorItem(List<DoacaoResponse> lista) {
		Map<String, TotalItem> totais = new LinkedHashMap<>();
		for (DoacaoResponse d : lista) {
			for (ItemDoacaoResponse i : d.itens()) {
				String chave = i.item().toLowerCase(Locale.ROOT) + "|" + i.unidade();
				totais.merge(chave, new TotalItem(i.item(), i.unidade(), i.quantidade()),
						(a, b) -> new TotalItem(a.item(), a.unidade(), a.quantidade().add(b.quantidade())));
			}
		}
		return totais.values().stream()
				.sorted(Comparator.comparing(TotalItem::item, ALFABETICA))
				.toList();
	}
}

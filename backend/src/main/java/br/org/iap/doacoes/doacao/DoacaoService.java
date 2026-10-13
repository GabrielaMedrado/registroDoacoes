package br.org.iap.doacoes.doacao;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.campanha.Campanha;
import br.org.iap.doacoes.campanha.CampanhaService;
import br.org.iap.doacoes.campanha.Necessidade;
import br.org.iap.doacoes.campanha.NecessidadeRepository;
import br.org.iap.doacoes.campanha.StatusCampanha;
import br.org.iap.doacoes.comum.AcessoNegadoException;
import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.PaginaResponse;
import br.org.iap.doacoes.comum.RegraNegocioException;
import br.org.iap.doacoes.doador.DoadorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro e manutenção de doações, com as regras R1–R4 e R8 da especificação. */
@Service
public class DoacaoService {

	static final String MSG_CAMPANHA_NAO_ATIVA = "Só é possível registrar ou alterar doações em campanhas ativas.";
	static final String MSG_DOACAO_ALTERADA =
			"Esta doação foi alterada por outra pessoa. Recarregue a página e tente de novo.";
	private static final String CAMPO_ITENS = "itens";

	private final DoacaoRepository doacoes;
	private final CampanhaService campanhas;
	private final NecessidadeRepository necessidades;
	private final DoadorRepository doadores;
	private final DoacaoMontador montador;
	private final Clock clock;

	public DoacaoService(DoacaoRepository doacoes, CampanhaService campanhas, NecessidadeRepository necessidades,
			DoadorRepository doadores, DoacaoMontador montador, Clock clock) {
		this.doacoes = doacoes;
		this.campanhas = campanhas;
		this.necessidades = necessidades;
		this.doadores = doadores;
		this.montador = montador;
		this.clock = clock;
	}

	@Transactional
	public DoacaoResponse registrar(DoacaoRequest req, UsuarioAutenticado usuario) {
		List<ItemDoacao> itens = validar(req);
		Doacao doacao = new Doacao(req.campanhaId(), req.doadorId(), req.tipo(), valorDe(req), formaDe(req),
				req.dataDoacao(), req.observacao(), usuario.getId());
		doacao.substituirItens(itens);
		return montador.montar(doacoes.save(doacao), usuario);
	}

	@Transactional(readOnly = true)
	public DoacaoResponse buscar(Long id, UsuarioAutenticado usuario) {
		return montador.montar(obter(id), usuario);
	}

	@Transactional(readOnly = true)
	public PaginaResponse<DoacaoResponse> listar(Long campanhaId, LocalDate de, LocalDate ate, TipoDoacao tipo,
			Long doadorId, int pagina, int tamanho, UsuarioAutenticado usuario) {
		Sort maisRecentes = Sort.by(Sort.Order.desc("dataDoacao"), Sort.Order.desc("id"));
		Page<Doacao> page = doacoes.findAll(DoacaoFiltros.de(campanhaId, de, ate, tipo, doadorId),
				PaginaResponse.pageable(pagina, tamanho, maisRecentes));
		List<DoacaoResponse> itens = montador.montar(page.getContent(), usuario);
		return new PaginaResponse<>(itens, page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Transactional
	public DoacaoResponse atualizar(Long id, DoacaoRequest req, UsuarioAutenticado usuario) {
		Doacao doacao = obterParaAlterar(id, usuario);
		if (req.versao() == null) {
			throw new CampoInvalidoException("versao", "Recarregue a página e tente de novo.");
		}
		if (!req.versao().equals(doacao.getVersao())) {
			throw new RegraNegocioException(MSG_DOACAO_ALTERADA);
		}
		List<ItemDoacao> itens = validar(req);
		doacao.atualizar(req.campanhaId(), req.doadorId(), req.tipo(), valorDe(req), formaDe(req), req.dataDoacao(),
				req.observacao());
		doacao.substituirItens(itens);
		// Flush aqui: a corrida entre a conferência acima e a gravação vira 409 nesta requisição (@Version),
		// e a resposta já leva a versão nova.
		return montador.montar(doacoes.saveAndFlush(doacao), usuario);
	}

	@Transactional
	public void excluir(Long id, UsuarioAutenticado usuario) {
		doacoes.delete(obterParaAlterar(id, usuario));
	}

	/** R8 e campanha ativa: verificados antes de qualquer alteração ou exclusão. */
	private Doacao obterParaAlterar(Long id, UsuarioAutenticado usuario) {
		Doacao doacao = obter(id);
		if (!DoacaoMontador.podeAlterar(doacao, usuario)) {
			throw new AcessoNegadoException("Você só pode alterar doações que você registrou.");
		}
		exigirAtiva(campanhas.obter(doacao.getCampanhaId()));
		return doacao;
	}

	Doacao obter(Long id) {
		return doacoes.findById(id).orElseThrow(() -> new NaoEncontradoException("Doação não encontrada."));
	}

	/** Aplica R1–R4 na ordem da especificação e devolve os itens a gravar (vazio para dinheiro). */
	List<ItemDoacao> validar(DoacaoRequest req) {
		Campanha campanha = campanhas.obter(req.campanhaId());
		exigirAtiva(campanha);
		if (req.dataDoacao().isAfter(LocalDate.now(clock))) {
			throw new CampoInvalidoException("dataDoacao", "A data da doação não pode ser futura.");
		}
		if (req.doadorId() != null && !doadores.existsById(req.doadorId())) {
			throw new CampoInvalidoException("doadorId", "Doador não encontrado.");
		}
		return req.tipo() == TipoDoacao.ITEM ? validarItens(req) : validarDinheiro(req);
	}

	static void exigirAtiva(Campanha campanha) {
		if (campanha.getStatus() != StatusCampanha.ATIVA) {
			throw new RegraNegocioException(MSG_CAMPANHA_NAO_ATIVA);
		}
	}

	private List<ItemDoacao> validarItens(DoacaoRequest req) {
		if (req.itens().isEmpty()) {
			throw new CampoInvalidoException(CAMPO_ITENS, "Adicione ao menos um item.");
		}
		Set<Long> ids = new HashSet<>();
		for (ItemDoacaoRequest item : req.itens()) {
			if (!ids.add(item.necessidadeId())) {
				throw new CampoInvalidoException(CAMPO_ITENS, "O mesmo item foi informado mais de uma vez.");
			}
		}
		Map<Long, Necessidade> encontradas = necessidades.findAllById(ids).stream()
				.collect(Collectors.toMap(Necessidade::getId, Function.identity()));
		for (Long id : ids) {
			Necessidade n = encontradas.get(id);
			if (n == null || !n.getCampanhaId().equals(req.campanhaId())) {
				throw new CampoInvalidoException(CAMPO_ITENS, "Este item não pertence à campanha escolhida.");
			}
		}
		return req.itens().stream().map(i -> new ItemDoacao(i.necessidadeId(), i.quantidade())).toList();
	}

	private static List<ItemDoacao> validarDinheiro(DoacaoRequest req) {
		if (req.valor() == null) {
			throw new CampoInvalidoException("valor", "Informe o valor.");
		}
		if (req.formaPagamento() == null) {
			throw new CampoInvalidoException("formaPagamento", "Escolha a forma de pagamento.");
		}
		return List.of();
	}

	/** Doação de itens nunca grava valor nem forma de pagamento (restrição do banco). */
	static BigDecimal valorDe(DoacaoRequest req) {
		return req.tipo() == TipoDoacao.DINHEIRO ? req.valor() : null;
	}

	static FormaPagamento formaDe(DoacaoRequest req) {
		return req.tipo() == TipoDoacao.DINHEIRO ? req.formaPagamento() : null;
	}
}

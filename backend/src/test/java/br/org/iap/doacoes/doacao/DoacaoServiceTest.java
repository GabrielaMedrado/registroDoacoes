package br.org.iap.doacoes.doacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.campanha.Campanha;
import br.org.iap.doacoes.campanha.CampanhaRepository;
import br.org.iap.doacoes.campanha.CampanhaService;
import br.org.iap.doacoes.campanha.Necessidade;
import br.org.iap.doacoes.campanha.NecessidadeRepository;
import br.org.iap.doacoes.campanha.StatusCampanha;
import br.org.iap.doacoes.campanha.Unidade;
import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.RegraNegocioException;
import br.org.iap.doacoes.doador.DoadorRepository;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DoacaoServiceTest {

	static final String MSG_NAO_ATIVA = "Só é possível registrar ou alterar doações em campanhas ativas.";
	static final LocalDate HOJE = LocalDate.parse("2026-10-15");

	private final DoacaoRepository doacoes = mock(DoacaoRepository.class);
	private final CampanhaService campanhas = mock(CampanhaService.class);
	private final NecessidadeRepository necessidades = mock(NecessidadeRepository.class);
	private final DoadorRepository doadores = mock(DoadorRepository.class);
	private final CampanhaRepository campanhaRepo = mock(CampanhaRepository.class);
	private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

	private final DoacaoMontador montador = new DoacaoMontador(campanhaRepo, doadores, usuarioRepo, necessidades);
	private final DoacaoService service =
			new DoacaoService(doacoes, campanhas, necessidades, doadores, montador, clock);

	private final Campanha campanha1 = campanha(1L, StatusCampanha.ATIVA);
	private final UsuarioAutenticado vitor = usuario(7L, "Vitor", Papel.VOLUNTARIO);

	private static <T> T comId(T entidade, Long id) {
		ReflectionTestUtils.setField(entidade, "id", id);
		return entidade;
	}

	private static Campanha campanha(Long id, StatusCampanha status) {
		Campanha c = comId(new Campanha("Cesta", null, LocalDate.parse("2026-10-01"), null, null, 1L), id);
		c.setStatus(status);
		return c;
	}

	private static UsuarioAutenticado usuario(Long id, String nome, Papel papel) {
		return new UsuarioAutenticado(comId(new Usuario(nome, nome + "@igreja.org", "hash", papel), id));
	}

	@BeforeEach
	void preparar() {
		when(campanhas.obter(1L)).thenReturn(campanha1);
		List<Necessidade> todas = List.of(
				comId(new Necessidade(1L, "Arroz", Unidade.KG, new BigDecimal("50"), null), 10L),
				comId(new Necessidade(1L, "Feijão", Unidade.KG, new BigDecimal("30"), null), 11L),
				comId(new Necessidade(2L, "Óleo", Unidade.LITRO, new BigDecimal("10"), null), 20L));
		when(necessidades.findAllById(anyIterable())).thenAnswer(inv -> {
			Collection<Long> ids = StreamSupport.stream(((Iterable<Long>) inv.getArgument(0)).spliterator(), false)
					.toList();
			return todas.stream().filter(n -> ids.contains(n.getId())).toList();
		});
		when(doacoes.save(any(Doacao.class))).thenAnswer(inv -> comId(inv.getArgument(0), 100L));
	}

	private static DoacaoRequest itens(Long campanhaId, LocalDate data, ItemDoacaoRequest... itens) {
		return new DoacaoRequest(campanhaId, null, TipoDoacao.ITEM, null, null, data, null, List.of(itens));
	}

	private static ItemDoacaoRequest item(Long necessidadeId, String quantidade) {
		return new ItemDoacaoRequest(necessidadeId, new BigDecimal(quantidade));
	}

	private Doacao salva() {
		ArgumentCaptor<Doacao> captor = ArgumentCaptor.forClass(Doacao.class);
		org.mockito.Mockito.verify(doacoes).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void registraDoacaoDeItensDoAutor() {
		DoacaoResponse r = service.registrar(itens(1L, HOJE, item(10L, "5"), item(11L, "2.5")), vitor);

		Doacao d = salva();
		assertThat(d.getRegistradoPorId()).isEqualTo(7L);
		assertThat(d.getItens()).hasSize(2);
		assertThat(d.getValor()).isNull();
		assertThat(d.getFormaPagamento()).isNull();
		assertThat(r.itens()).extracting(ItemDoacaoResponse::item).containsExactly("Arroz", "Feijão");
	}

	@Test
	void registraDoacaoEmDinheiroSemItens() {
		service.registrar(new DoacaoRequest(1L, null, TipoDoacao.DINHEIRO, new BigDecimal("50"),
				FormaPagamento.PIX, HOJE, null, List.of(item(10L, "5"))), vitor);

		Doacao d = salva();
		assertThat(d.getValor()).isEqualByComparingTo("50");
		assertThat(d.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
		assertThat(d.getItens()).isEmpty();
	}

	@Test
	void campanhaNaoAtivaLanca409() {
		for (StatusCampanha status : List.of(StatusCampanha.RASCUNHO, StatusCampanha.ENCERRADA)) {
			campanha1.setStatus(status);
			assertThatThrownBy(() -> service.registrar(itens(1L, HOJE, item(10L, "5")), vitor))
					.isInstanceOf(RegraNegocioException.class)
					.hasMessage(MSG_NAO_ATIVA);
		}
	}

	@Test
	void dataFuturaFalhaNoCampoDataDoacao() {
		assertThatThrownBy(() -> service.registrar(itens(1L, HOJE.plusDays(1), item(10L, "5")), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("A data da doação não pode ser futura.")
				.extracting("campo").isEqualTo("dataDoacao");
	}

	@Test
	void dataDeHojeEhPermitida() {
		assertThatCode(() -> service.registrar(itens(1L, HOJE, item(10L, "5")), vitor)).doesNotThrowAnyException();
	}

	@Test
	void itensVaziosFalhamNoCampoItens() {
		assertThatThrownBy(() -> service.registrar(itens(1L, HOJE), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Adicione ao menos um item.")
				.extracting("campo").isEqualTo("itens");
	}

	@Test
	void itemDeOutraCampanhaFalha() {
		assertThatThrownBy(() -> service.registrar(itens(1L, HOJE, item(20L, "1")), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Este item não pertence à campanha escolhida.");
	}

	@Test
	void itemRepetidoFalha() {
		assertThatThrownBy(() -> service.registrar(itens(1L, HOJE, item(10L, "1"), item(10L, "2")), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("O mesmo item foi informado mais de uma vez.");
	}

	@Test
	void dinheiroSemValorFalhaNoCampoValor() {
		assertThatThrownBy(() -> service.registrar(new DoacaoRequest(1L, null, TipoDoacao.DINHEIRO, null,
				FormaPagamento.PIX, HOJE, null, null), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Informe o valor.")
				.extracting("campo").isEqualTo("valor");
	}

	@Test
	void dinheiroSemFormaFalhaNoCampoFormaPagamento() {
		assertThatThrownBy(() -> service.registrar(new DoacaoRequest(1L, null, TipoDoacao.DINHEIRO,
				new BigDecimal("10"), null, HOJE, null, null), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Escolha a forma de pagamento.")
				.extracting("campo").isEqualTo("formaPagamento");
	}

	@Test
	void doadorInexistenteFalhaNoCampoDoadorId() {
		when(doadores.existsById(55L)).thenReturn(false);

		assertThatThrownBy(() -> service.registrar(new DoacaoRequest(1L, 55L, TipoDoacao.ITEM, null, null, HOJE,
				null, List.of(item(10L, "1"))), vitor))
				.isInstanceOf(CampoInvalidoException.class)
				.hasMessage("Doador não encontrado.")
				.extracting("campo").isEqualTo("doadorId");
	}

	@Test
	void podeAlterarVerdadeiroParaAutorEAdmin() {
		Doacao doacao = comId(new Doacao(1L, null, TipoDoacao.DINHEIRO, BigDecimal.TEN, FormaPagamento.PIX, HOJE,
				null, 7L), 100L);
		when(doacoes.findById(100L)).thenReturn(Optional.of(doacao));

		assertThat(service.buscar(100L, vitor).podeAlterar()).isTrue();
		assertThat(service.buscar(100L, usuario(8L, "Bia", Papel.VOLUNTARIO)).podeAlterar()).isFalse();
		assertThat(service.buscar(100L, usuario(1L, "Ana", Papel.ADMIN)).podeAlterar()).isTrue();
	}
}

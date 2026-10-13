package br.org.iap.doacoes.doacao;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.campanha.Campanha;
import br.org.iap.doacoes.campanha.CampanhaRepository;
import br.org.iap.doacoes.campanha.Necessidade;
import br.org.iap.doacoes.campanha.NecessidadeRepository;
import br.org.iap.doacoes.campanha.StatusCampanha;
import br.org.iap.doacoes.doador.Doador;
import br.org.iap.doacoes.doador.DoadorRepository;
import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Monta as respostas de doação buscando nomes e títulos em lote (uma consulta por tabela, não por doação). */
@Component
public class DoacaoMontador {

	private final CampanhaRepository campanhas;
	private final DoadorRepository doadores;
	private final UsuarioRepository usuarios;
	private final NecessidadeRepository necessidades;

	public DoacaoMontador(CampanhaRepository campanhas, DoadorRepository doadores, UsuarioRepository usuarios,
			NecessidadeRepository necessidades) {
		this.campanhas = campanhas;
		this.doadores = doadores;
		this.usuarios = usuarios;
		this.necessidades = necessidades;
	}

	/** R8: ADMIN altera qualquer doação; os demais, só as que registraram. */
	public static boolean podeAlterar(Doacao doacao, UsuarioAutenticado usuario) {
		return usuario.getPapel() == Papel.ADMIN || doacao.getRegistradoPorId().equals(usuario.getId());
	}

	public DoacaoResponse montar(Doacao doacao, UsuarioAutenticado usuario) {
		return montar(List.of(doacao), usuario).getFirst();
	}

	public List<DoacaoResponse> montar(List<Doacao> lista, UsuarioAutenticado usuario) {
		Map<Long, Campanha> campanhasPorId = porId(campanhas.findAllById(ids(lista, Doacao::getCampanhaId)),
				Campanha::getId, Function.identity());
		Map<Long, String> nomesDoadores = porId(doadores.findAllById(ids(lista, Doacao::getDoadorId)),
				Doador::getId, Doador::getNome);
		Map<Long, String> nomesUsuarios = porId(usuarios.findAllById(ids(lista, Doacao::getRegistradoPorId)),
				Usuario::getId, Usuario::getNome);
		List<Long> idsNecessidades = lista.stream()
				.flatMap(d -> d.getItens().stream().map(ItemDoacao::getNecessidadeId))
				.distinct().toList();
		Map<Long, Necessidade> itens = necessidades.findAllById(idsNecessidades).stream()
				.collect(Collectors.toMap(Necessidade::getId, Function.identity()));

		return lista.stream().map(d -> {
			Campanha campanha = campanhasPorId.get(d.getCampanhaId());
			boolean campanhaAtiva = campanha != null && campanha.getStatus() == StatusCampanha.ATIVA;
			return new DoacaoResponse(d.getId(), d.getCampanhaId(), campanha == null ? null : campanha.getTitulo(),
					d.getDoadorId(), nomesDoadores.get(d.getDoadorId()), d.getTipo(), d.getValor(),
					d.getFormaPagamento(), d.getDataDoacao(), d.getObservacao(), d.getRegistradoPorId(),
					nomesUsuarios.get(d.getRegistradoPorId()), d.getCriadoEm(),
					d.getItens().stream().map(i -> {
						Necessidade n = itens.get(i.getNecessidadeId());
						return new ItemDoacaoResponse(i.getNecessidadeId(), n == null ? null : n.getItem(),
								n == null ? null : n.getUnidade(), i.getQuantidade());
					}).toList(),
					// Campanha não ativa: o service recusa a alteração (409), então a tela nem oferece.
					campanhaAtiva, campanhaAtiva && podeAlterar(d, usuario));
		}).toList();
	}

	private static List<Long> ids(List<Doacao> lista, Function<Doacao, Long> campo) {
		return lista.stream().map(campo).filter(Objects::nonNull).distinct().toList();
	}

	private static <T, V> Map<Long, V> porId(Collection<T> entidades, Function<T, Long> id, Function<T, V> valor) {
		return entidades.stream().collect(Collectors.toMap(id, valor));
	}
}

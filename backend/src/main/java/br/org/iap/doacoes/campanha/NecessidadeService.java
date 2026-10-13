package br.org.iap.doacoes.campanha;

import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.RegraNegocioException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NecessidadeService {

	private final NecessidadeRepository repositorio;
	private final CampanhaService campanhas;

	public NecessidadeService(NecessidadeRepository repositorio, CampanhaService campanhas) {
		this.repositorio = repositorio;
		this.campanhas = campanhas;
	}

	@Transactional(readOnly = true)
	public List<NecessidadeResponse> listar(Long campanhaId) {
		campanhas.obter(campanhaId);
		return repositorio.findByCampanhaIdOrderByItemAsc(campanhaId).stream()
				.map(NecessidadeResponse::de)
				.toList();
	}

	@Transactional
	public NecessidadeResponse criar(Long campanhaId, NecessidadeRequest req) {
		campanhas.obter(campanhaId);
		Necessidade necessidade = new Necessidade(campanhaId, req.item().trim(), req.unidade(),
				req.quantidadeMeta(), vazioComoNulo(req.observacao()));
		return NecessidadeResponse.de(repositorio.save(necessidade));
	}

	@Transactional
	public NecessidadeResponse atualizar(Long id, NecessidadeRequest req) {
		Necessidade necessidade = obter(id);
		necessidade.atualizar(req.item().trim(), req.unidade(), req.quantidadeMeta(), vazioComoNulo(req.observacao()));
		return NecessidadeResponse.de(repositorio.save(necessidade));
	}

	@Transactional
	public void excluir(Long id) {
		Necessidade necessidade = obter(id);
		if (repositorio.possuiDoacoes(id)) {
			throw new RegraNegocioException("Esta necessidade já recebeu doações e não pode ser excluída.");
		}
		repositorio.delete(necessidade);
	}

	private Necessidade obter(Long id) {
		return repositorio.findById(id)
				.orElseThrow(() -> new NaoEncontradoException("Necessidade não encontrada."));
	}

	private static String vazioComoNulo(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}
}

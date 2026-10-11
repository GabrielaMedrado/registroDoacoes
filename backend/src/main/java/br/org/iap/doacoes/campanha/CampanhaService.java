package br.org.iap.doacoes.campanha;

import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.PaginaResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampanhaService {

	private final CampanhaRepository repositorio;

	public CampanhaService(CampanhaRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional(readOnly = true)
	public PaginaResponse<CampanhaResponse> listar(StatusCampanha status, int pagina, int tamanho) {
		Pageable pageable = PaginaResponse.pageable(pagina, tamanho,
				Sort.by(Sort.Order.desc("dataInicio"), Sort.Order.desc("id")));
		var page = status == null ? repositorio.findAll(pageable) : repositorio.findByStatus(status, pageable);
		return PaginaResponse.de(page.map(CampanhaResponse::de));
	}

	@Transactional(readOnly = true)
	public CampanhaResponse buscar(Long id) {
		return CampanhaResponse.de(obter(id));
	}

	@Transactional
	public CampanhaResponse criar(CampanhaRequest req, Long usuarioId) {
		validarPeriodo(req);
		Campanha campanha = new Campanha(req.titulo().trim(), vazioComoNulo(req.descricao()), req.dataInicio(),
				req.dataFim(), req.metaValor(), usuarioId);
		return CampanhaResponse.de(repositorio.save(campanha));
	}

	@Transactional
	public CampanhaResponse atualizar(Long id, CampanhaRequest req) {
		Campanha campanha = obter(id);
		validarPeriodo(req);
		campanha.atualizar(req.titulo().trim(), vazioComoNulo(req.descricao()), req.dataInicio(), req.dataFim(),
				req.metaValor());
		return CampanhaResponse.de(repositorio.save(campanha));
	}

	@Transactional
	public CampanhaResponse alterarStatus(Long id, StatusCampanha status) {
		Campanha campanha = obter(id);
		campanha.setStatus(status);
		return CampanhaResponse.de(repositorio.save(campanha));
	}

	/** Entidade ou 404; usado por outros serviços que dependem de uma campanha existente. */
	@Transactional(readOnly = true)
	public Campanha obter(Long id) {
		return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Campanha não encontrada."));
	}

	private static void validarPeriodo(CampanhaRequest req) {
		if (req.dataFim() != null && req.dataFim().isBefore(req.dataInicio())) {
			throw new CampoInvalidoException("dataFim", "A data de término deve ser igual ou posterior à de início.");
		}
	}

	private static String vazioComoNulo(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}
}

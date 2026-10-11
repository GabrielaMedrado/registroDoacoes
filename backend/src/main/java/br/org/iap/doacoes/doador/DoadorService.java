package br.org.iap.doacoes.doador;

import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.PaginaResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoadorService {

	private final DoadorRepository repositorio;

	public DoadorService(DoadorRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional(readOnly = true)
	public PaginaResponse<DoadorResponse> listar(String busca, int pagina, int tamanho) {
		String termo = busca == null ? "" : busca.trim();
		String digitos = termo.replaceAll("\\D", "");
		// A ordenação está na própria consulta nativa.
		return PaginaResponse.de(repositorio
				.buscar(termo, digitos, PaginaResponse.pageable(pagina, tamanho, Sort.unsorted()))
				.map(DoadorResponse::de));
	}

	@Transactional(readOnly = true)
	public DoadorResponse buscar(Long id) {
		return DoadorResponse.de(obter(id));
	}

	@Transactional
	public DoadorResponse criar(DoadorRequest req) {
		Doador doador = new Doador(req.nome().trim(), req.telefone(), req.email(), req.observacao());
		return DoadorResponse.de(repositorio.save(doador));
	}

	@Transactional
	public DoadorResponse atualizar(Long id, DoadorRequest req) {
		Doador doador = obter(id);
		doador.atualizar(req.nome().trim(), req.telefone(), req.email(), req.observacao());
		return DoadorResponse.de(repositorio.save(doador));
	}

	@Transactional
	public DoadorResponse anonimizar(Long id) {
		Doador doador = obter(id);
		doador.anonimizar();
		return DoadorResponse.de(repositorio.save(doador));
	}

	@Transactional(readOnly = true)
	public Doador obter(Long id) {
		return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Doador não encontrado."));
	}
}

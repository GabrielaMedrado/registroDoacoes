package br.org.iap.doacoes.doador;

import br.org.iap.doacoes.comum.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doadores")
public class DoadorController {

	private final DoadorService service;

	public DoadorController(DoadorService service) {
		this.service = service;
	}

	@GetMapping
	public PaginaResponse<DoadorResponse> listar(@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
		return service.listar(busca, pagina, tamanho);
	}

	@GetMapping("/{id}")
	public DoadorResponse buscar(@PathVariable Long id) {
		return service.buscar(id);
	}

	@PostMapping
	public ResponseEntity<DoadorResponse> criar(@Valid @RequestBody DoadorRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
	}

	@PutMapping("/{id}")
	public DoadorResponse atualizar(@PathVariable Long id, @Valid @RequestBody DoadorRequest req) {
		return service.atualizar(id, req);
	}

	@PostMapping("/{id}/anonimizar")
	@PreAuthorize("hasRole('ADMIN')")
	public DoadorResponse anonimizar(@PathVariable Long id) {
		return service.anonimizar(id);
	}
}

package br.org.iap.doacoes.campanha;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.comum.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campanhas")
public class CampanhaController {

	private final CampanhaService service;

	public CampanhaController(CampanhaService service) {
		this.service = service;
	}

	@GetMapping
	public PaginaResponse<CampanhaResponse> listar(@RequestParam(required = false) StatusCampanha status,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
		return service.listar(status, pagina, tamanho);
	}

	@GetMapping("/{id}")
	public CampanhaResponse buscar(@PathVariable Long id) {
		return service.buscar(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<CampanhaResponse> criar(@Valid @RequestBody CampanhaRequest req,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req, usuario.getId()));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public CampanhaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CampanhaRequest req) {
		return service.atualizar(id, req);
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public CampanhaResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody AlterarStatusRequest req) {
		return service.alterarStatus(id, req.status());
	}
}

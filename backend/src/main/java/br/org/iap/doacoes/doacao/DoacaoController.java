package br.org.iap.doacoes.doacao;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doacoes")
public class DoacaoController {

	private final DoacaoService service;

	public DoacaoController(DoacaoService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<DoacaoResponse> registrar(@Valid @RequestBody DoacaoRequest req,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(req, usuario));
	}

	@GetMapping("/{id}")
	public DoacaoResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return service.buscar(id, usuario);
	}
}

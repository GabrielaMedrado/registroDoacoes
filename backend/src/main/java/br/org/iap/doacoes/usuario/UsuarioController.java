package br.org.iap.doacoes.usuario;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.comum.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

	private final UsuarioService service;

	public UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@GetMapping
	public PaginaResponse<UsuarioResponse> listar(@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanho) {
		return service.listar(pagina, tamanho);
	}

	@PostMapping
	public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
	}

	@PutMapping("/{id}")
	public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest req,
			@AuthenticationPrincipal UsuarioAutenticado admin) {
		return service.atualizar(id, req, admin.getId());
	}

	@PostMapping("/{id}/redefinir-senha")
	public ResponseEntity<Void> redefinirSenha(@PathVariable Long id, @Valid @RequestBody RedefinirSenhaRequest req) {
		service.redefinirSenha(id, req.novaSenha());
		return ResponseEntity.noContent().build();
	}
}

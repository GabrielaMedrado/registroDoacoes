package br.org.iap.doacoes.doacao;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.comum.PaginaResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doacoes")
public class DoacaoController {

	private final DoacaoService service;

	public DoacaoController(DoacaoService service) {
		this.service = service;
	}

	@GetMapping
	public PaginaResponse<DoacaoResponse> listar(@RequestParam(required = false) Long campanhaId,
			@RequestParam(required = false) LocalDate de, @RequestParam(required = false) LocalDate ate,
			@RequestParam(required = false) TipoDoacao tipo, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanho, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return service.listar(campanhaId, de, ate, tipo, null, pagina, tamanho, usuario);
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

	@PutMapping("/{id}")
	public DoacaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody DoacaoRequest req,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return service.atualizar(id, req, usuario);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		service.excluir(id, usuario);
		return ResponseEntity.noContent().build();
	}
}

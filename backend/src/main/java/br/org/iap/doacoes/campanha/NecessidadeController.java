package br.org.iap.doacoes.campanha;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Necessidades")
@RequestMapping("/api")
public class NecessidadeController {

	private final NecessidadeService service;

	public NecessidadeController(NecessidadeService service) {
		this.service = service;
	}

	@GetMapping("/campanhas/{campanhaId}/necessidades")
	public List<NecessidadeResponse> listar(@PathVariable Long campanhaId) {
		return service.listar(campanhaId);
	}

	@PostMapping("/campanhas/{campanhaId}/necessidades")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<NecessidadeResponse> criar(@PathVariable Long campanhaId,
			@Valid @RequestBody NecessidadeRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(campanhaId, req));
	}

	@PutMapping("/necessidades/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public NecessidadeResponse atualizar(@PathVariable Long id, @Valid @RequestBody NecessidadeRequest req) {
		return service.atualizar(id, req);
	}

	@DeleteMapping("/necessidades/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		service.excluir(id);
		return ResponseEntity.noContent().build();
	}
}

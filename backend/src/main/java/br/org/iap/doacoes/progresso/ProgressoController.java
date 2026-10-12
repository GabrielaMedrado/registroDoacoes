package br.org.iap.doacoes.progresso;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProgressoController {

	private final ProgressoService service;

	public ProgressoController(ProgressoService service) {
		this.service = service;
	}

	@GetMapping("/campanhas/{id}/progresso")
	public ProgressoCampanhaResponse progresso(@PathVariable Long id) {
		return service.daCampanha(id);
	}
}

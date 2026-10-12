package br.org.iap.doacoes.relatorio;

import br.org.iap.doacoes.auth.UsuarioAutenticado;
import br.org.iap.doacoes.doacao.TipoDoacao;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final RelatorioService service;

	public RelatorioController(RelatorioService service) {
		this.service = service;
	}

	/** JSON para a tela; com {@code ?formato=csv}, arquivo para planilha. */
	@GetMapping("/campanha/{id}")
	public ResponseEntity<?> campanha(@PathVariable Long id, @RequestParam(required = false) String formato) {
		if ("csv".equalsIgnoreCase(formato)) {
			return arquivo(service.campanhaCsv(id), "campanha-" + id + ".csv");
		}
		return ResponseEntity.ok(service.campanha(id));
	}

	@GetMapping("/doacoes")
	public ResponseEntity<?> doacoes(@RequestParam(required = false) LocalDate de,
			@RequestParam(required = false) LocalDate ate, @RequestParam(required = false) Long campanhaId,
			@RequestParam(required = false) TipoDoacao tipo, @RequestParam(required = false) String formato,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if ("csv".equalsIgnoreCase(formato)) {
			return arquivo(service.doacoesCsv(de, ate, campanhaId, tipo, usuario),
					"doacoes-" + de + "-a-" + ate + ".csv");
		}
		return ResponseEntity.ok(service.doacoes(de, ate, campanhaId, tipo, usuario));
	}

	@GetMapping(value = "/pendencias", produces = "text/plain;charset=UTF-8")
	public String pendencias(@RequestParam(required = false) Long campanhaId) {
		return service.pendencias(campanhaId);
	}

	private static ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome) {
		ContentDisposition anexo = ContentDisposition.attachment().filename(nome).build();
		return ResponseEntity.ok()
				.contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, anexo.toString())
				.body(conteudo);
	}
}

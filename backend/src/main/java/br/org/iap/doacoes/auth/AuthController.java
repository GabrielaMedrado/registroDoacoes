package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.comum.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String MSG_INVALIDO = "E-mail ou senha inválidos.";
	static final String MSG_BLOQUEADO = "Muitas tentativas. Tente novamente em 15 minutos.";

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final LoginAttemptService tentativas;
	private final ContaService contas;

	public AuthController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			LoginAttemptService tentativas, ContaService contas) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.tentativas = tentativas;
		this.contas = contas;
	}

	/** Força a emissão do cookie XSRF-TOKEN para o frontend. */
	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
			HttpServletRequest request, HttpServletResponse response) {
		String email = req.email();
		if (tentativas.estaBloqueado(email)) {
			return GlobalExceptionHandler.problema(HttpStatus.TOO_MANY_REQUESTS, MSG_BLOQUEADO);
		}
		Authentication auth;
		try {
			auth = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, req.senha()));
		} catch (AuthenticationException e) {
			tentativas.registrarFalha(email);
			return GlobalExceptionHandler.problema(HttpStatus.UNAUTHORIZED, MSG_INVALIDO);
		}
		tentativas.registrarSucesso(email);

		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(auth);
		SecurityContextHolder.setContext(contexto);
		securityContextRepository.saveContext(contexto, request, response);

		UsuarioAutenticado usuario = (UsuarioAutenticado) auth.getPrincipal();
		return ResponseEntity.ok(new UsuarioLogadoResponse(
				usuario.getId(), usuario.getNome(), usuario.getUsername(), usuario.getPapel()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
			Authentication auth) {
		new SecurityContextLogoutHandler().logout(request, response, auth);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public ResponseEntity<UsuarioLogadoResponse> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return contas.buscarAtivo(usuario.getId())
				.map(UsuarioLogadoResponse::de)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@PutMapping("/minha-senha")
	public ResponseEntity<Void> alterarSenha(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @RequestBody AlterarSenhaRequest req) {
		contas.alterarSenha(usuario.getId(), req.senhaAtual(), req.novaSenha());
		return ResponseEntity.noContent().build();
	}
}

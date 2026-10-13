package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Relê o usuário logado a cada requisição da API: desativação ou troca de senha encerram a sessão (401) e
 * mudanças de papel ou nome passam a valer imediatamente, sem novo login.
 */
public class SessaoAtualizadaFilter extends OncePerRequestFilter {

	private final UsuarioRepository usuarios;
	private final SecurityContextRepository securityContextRepository;

	public SessaoAtualizadaFilter(UsuarioRepository usuarios, SecurityContextRepository securityContextRepository) {
		this.usuarios = usuarios;
		this.securityContextRepository = securityContextRepository;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		// Login e CSRF ficam de fora: um cookie velho (ex.: de alguém desativado num celular compartilhado)
		// não pode impedir outra pessoa de entrar.
		return !uri.startsWith("/api/") || uri.equals("/api/auth/login") || uri.equals("/api/auth/csrf");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado logado)) {
			chain.doFilter(request, response);
			return;
		}

		// Senha trocada (ex.: ADMIN redefiniu porque o celular foi perdido) também encerra a sessão.
		Optional<Usuario> atual = usuarios.findById(logado.getId())
				.filter(Usuario::isAtivo)
				.filter(u -> Objects.equals(u.getSenhaHash(), logado.getPassword()));
		if (atual.isEmpty()) {
			encerrarSessao(request);
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		if (mudou(logado, atual.get())) {
			atualizarAutenticacao(new UsuarioAutenticado(atual.get()), request, response);
		}
		chain.doFilter(request, response);
	}

	private static boolean mudou(UsuarioAutenticado logado, Usuario atual) {
		return logado.getPapel() != atual.getPapel()
				|| !Objects.equals(logado.getNome(), atual.getNome())
				|| !Objects.equals(logado.getUsername(), atual.getEmail());
	}

	private void atualizarAutenticacao(UsuarioAutenticado principal, HttpServletRequest request,
			HttpServletResponse response) {
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
		SecurityContextHolder.setContext(contexto);
		securityContextRepository.saveContext(contexto, request, response);
	}

	private static void encerrarSessao(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession sessao = request.getSession(false);
		if (sessao != null) {
			sessao.invalidate();
		}
	}
}

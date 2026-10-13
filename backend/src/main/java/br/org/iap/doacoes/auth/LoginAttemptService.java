package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.usuario.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Bloqueia um e-mail por 15 minutos após 5 falhas de login consecutivas (estado em memória). */
@Service
public class LoginAttemptService {

	public static final int MAX_TENTATIVAS = 5;
	public static final Duration BLOQUEIO = Duration.ofMinutes(15);

	private record Tentativas(int falhas, Instant bloqueadoAte) {
	}

	private final Clock clock;
	private final Map<String, Tentativas> porEmail = new ConcurrentHashMap<>();

	public LoginAttemptService(Clock clock) {
		this.clock = clock;
	}

	public boolean estaBloqueado(String email) {
		String chave = Usuario.normalizarEmail(email);
		Tentativas t = porEmail.get(chave);
		if (t == null || t.bloqueadoAte() == null) {
			return false;
		}
		if (clock.instant().isBefore(t.bloqueadoAte())) {
			return true;
		}
		porEmail.remove(chave, t);
		return false;
	}

	public void registrarFalha(String email) {
		String chave = Usuario.normalizarEmail(email);
		Instant agora = clock.instant();
		porEmail.compute(chave, (k, atual) -> {
			if (atual != null && atual.bloqueadoAte() != null && agora.isBefore(atual.bloqueadoAte())) {
				return atual;
			}
			int falhas = (atual == null || atual.bloqueadoAte() != null) ? 1 : atual.falhas() + 1;
			Instant bloqueadoAte = falhas >= MAX_TENTATIVAS ? agora.plus(BLOQUEIO) : null;
			return new Tentativas(falhas, bloqueadoAte);
		});
	}

	public void registrarSucesso(String email) {
		porEmail.remove(Usuario.normalizarEmail(email));
	}
}

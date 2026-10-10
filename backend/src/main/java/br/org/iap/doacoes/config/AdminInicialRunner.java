package br.org.iap.doacoes.config;

import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria o primeiro ADMIN a partir de ADMIN_EMAIL/ADMIN_SENHA quando o banco não tem usuários. */
@Component
public class AdminInicialRunner implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(AdminInicialRunner.class);
	private static final int TAMANHO_MINIMO_SENHA = 8;
	/** Mesma regra da tela de login; um e-mail que não passa nela trancaria o primeiro ADMIN para sempre. */
	private static final Pattern EMAIL_VALIDO = Pattern.compile("^\\S+@\\S+\\.\\S+$");

	private final UsuarioRepository repositorio;
	private final PasswordEncoder encoder;
	private final String email;
	private final String senha;

	public AdminInicialRunner(UsuarioRepository repositorio, PasswordEncoder encoder,
			@Value("${app.admin-inicial.email}") String email,
			@Value("${app.admin-inicial.senha}") String senha) {
		this.repositorio = repositorio;
		this.encoder = encoder;
		this.email = email;
		this.senha = senha;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repositorio.count() > 0) {
			return;
		}
		if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
			LOG.warn("Nenhum usuário cadastrado e ADMIN_EMAIL/ADMIN_SENHA não definidos");
			return;
		}
		if (!EMAIL_VALIDO.matcher(email.trim()).matches()) {
			throw new IllegalStateException(
					"ADMIN_EMAIL inválido: informe um e-mail completo, como admin@igreja.org");
		}
		if (senha.length() < TAMANHO_MINIMO_SENHA) {
			throw new IllegalStateException("ADMIN_SENHA deve ter ao menos 8 caracteres");
		}
		repositorio.save(new Usuario("Administrador", email, encoder.encode(senha), Papel.ADMIN));
		LOG.info("Usuário ADMIN inicial criado: {}", Usuario.normalizarEmail(email));
	}
}

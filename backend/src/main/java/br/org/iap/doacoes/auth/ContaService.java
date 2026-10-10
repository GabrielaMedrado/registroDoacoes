package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.usuario.Usuario;
import br.org.iap.doacoes.usuario.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações do usuário logado sobre a própria conta. */
@Service
public class ContaService {

	/** O BCrypt recusa senhas acima de 72 bytes (acentos ocupam 2 bytes em UTF-8). */
	private static final int MAX_BYTES_BCRYPT = 72;

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;

	public ContaService(UsuarioRepository usuarios, PasswordEncoder encoder) {
		this.usuarios = usuarios;
		this.encoder = encoder;
	}

	@Transactional(readOnly = true)
	public Optional<Usuario> buscarAtivo(Long usuarioId) {
		return usuarios.findById(usuarioId).filter(Usuario::isAtivo);
	}

	@Transactional
	public void alterarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
		if (!encoder.matches(senhaAtual, usuario.getSenhaHash())) {
			throw new CampoInvalidoException("senhaAtual", "Senha atual incorreta.");
		}
		if (novaSenha.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
			throw new CampoInvalidoException("novaSenha", "A nova senha é longa demais. Use uma senha mais curta.");
		}
		usuario.setSenhaHash(encoder.encode(novaSenha));
	}
}

package br.org.iap.doacoes.usuario;

import br.org.iap.doacoes.auth.PoliticaSenha;
import br.org.iap.doacoes.comum.CampoInvalidoException;
import br.org.iap.doacoes.comum.NaoEncontradoException;
import br.org.iap.doacoes.comum.PaginaResponse;
import br.org.iap.doacoes.comum.RegraNegocioException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de usuários pelo ADMIN. Usuários nunca são excluídos, só desativados (R10). */
@Service
public class UsuarioService {

	private static final String MSG_EMAIL_DUPLICADO = "Já existe um usuário com este e-mail.";

	private final UsuarioRepository repositorio;
	private final PasswordEncoder encoder;

	public UsuarioService(UsuarioRepository repositorio, PasswordEncoder encoder) {
		this.repositorio = repositorio;
		this.encoder = encoder;
	}

	@Transactional(readOnly = true)
	public PaginaResponse<UsuarioResponse> listar(int pagina, int tamanho) {
		return PaginaResponse.de(repositorio
				.findAll(PaginaResponse.pageable(pagina, tamanho, Sort.by("nome", "id")))
				.map(UsuarioResponse::de));
	}

	@Transactional
	public UsuarioResponse criar(CriarUsuarioRequest req) {
		String email = Usuario.normalizarEmail(req.email());
		if (repositorio.existsByEmail(email)) {
			throw new CampoInvalidoException("email", MSG_EMAIL_DUPLICADO);
		}
		PoliticaSenha.validar("senha", req.senha());
		Usuario usuario = new Usuario(req.nome().trim(), email, encoder.encode(req.senha()), req.papel());
		return UsuarioResponse.de(repositorio.save(usuario));
	}

	@Transactional
	public UsuarioResponse atualizar(Long id, AtualizarUsuarioRequest req, Long idAdminLogado) {
		Usuario usuario = obter(id);
		if (id.equals(idAdminLogado) && (!req.ativo() || req.papel() != usuario.getPapel())) {
			throw new RegraNegocioException("Você não pode desativar a própria conta nem alterar o próprio papel.");
		}
		String email = Usuario.normalizarEmail(req.email());
		if (repositorio.existsByEmailAndIdNot(email, id)) {
			throw new CampoInvalidoException("email", MSG_EMAIL_DUPLICADO);
		}
		usuario.setNome(req.nome().trim());
		usuario.setEmail(email);
		usuario.setPapel(req.papel());
		usuario.setAtivo(req.ativo());
		return UsuarioResponse.de(repositorio.save(usuario));
	}

	@Transactional
	public void redefinirSenha(Long id, String novaSenha) {
		Usuario usuario = obter(id);
		PoliticaSenha.validar("novaSenha", novaSenha);
		usuario.setSenhaHash(encoder.encode(novaSenha));
	}

	private Usuario obter(Long id) {
		return repositorio.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
	}
}

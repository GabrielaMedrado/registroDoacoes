package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;

public record UsuarioLogadoResponse(Long id, String nome, String email, Papel papel) {

	public static UsuarioLogadoResponse de(Usuario u) {
		return new UsuarioLogadoResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel());
	}
}

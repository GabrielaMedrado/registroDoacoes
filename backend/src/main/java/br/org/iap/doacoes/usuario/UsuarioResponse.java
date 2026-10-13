package br.org.iap.doacoes.usuario;

import java.time.OffsetDateTime;

public record UsuarioResponse(Long id, String nome, String email, Papel papel, boolean ativo,
		OffsetDateTime criadoEm) {

	public static UsuarioResponse de(Usuario u) {
		return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAtivo(), u.getCriadoEm());
	}
}

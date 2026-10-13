package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.comum.CampoInvalidoException;
import java.nio.charset.StandardCharsets;

/** Regras para qualquer senha nova: ao menos 8 caracteres e no máximo 72 bytes (limite do BCrypt). */
public final class PoliticaSenha {

	private static final int TAMANHO_MINIMO = 8;
	/** Acentos ocupam 2 bytes em UTF-8. */
	private static final int MAX_BYTES_BCRYPT = 72;

	private PoliticaSenha() {
	}

	public static void validar(String campo, String senha) {
		if (senha == null || senha.length() < TAMANHO_MINIMO) {
			throw new CampoInvalidoException(campo, "A senha deve ter ao menos 8 caracteres.");
		}
		if (senha.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
			throw new CampoInvalidoException(campo, "A senha é longa demais. Use uma senha mais curta.");
		}
	}
}

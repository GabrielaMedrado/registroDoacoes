package br.org.iap.doacoes.comum;

/** Violação de regra de negócio; responde 409. */
public class RegraNegocioException extends RuntimeException {

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}
}

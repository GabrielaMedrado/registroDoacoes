package br.org.iap.doacoes.comum;

/** Usuário autenticado sem permissão para esta ação específica; responde 403 com a mensagem no detail. */
public class AcessoNegadoException extends RuntimeException {

	public AcessoNegadoException(String mensagem) {
		super(mensagem);
	}
}

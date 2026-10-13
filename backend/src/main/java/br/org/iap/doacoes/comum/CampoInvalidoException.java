package br.org.iap.doacoes.comum;

/** Erro de domínio associado a um campo do formulário; responde 400 com {@code erros}. */
public class CampoInvalidoException extends RuntimeException {

	private final String campo;

	public CampoInvalidoException(String campo, String mensagem) {
		super(mensagem);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}
}

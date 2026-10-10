package br.org.iap.doacoes.comum;

/** Recurso inexistente; responde 404. */
public class NaoEncontradoException extends RuntimeException {

	public NaoEncontradoException(String mensagem) {
		super(mensagem);
	}
}

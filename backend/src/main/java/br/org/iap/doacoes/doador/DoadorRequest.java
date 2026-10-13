package br.org.iap.doacoes.doador;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoadorRequest(
		@NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Use até 120 caracteres.") String nome,
		@Size(max = 30, message = "Use até 30 caracteres.") String telefone,
		@Email(message = "E-mail inválido.") @Size(max = 160, message = "Use até 160 caracteres.") String email,
		@Size(max = 1000, message = "Use até 1000 caracteres.") String observacao) {

	/** Campos opcionais em branco viram nulo (o e-mail vazio também não deve falhar na validação). */
	public DoadorRequest {
		telefone = vazioComoNulo(telefone);
		email = vazioComoNulo(email);
		observacao = vazioComoNulo(observacao);
	}

	private static String vazioComoNulo(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}
}

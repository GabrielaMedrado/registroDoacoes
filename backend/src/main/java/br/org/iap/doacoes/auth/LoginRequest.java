package br.org.iap.doacoes.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") String email,
		@NotBlank(message = "Informe a senha.") String senha) {

	/** Remove espaços nas pontas antes da validação de formato. */
	public LoginRequest {
		email = email == null ? null : email.trim();
	}
}

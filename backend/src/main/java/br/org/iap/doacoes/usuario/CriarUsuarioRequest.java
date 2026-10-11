package br.org.iap.doacoes.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarUsuarioRequest(
		@NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Use até 120 caracteres.") String nome,
		@NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.")
		@Size(max = 160, message = "Use até 160 caracteres.") String email,
		@NotNull(message = "Escolha o papel.") Papel papel,
		@NotBlank(message = "Informe a senha.") String senha) {

	/** Remove espaços nas pontas antes da validação de formato. */
	public CriarUsuarioRequest {
		email = email == null ? null : email.trim();
	}
}

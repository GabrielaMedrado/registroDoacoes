package br.org.iap.doacoes.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
		@NotBlank(message = "Informe a senha atual.") String senhaAtual,
		@NotBlank(message = "Informe a nova senha.")
		@Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres.") String novaSenha) {
}

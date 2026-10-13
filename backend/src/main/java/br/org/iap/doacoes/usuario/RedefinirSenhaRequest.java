package br.org.iap.doacoes.usuario;

import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaRequest(@NotBlank(message = "Informe a nova senha.") String novaSenha) {
}

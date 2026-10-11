package br.org.iap.doacoes.campanha;

import jakarta.validation.constraints.NotNull;

public record AlterarStatusRequest(@NotNull(message = "Informe o status.") StatusCampanha status) {
}

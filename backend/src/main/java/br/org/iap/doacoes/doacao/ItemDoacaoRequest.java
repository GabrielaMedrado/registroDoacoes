package br.org.iap.doacoes.doacao;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ItemDoacaoRequest(
		@NotNull(message = "Escolha o item.") Long necessidadeId,
		@NotNull(message = "Informe a quantidade.") @Positive(message = "A quantidade deve ser maior que zero.")
		@Digits(integer = 10, fraction = 2, message = "Quantidade inválida.") BigDecimal quantidade) {
}

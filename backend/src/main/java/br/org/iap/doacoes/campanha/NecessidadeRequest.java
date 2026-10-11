package br.org.iap.doacoes.campanha;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record NecessidadeRequest(
		@NotBlank(message = "Informe o item.") @Size(max = 120, message = "Use até 120 caracteres.") String item,
		@NotNull(message = "Escolha a unidade.") Unidade unidade,
		@NotNull(message = "Informe a quantidade.") @Positive(message = "A quantidade deve ser maior que zero.")
		@Digits(integer = 10, fraction = 2, message = "Quantidade inválida.") BigDecimal quantidadeMeta,
		@Size(max = 1000, message = "Use até 1000 caracteres.") String observacao) {
}

package br.org.iap.doacoes.campanha;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CampanhaRequest(
		@NotBlank(message = "Informe o título.") @Size(max = 150, message = "Use até 150 caracteres.") String titulo,
		@Size(max = 2000, message = "Use até 2000 caracteres.") String descricao,
		@NotNull(message = "Informe a data de início.") LocalDate dataInicio,
		LocalDate dataFim,
		@Positive(message = "A meta deve ser maior que zero.")
		@Digits(integer = 10, fraction = 2, message = "Valor inválido.") BigDecimal metaValor) {
}

package br.org.iap.doacoes.doacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DoacaoRequest(
		@NotNull(message = "Escolha a campanha.") Long campanhaId,
		Long doadorId,
		@NotNull(message = "Escolha o tipo da doação.") TipoDoacao tipo,
		@Positive(message = "O valor deve ser maior que zero.")
		@Digits(integer = 10, fraction = 2, message = "Valor inválido.") BigDecimal valor,
		FormaPagamento formaPagamento,
		@NotNull(message = "Informe a data da doação.") LocalDate dataDoacao,
		@Size(max = 1000, message = "Use até 1000 caracteres.") String observacao,
		@Valid List<ItemDoacaoRequest> itens,
		// Só na edição: a versão lida pelo formulário (ver DoacaoService.atualizar).
		Long versao) {

	public DoacaoRequest {
		itens = itens == null ? List.of() : itens;
		observacao = observacao == null || observacao.isBlank() ? null : observacao.trim();
	}
}

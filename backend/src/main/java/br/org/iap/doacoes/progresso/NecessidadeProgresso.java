package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;

public record NecessidadeProgresso(Long id, String item, Unidade unidade, BigDecimal quantidadeMeta,
		String observacao, BigDecimal arrecadado, BigDecimal pendente, BigDecimal excedente, int percentual) {

	public static NecessidadeProgresso de(ArrecadadoNecessidade a) {
		return new NecessidadeProgresso(a.necessidadeId(), a.item(), a.unidade(), a.quantidadeMeta(), a.observacao(),
				a.arrecadado(), CalculoProgresso.pendente(a.quantidadeMeta(), a.arrecadado()),
				CalculoProgresso.excedente(a.quantidadeMeta(), a.arrecadado()),
				CalculoProgresso.percentual(a.quantidadeMeta(), a.arrecadado()));
	}
}

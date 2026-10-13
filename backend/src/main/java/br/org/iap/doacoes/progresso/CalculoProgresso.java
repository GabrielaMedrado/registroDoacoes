package br.org.iap.doacoes.progresso;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Regras de progresso (R5 e percentuais da spec), sem acesso a banco. */
public final class CalculoProgresso {

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	private CalculoProgresso() {
	}

	/** R5: o que ainda falta, nunca negativo. */
	public static BigDecimal pendente(BigDecimal meta, BigDecimal arrecadado) {
		return meta.subtract(arrecadado).max(BigDecimal.ZERO);
	}

	/** R5: o que passou da meta. */
	public static BigDecimal excedente(BigDecimal meta, BigDecimal arrecadado) {
		return arrecadado.subtract(meta).max(BigDecimal.ZERO);
	}

	/** Percentual inteiro, arredondado para baixo: só chega a 100 quando a meta é atingida. */
	public static int percentual(BigDecimal meta, BigDecimal arrecadado) {
		if (arrecadado.compareTo(meta) >= 0) {
			return 100;
		}
		return arrecadado.multiply(CEM).divide(meta, 0, RoundingMode.DOWN).intValue();
	}

	/** Média, entre as necessidades, de min(arrecadado / meta, 1); nulo sem necessidades. */
	public static Integer percentualGeral(List<NecessidadeProgresso> necessidades) {
		if (necessidades.isEmpty()) {
			return null;
		}
		BigDecimal soma = BigDecimal.ZERO;
		boolean todasAtingidas = true;
		for (NecessidadeProgresso n : necessidades) {
			if (n.arrecadado().compareTo(n.quantidadeMeta()) >= 0) {
				soma = soma.add(BigDecimal.ONE);
			} else {
				todasAtingidas = false;
				soma = soma.add(n.arrecadado().divide(n.quantidadeMeta(), MathContext.DECIMAL128));
			}
		}
		if (todasAtingidas) {
			return 100;
		}
		BigDecimal media = soma.multiply(CEM).divide(BigDecimal.valueOf(necessidades.size()), MathContext.DECIMAL128);
		// Com alguma necessidade incompleta, o arredondamento nunca pode mostrar 100%.
		return Math.min(media.setScale(0, RoundingMode.DOWN).intValue(), 99);
	}

	/** Percentual da arrecadação em dinheiro; nulo quando a campanha não tem meta em R$. */
	public static Integer percentualValor(BigDecimal metaValor, BigDecimal total) {
		return metaValor == null ? null : percentual(metaValor, total);
	}
}

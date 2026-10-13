package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;

/** Uma necessidade com a soma das quantidades doadas (linha da consulta agregada). */
public record ArrecadadoNecessidade(Long campanhaId, Long necessidadeId, String item, Unidade unidade,
		BigDecimal quantidadeMeta, String observacao, BigDecimal arrecadado) {
}

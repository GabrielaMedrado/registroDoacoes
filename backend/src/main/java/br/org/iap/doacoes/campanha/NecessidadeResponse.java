package br.org.iap.doacoes.campanha;

import java.math.BigDecimal;

public record NecessidadeResponse(Long id, Long campanhaId, String item, Unidade unidade, BigDecimal quantidadeMeta,
		String observacao) {

	public static NecessidadeResponse de(Necessidade n) {
		return new NecessidadeResponse(n.getId(), n.getCampanhaId(), n.getItem(), n.getUnidade(),
				n.getQuantidadeMeta(), n.getObservacao());
	}
}

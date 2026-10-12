package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;

public record PendenciaResponse(Long campanhaId, String campanhaTitulo, Long necessidadeId, String item,
		Unidade unidade, BigDecimal pendente, int percentual) {
}

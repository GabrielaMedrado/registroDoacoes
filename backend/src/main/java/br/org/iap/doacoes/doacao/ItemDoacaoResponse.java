package br.org.iap.doacoes.doacao;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;

public record ItemDoacaoResponse(Long necessidadeId, String item, Unidade unidade, BigDecimal quantidade) {
}

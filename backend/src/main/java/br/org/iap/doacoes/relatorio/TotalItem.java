package br.org.iap.doacoes.relatorio;

import br.org.iap.doacoes.campanha.Unidade;
import java.math.BigDecimal;

public record TotalItem(String item, Unidade unidade, BigDecimal quantidade) {
}

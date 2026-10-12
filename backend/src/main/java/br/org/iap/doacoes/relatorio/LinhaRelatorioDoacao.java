package br.org.iap.doacoes.relatorio;

import br.org.iap.doacoes.doacao.TipoDoacao;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LinhaRelatorioDoacao(Long id, LocalDate dataDoacao, String campanhaTitulo, String doadorNome,
		TipoDoacao tipo, String descricao, BigDecimal valor) {
}

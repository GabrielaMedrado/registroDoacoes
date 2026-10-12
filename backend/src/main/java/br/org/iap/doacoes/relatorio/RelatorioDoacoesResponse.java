package br.org.iap.doacoes.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioDoacoesResponse(LocalDate de, LocalDate ate, long quantidade, BigDecimal totalDinheiro,
		List<TotalItem> totaisItens, List<LinhaRelatorioDoacao> doacoes) {
}

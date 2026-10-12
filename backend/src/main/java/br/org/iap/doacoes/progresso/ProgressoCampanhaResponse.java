package br.org.iap.doacoes.progresso;

import br.org.iap.doacoes.campanha.StatusCampanha;
import java.math.BigDecimal;
import java.util.List;

public record ProgressoCampanhaResponse(Long campanhaId, String titulo, StatusCampanha status,
		Integer percentualGeral, BigDecimal totalDinheiro, BigDecimal metaValor, Integer percentualValor,
		List<NecessidadeProgresso> necessidades) {
}

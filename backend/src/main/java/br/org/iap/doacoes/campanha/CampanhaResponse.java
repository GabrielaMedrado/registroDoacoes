package br.org.iap.doacoes.campanha;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CampanhaResponse(Long id, String titulo, String descricao, LocalDate dataInicio, LocalDate dataFim,
		StatusCampanha status, BigDecimal metaValor, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {

	public static CampanhaResponse de(Campanha c) {
		return new CampanhaResponse(c.getId(), c.getTitulo(), c.getDescricao(), c.getDataInicio(), c.getDataFim(),
				c.getStatus(), c.getMetaValor(), c.getCriadoEm(), c.getAtualizadoEm());
	}
}

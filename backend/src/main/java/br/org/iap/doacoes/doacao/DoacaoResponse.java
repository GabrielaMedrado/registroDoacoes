package br.org.iap.doacoes.doacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DoacaoResponse(Long id, Long campanhaId, String campanhaTitulo, Long doadorId, String doadorNome,
		TipoDoacao tipo, BigDecimal valor, FormaPagamento formaPagamento, LocalDate dataDoacao, String observacao,
		Long registradoPorId, String registradoPorNome, OffsetDateTime criadoEm, List<ItemDoacaoResponse> itens,
		boolean campanhaAtiva, boolean podeAlterar, Long versao) {
}

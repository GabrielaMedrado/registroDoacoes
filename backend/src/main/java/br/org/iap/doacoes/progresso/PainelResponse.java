package br.org.iap.doacoes.progresso;

import java.util.List;

public record PainelResponse(List<ProgressoCampanhaResponse> campanhasAtivas,
		List<PendenciaResponse> maioresPendencias) {
}

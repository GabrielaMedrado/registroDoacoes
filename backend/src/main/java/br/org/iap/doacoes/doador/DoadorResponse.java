package br.org.iap.doacoes.doador;

import java.time.OffsetDateTime;

public record DoadorResponse(Long id, String nome, String telefone, String email, String observacao,
		boolean anonimizado, OffsetDateTime criadoEm) {

	public static DoadorResponse de(Doador d) {
		return new DoadorResponse(d.getId(), d.getNome(), d.getTelefone(), d.getEmail(), d.getObservacao(),
				d.isAnonimizado(), d.getCriadoEm());
	}
}

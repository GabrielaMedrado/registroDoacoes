package br.org.iap.doacoes.doacao;

public enum FormaPagamento {
	PIX("PIX"),
	ESPECIE("Espécie"),
	OUTRO("Outro");

	private final String rotulo;

	FormaPagamento(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}
}

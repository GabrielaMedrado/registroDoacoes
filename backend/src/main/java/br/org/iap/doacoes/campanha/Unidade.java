package br.org.iap.doacoes.campanha;

public enum Unidade {
	UNIDADE("unidade(s)"),
	KG("kg"),
	LITRO("litro(s)"),
	PACOTE("pacote(s)"),
	CAIXA("caixa(s)"),
	PAR("par(es)");

	private final String rotulo;

	Unidade(String rotulo) {
		this.rotulo = rotulo;
	}

	/** Como aparece para as pessoas (igual ao frontend). */
	public String rotulo() {
		return rotulo;
	}
}

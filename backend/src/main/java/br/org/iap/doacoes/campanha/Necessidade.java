package br.org.iap.doacoes.campanha;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "necessidade")
public class Necessidade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campanha_id", nullable = false, updatable = false)
	private Long campanhaId;

	@Column(nullable = false)
	private String item;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Unidade unidade;

	@Column(name = "quantidade_meta", nullable = false)
	private BigDecimal quantidadeMeta;

	private String observacao;

	protected Necessidade() {
	}

	public Necessidade(Long campanhaId, String item, Unidade unidade, BigDecimal quantidadeMeta, String observacao) {
		this.campanhaId = campanhaId;
		atualizar(item, unidade, quantidadeMeta, observacao);
	}

	public final void atualizar(String item, Unidade unidade, BigDecimal quantidadeMeta, String observacao) {
		this.item = item;
		this.unidade = unidade;
		this.quantidadeMeta = quantidadeMeta;
		this.observacao = observacao;
	}

	public Long getId() {
		return id;
	}

	public Long getCampanhaId() {
		return campanhaId;
	}

	public String getItem() {
		return item;
	}

	public Unidade getUnidade() {
		return unidade;
	}

	public BigDecimal getQuantidadeMeta() {
		return quantidadeMeta;
	}

	public String getObservacao() {
		return observacao;
	}
}

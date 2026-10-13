package br.org.iap.doacoes.doacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "item_doacao")
public class ItemDoacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doacao_id")
	private Doacao doacao;

	@Column(name = "necessidade_id", nullable = false)
	private Long necessidadeId;

	@Column(nullable = false)
	private BigDecimal quantidade;

	protected ItemDoacao() {
	}

	public ItemDoacao(Long necessidadeId, BigDecimal quantidade) {
		this.necessidadeId = necessidadeId;
		this.quantidade = quantidade;
	}

	void setDoacao(Doacao doacao) {
		this.doacao = doacao;
	}

	public Long getNecessidadeId() {
		return necessidadeId;
	}

	public BigDecimal getQuantidade() {
		return quantidade;
	}
}

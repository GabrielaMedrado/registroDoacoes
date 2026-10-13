package br.org.iap.doacoes.doacao;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doacao")
public class Doacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campanha_id", nullable = false)
	private Long campanhaId;

	@Column(name = "doador_id")
	private Long doadorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoDoacao tipo;

	private BigDecimal valor;

	@Enumerated(EnumType.STRING)
	@Column(name = "forma_pagamento")
	private FormaPagamento formaPagamento;

	@Column(name = "data_doacao", nullable = false)
	private LocalDate dataDoacao;

	private String observacao;

	@Column(name = "registrado_por", nullable = false, updatable = false)
	private Long registradoPorId;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private OffsetDateTime criadoEm;

	@OneToMany(mappedBy = "doacao", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<ItemDoacao> itens = new ArrayList<>();

	@Column(name = "atualizado_em", nullable = false)
	private OffsetDateTime atualizadoEm;

	@Version
	private Long versao;

	protected Doacao() {
	}

	public Doacao(Long campanhaId, Long doadorId, TipoDoacao tipo, BigDecimal valor, FormaPagamento formaPagamento,
			LocalDate dataDoacao, String observacao, Long registradoPorId) {
		atualizar(campanhaId, doadorId, tipo, valor, formaPagamento, dataDoacao, observacao);
		this.registradoPorId = registradoPorId;
	}

	/** O autor (registradoPorId) nunca muda. */
	public final void atualizar(Long campanhaId, Long doadorId, TipoDoacao tipo, BigDecimal valor,
			FormaPagamento formaPagamento, LocalDate dataDoacao, String observacao) {
		this.campanhaId = campanhaId;
		this.doadorId = doadorId;
		this.tipo = tipo;
		this.valor = valor;
		this.formaPagamento = formaPagamento;
		this.dataDoacao = dataDoacao;
		this.observacao = observacao;
		// Sempre muda: assim o Hibernate aumenta a versão mesmo quando só os itens (outra tabela) mudaram.
		this.atualizadoEm = OffsetDateTime.now();
	}

	public void substituirItens(List<ItemDoacao> novos) {
		itens.clear();
		novos.forEach(item -> {
			item.setDoacao(this);
			itens.add(item);
		});
	}

	@PrePersist
	void aoCriar() {
		criadoEm = OffsetDateTime.now();
	}

	public Long getVersao() {
		return versao;
	}

	public Long getId() {
		return id;
	}

	public Long getCampanhaId() {
		return campanhaId;
	}

	public Long getDoadorId() {
		return doadorId;
	}

	public TipoDoacao getTipo() {
		return tipo;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public FormaPagamento getFormaPagamento() {
		return formaPagamento;
	}

	public LocalDate getDataDoacao() {
		return dataDoacao;
	}

	public String getObservacao() {
		return observacao;
	}

	public Long getRegistradoPorId() {
		return registradoPorId;
	}

	public OffsetDateTime getCriadoEm() {
		return criadoEm;
	}

	public List<ItemDoacao> getItens() {
		return itens;
	}
}

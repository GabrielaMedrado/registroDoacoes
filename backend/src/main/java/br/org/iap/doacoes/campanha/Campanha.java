package br.org.iap.doacoes.campanha;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "campanha")
public class Campanha {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String titulo;

	private String descricao;

	@Column(name = "data_inicio", nullable = false)
	private LocalDate dataInicio;

	@Column(name = "data_fim")
	private LocalDate dataFim;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusCampanha status = StatusCampanha.RASCUNHO;

	@Column(name = "meta_valor")
	private BigDecimal metaValor;

	@Column(name = "criado_por", nullable = false, updatable = false)
	private Long criadoPorId;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private OffsetDateTime criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private OffsetDateTime atualizadoEm;

	protected Campanha() {
	}

	public Campanha(String titulo, String descricao, LocalDate dataInicio, LocalDate dataFim, BigDecimal metaValor,
			Long criadoPorId) {
		atualizar(titulo, descricao, dataInicio, dataFim, metaValor);
		this.criadoPorId = criadoPorId;
	}

	public final void atualizar(String titulo, String descricao, LocalDate dataInicio, LocalDate dataFim,
			BigDecimal metaValor) {
		this.titulo = titulo;
		this.descricao = descricao;
		this.dataInicio = dataInicio;
		this.dataFim = dataFim;
		this.metaValor = metaValor;
	}

	@PrePersist
	void aoCriar() {
		criadoEm = OffsetDateTime.now();
		atualizadoEm = criadoEm;
	}

	@PreUpdate
	void aoAtualizar() {
		atualizadoEm = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getDescricao() {
		return descricao;
	}

	public LocalDate getDataInicio() {
		return dataInicio;
	}

	public LocalDate getDataFim() {
		return dataFim;
	}

	public StatusCampanha getStatus() {
		return status;
	}

	public void setStatus(StatusCampanha status) {
		this.status = status;
	}

	public BigDecimal getMetaValor() {
		return metaValor;
	}

	public Long getCriadoPorId() {
		return criadoPorId;
	}

	public OffsetDateTime getCriadoEm() {
		return criadoEm;
	}

	public OffsetDateTime getAtualizadoEm() {
		return atualizadoEm;
	}
}

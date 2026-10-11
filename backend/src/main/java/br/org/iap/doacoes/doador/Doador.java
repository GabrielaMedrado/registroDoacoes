package br.org.iap.doacoes.doador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "doador")
public class Doador {

	static final String NOME_ANONIMO = "Anônimo";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nome;

	private String telefone;

	private String email;

	private String observacao;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private OffsetDateTime criadoEm;

	protected Doador() {
	}

	public Doador(String nome, String telefone, String email, String observacao) {
		atualizar(nome, telefone, email, observacao);
	}

	public final void atualizar(String nome, String telefone, String email, String observacao) {
		this.nome = nome;
		this.telefone = telefone;
		this.email = email;
		this.observacao = observacao;
	}

	/** R9: apaga os dados pessoais; as doações continuam ligadas a este registro. */
	public void anonimizar() {
		atualizar(NOME_ANONIMO, null, null, null);
	}

	@PrePersist
	void aoCriar() {
		criadoEm = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getEmail() {
		return email;
	}

	public String getObservacao() {
		return observacao;
	}

	public OffsetDateTime getCriadoEm() {
		return criadoEm;
	}
}

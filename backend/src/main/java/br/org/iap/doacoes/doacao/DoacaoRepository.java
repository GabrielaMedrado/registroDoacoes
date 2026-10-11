package br.org.iap.doacoes.doacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DoacaoRepository extends JpaRepository<Doacao, Long>, JpaSpecificationExecutor<Doacao> {
}

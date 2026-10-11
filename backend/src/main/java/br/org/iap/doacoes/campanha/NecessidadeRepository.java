package br.org.iap.doacoes.campanha;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NecessidadeRepository extends JpaRepository<Necessidade, Long> {

	List<Necessidade> findByCampanhaIdOrderByItemAsc(Long campanhaId);

	/** R7: necessidade com doações vinculadas não pode ser excluída. */
	@Query(value = "select exists(select 1 from item_doacao where necessidade_id = :id)", nativeQuery = true)
	boolean possuiDoacoes(@Param("id") Long id);
}

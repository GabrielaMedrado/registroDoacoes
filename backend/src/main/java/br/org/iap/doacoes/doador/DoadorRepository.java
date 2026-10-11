package br.org.iap.doacoes.doador;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoadorRepository extends JpaRepository<Doador, Long> {

	String FILTRO_BUSCA = " from doador where :termo = ''"
			+ " or nome ilike '%' || :termo || '%'"
			+ " or (:digitos <> '' and regexp_replace(telefone, '\\D', '', 'g') like '%' || :digitos || '%')";

	/** Busca por nome (sem diferenciar maiúsculas) ou pelos dígitos do telefone, ignorando a formatação. */
	@Query(value = "select *" + FILTRO_BUSCA + " order by nome, id",
			countQuery = "select count(*)" + FILTRO_BUSCA,
			nativeQuery = true)
	Page<Doador> buscar(@Param("termo") String termo, @Param("digitos") String digitos, Pageable pageable);
}

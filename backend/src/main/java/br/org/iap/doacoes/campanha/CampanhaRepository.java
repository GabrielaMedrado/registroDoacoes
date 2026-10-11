package br.org.iap.doacoes.campanha;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

	Page<Campanha> findByStatus(StatusCampanha status, Pageable pageable);
}

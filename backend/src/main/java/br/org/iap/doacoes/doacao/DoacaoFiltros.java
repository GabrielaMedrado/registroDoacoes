package br.org.iap.doacoes.doacao;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionais da listagem de doações; filtro nulo é ignorado e as datas são inclusivas. */
public final class DoacaoFiltros {

	private DoacaoFiltros() {
	}

	public static Specification<Doacao> de(Long campanhaId, LocalDate de, LocalDate ate, TipoDoacao tipo,
			Long doadorId) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condicoes = new ArrayList<>();
			if (campanhaId != null) {
				condicoes.add(cb.equal(raiz.get("campanhaId"), campanhaId));
			}
			if (de != null) {
				condicoes.add(cb.greaterThanOrEqualTo(raiz.get("dataDoacao"), de));
			}
			if (ate != null) {
				condicoes.add(cb.lessThanOrEqualTo(raiz.get("dataDoacao"), ate));
			}
			if (tipo != null) {
				condicoes.add(cb.equal(raiz.get("tipo"), tipo));
			}
			if (doadorId != null) {
				condicoes.add(cb.equal(raiz.get("doadorId"), doadorId));
			}
			return cb.and(condicoes.toArray(Predicate[]::new));
		};
	}
}

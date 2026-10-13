package br.org.iap.doacoes.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String emailNormalizado);

	boolean existsByEmail(String emailNormalizado);

	boolean existsByEmailAndIdNot(String emailNormalizado, Long id);
}

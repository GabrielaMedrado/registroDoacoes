package br.org.iap.doacoes.auth;

import br.org.iap.doacoes.usuario.Papel;
import br.org.iap.doacoes.usuario.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UsuarioAutenticado implements UserDetails {

	private final Long id;
	private final String nome;
	private final String email;
	private final String senhaHash;
	private final Papel papel;
	private final boolean ativo;

	public UsuarioAutenticado(Usuario usuario) {
		this.id = usuario.getId();
		this.nome = usuario.getNome();
		this.email = usuario.getEmail();
		this.senhaHash = usuario.getSenhaHash();
		this.papel = usuario.getPapel();
		this.ativo = usuario.isAtivo();
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public Papel getPapel() {
		return papel;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()));
	}

	@Override
	public String getPassword() {
		return senhaHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return ativo;
	}
}

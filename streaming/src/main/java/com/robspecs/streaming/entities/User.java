package com.robspecs.streaming.entities;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.robspecs.streaming.enums.Roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", indexes = { @Index(name = "email_idx", columnList = "email", unique = true),
		@Index(name = "username_idx", columnList = "userName", unique = true) })
public class User implements UserDetails { // <-- Implement UserDetails here!

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, unique = true)
	private String userName;

	@Column(nullable = false)
	private Roles role;

	@Column(nullable = false)
	private String password;

	private boolean enabled = false;



	@CreationTimestamp // Automatically sets the creation timestamp
	@Column(nullable = false, updatable = false) // Not nullable, not updatable after creation
	private LocalDateTime createdAt;


	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// Map your Roles enum to Spring Security's GrantedAuthority
		return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	@Override
	public String getPassword() {
		return this.password; // Your entity already has this
	}

	@Override
	public String getUsername() {
		// Spring Security expects getUsername(), use your userName field
		return this.userName; // Or this.email if that's your primary login identifier
	}

	@Override
	public boolean isAccountNonExpired() {
		return true; // Or implement logic for account expiry
	}

	@Override
	public boolean isAccountNonLocked() {
		return true; // Or implement logic for account locking
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true; // Or implement logic for password expiry
	}

	@Override
	public boolean isEnabled() {
		return this.enabled; // Your entity already has this
	}

	// --- Existing Getters and Setters (unmodified) ---
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUserName() { // Keep this for your domain logic, though getUsername() handles security
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Roles getRole() {
		return role;
	}

	public void setRole(Roles role) {
		this.role = role;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Long getUserId() {
		return userId;
	}



	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
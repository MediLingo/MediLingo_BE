package com.example.medilingo.security.entity;

import com.example.medilingo.domain.member.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserDetailsImpl implements UserDetails {
    private final Member member;

    public UserDetailsImpl(Member member) {
        this.member = member;
    }

    public Member getUser() {
        return member;
    }

    @Override
    public String getPassword() {
        return member.getPassword();
    }

    // unsername -> email
    @Override
    public String getUsername() {
        return member.getEmail();
    }

    // 사용자가 권한이 없으므로 빈 리스트 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        // (1) 멤버가 단일 역할을 가진 경우
        UserRole role = member.getRole(); // USER / ADMIN 등
        String authority = role != null ? role.getAuthority() : "ROLE_USER";
        // getAuthority()가 "ROLE_USER"/"ROLE_ADMIN"을 반환하도록 enum을 설계하는 게 베스트

        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

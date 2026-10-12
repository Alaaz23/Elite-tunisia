package tn.elitetunisia.auth.security;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import tn.elitetunisia.auth.entity.UserEntity;

public final class AuthAuthorities {
    private AuthAuthorities() { }

    public static List<GrantedAuthority> from(UserEntity user) {
        return user.getRoles().stream()
                .flatMap(role -> Stream.concat(
                        Stream.of("ROLE_" + role.getRoleKey()),
                        role.getPermissions().stream().map(permission -> permission.getPermissionKey())))
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
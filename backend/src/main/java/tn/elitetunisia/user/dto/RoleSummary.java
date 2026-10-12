package tn.elitetunisia.user.dto;

import java.util.Set;

public record RoleSummary(String roleKey, String displayName, Set<String> permissions) { }
package tn.elitetunisia.user.dto;

import java.util.Set;
import java.util.UUID;

public record UserSummary(UUID id, String firstName, String lastName, String email, String phone,
        String jobTitle, Set<String> roles, String status) { }
// Current security-relevant state of an app user (from app_users), compared with the claims
// of every bearer token to detect revoked sessions.
// Estado atual do usuario usado para revogar tokens antigos.
package com.fwdford.forwardapi.security;

public record UserSecurityState(boolean active, Role role, String dealerId, long tokenVersion) {}

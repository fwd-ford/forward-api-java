// Small in-memory cache of UserSecurityState keyed by user id, used by the authentication
// filter to check on every request that a token still matches the user's current state
// (exists, active, role, dealer, token_version) without a database round trip each time.
//
// Entries live for forward.jwt.user-state-cache-ttl (default 30 s; 0 disables caching). Admin
// writes call invalidateAfterCommit, so on this instance a change takes effect immediately;
// on other instances it takes at most one TTL. Absent users are cached too (deleted users
// stay rejected).
// Cache curto do estado do usuario para revogar tokens sem consultar o banco a cada request.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.repository.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public final class UserStateCache {

  /** Upper bound on cached users; the cache is reset when exceeded (memory guard). */
  static final int MAX_ENTRIES = 10_000;

  private record Entry(Optional<UserSecurityState> state, long expiresAtNanos) {}

  private final Function<String, Optional<UserSecurityState>> loader;
  private final long ttlNanos;
  private final LongSupplier ticker;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  @Autowired
  public UserStateCache(
      UserRepository users, @Value("${forward.jwt.user-state-cache-ttl:30s}") Duration ttl) {
    this(id -> users.findSecurityState(UUID.fromString(id)), ttl, System::nanoTime);
  }

  UserStateCache(
      Function<String, Optional<UserSecurityState>> loader, Duration ttl, LongSupplier ticker) {
    if (ttl.isNegative()) {
      throw new IllegalArgumentException("user-state-cache-ttl must not be negative");
    }
    this.loader = loader;
    this.ttlNanos = ttl.toNanos();
    this.ticker = ticker;
  }

  /** Current state of the user, or empty when the user does not exist (anymore). */
  public Optional<UserSecurityState> get(String userId) {
    if (ttlNanos == 0) {
      return loader.apply(userId);
    }
    long now = ticker.getAsLong();
    Entry cached = entries.get(userId);
    if (cached != null && now - cached.expiresAtNanos() < 0) {
      return cached.state();
    }
    Optional<UserSecurityState> state = loader.apply(userId);
    if (entries.size() >= MAX_ENTRIES) {
      entries.clear();
    }
    entries.put(userId, new Entry(state, now + ttlNanos));
    return state;
  }

  public void invalidate(String userId) {
    entries.remove(userId);
  }

  /**
   * Invalidates now and, when a transaction is active, again after it commits, so a request running
   * concurrently with the admin write cannot re-cache the pre-commit state.
   */
  public void invalidateAfterCommit(String userId) {
    invalidate(userId);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              invalidate(userId);
            }
          });
    }
  }

  int size() {
    return entries.size();
  }
}

package com.fwdford.forwardapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** TTL behaviour of the user state cache used for token revocation (fake clock). */
class UserStateCacheTest {

  private static final String USER = "ad000000-0000-4000-8000-000000000003";
  private static final String DEALER = "d0000000-0000-4000-8000-000000000001";

  private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
  private final AtomicInteger loads = new AtomicInteger();
  private final Map<String, UserSecurityState> db = new HashMap<>();

  private UserStateCache cache(Duration ttl) {
    return new UserStateCache(
        id -> {
          loads.incrementAndGet();
          return Optional.ofNullable(db.get(id));
        },
        ttl,
        nanos::get);
  }

  private void advance(Duration d) {
    nanos.addAndGet(d.toNanos());
  }

  @BeforeEach
  void seed() {
    db.put(USER, new UserSecurityState(true, Role.ATENDENTE, DEALER, 0));
  }

  @Test
  void serves_from_cache_within_ttl() {
    UserStateCache cache = cache(Duration.ofSeconds(30));
    cache.get(USER);
    db.put(USER, new UserSecurityState(false, Role.ATENDENTE, DEALER, 1));
    advance(Duration.ofSeconds(29));

    assertTrue(cache.get(USER).orElseThrow().active());
    assertEquals(1, loads.get());
  }

  @Test
  void reloads_after_ttl_expires() {
    UserStateCache cache = cache(Duration.ofSeconds(30));
    cache.get(USER);
    db.put(USER, new UserSecurityState(false, Role.ATENDENTE, DEALER, 1));
    advance(Duration.ofSeconds(30));

    UserSecurityState state = cache.get(USER).orElseThrow();
    assertFalse(state.active());
    assertEquals(1, state.tokenVersion());
    assertEquals(2, loads.get());
  }

  @Test
  void zero_ttl_always_reads_the_database() {
    UserStateCache cache = cache(Duration.ZERO);
    cache.get(USER);
    cache.get(USER);
    cache.get(USER);

    assertEquals(3, loads.get());
    assertEquals(0, cache.size());
  }

  @Test
  void invalidate_forces_a_fresh_read() {
    UserStateCache cache = cache(Duration.ofMinutes(5));
    cache.get(USER);
    db.put(USER, new UserSecurityState(true, Role.GESTOR, DEALER, 1));

    cache.invalidate(USER);

    assertEquals(Role.GESTOR, cache.get(USER).orElseThrow().role());
    assertEquals(2, loads.get());
  }

  @Test
  void deleted_user_is_cached_as_absent() {
    UserStateCache cache = cache(Duration.ofSeconds(30));
    db.remove(USER);

    assertTrue(cache.get(USER).isEmpty());
    assertTrue(cache.get(USER).isEmpty());
    assertEquals(1, loads.get());
  }

  @Test
  void invalidate_after_commit_also_evicts_when_the_transaction_commits() {
    UserStateCache cache = cache(Duration.ofMinutes(5));
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.invalidateAfterCommit(USER);
      // A concurrent request re-caches the pre-commit state before the admin write commits.
      cache.get(USER);
      db.put(USER, new UserSecurityState(false, Role.ATENDENTE, DEALER, 1));
      for (TransactionSynchronization sync :
          TransactionSynchronizationManager.getSynchronizations()) {
        sync.afterCommit();
      }
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    assertFalse(cache.get(USER).orElseThrow().active());
  }

  @Test
  void memory_guard_resets_the_cache_when_full() {
    UserStateCache cache = cache(Duration.ofMinutes(5));
    for (int i = 0; i < UserStateCache.MAX_ENTRIES + 5; i++) {
      cache.get("user-" + i);
    }
    assertTrue(cache.size() <= UserStateCache.MAX_ENTRIES);
  }

  @Test
  void negative_ttl_is_rejected() {
    assertThrows(IllegalArgumentException.class, () -> cache(Duration.ofSeconds(-1)));
  }
}

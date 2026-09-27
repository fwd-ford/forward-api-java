// Lead lifecycle state machine.
//   new       -> assigned | contacted | lost
//   assigned  -> contacted | lost
//   contacted -> converted | lost
//   converted, lost, expired: terminal (no outgoing transition)
// "expired" is only set by the expiration job, never through the API.
// Maquina de estados do lead; converted, lost e expired sao terminais.
package com.fwdford.forwardapi.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum LeadStatus {
  NEW,
  ASSIGNED,
  CONTACTED,
  CONVERTED,
  LOST,
  EXPIRED;

  private static final Map<LeadStatus, Set<LeadStatus>> TRANSITIONS =
      Map.of(
          NEW, Set.of(ASSIGNED, CONTACTED, LOST),
          ASSIGNED, Set.of(CONTACTED, LOST),
          CONTACTED, Set.of(CONVERTED, LOST),
          CONVERTED, Set.of(),
          LOST, Set.of(),
          EXPIRED, Set.of());

  /** Wire value, e.g. "contacted". */
  public String value() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean isTerminal() {
    return TRANSITIONS.get(this).isEmpty();
  }

  public boolean canTransitionTo(LeadStatus target) {
    return TRANSITIONS.get(this).contains(target);
  }

  public static Optional<LeadStatus> fromValue(String value) {
    return Arrays.stream(values()).filter(s -> s.value().equals(value)).findFirst();
  }

  public static List<String> wireValues() {
    return Arrays.stream(values()).map(LeadStatus::value).toList();
  }
}

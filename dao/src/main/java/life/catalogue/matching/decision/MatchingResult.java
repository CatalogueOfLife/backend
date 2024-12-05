package life.catalogue.matching.decision;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MatchingResult {
  public static class IgnoredUsage {
    public final NameUsage usage;
    public final String reason;

    public IgnoredUsage(NameUsage usage, String reason) {
      this.usage = usage;
      this.reason = reason;
    }
  }
  private final SimpleName query;
  private final List<NameUsage> matches = new ArrayList<>();
  private final List<IgnoredUsage> ignored = new ArrayList<>();

  public MatchingResult(SimpleName query) {
    this.query = query;
  }

  public List<NameUsage> getMatches() {
    return matches;
  }

  public void addMatch(NameUsage match) {
    this.matches.add(match);
  }

  public List<IgnoredUsage> getIgnored() {
    return ignored;
  }

  public void ignore(NameUsage usage, String reason) {
    this.ignored.add(new IgnoredUsage(usage, reason));
  }

  public int size() {
    return matches.size();
  }

  public boolean isEmpty() {
    return matches.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchingResult)) return false;
    MatchingResult that = (MatchingResult) o;
    return Objects.equals(matches, that.matches) && Objects.equals(ignored, that.ignored);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matches, ignored);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Match for >")
      .append(query)
      .append("< matches: ");
    boolean first = true;
    if (isEmpty()) {
      sb.append(" None");
    } else {
      for (var m : matches) {
        if (first) {
          first = false;
        } else {
          sb.append("; ");
        }
        sb.append(m.getLabel())
          .append(" [")
          .append(m.getId())
          .append(']');
      }
    }
    sb.append(". Ignored: ");
    first = true;
    for (var m : ignored) {
      if (first) {
        first = false;
      } else {
        sb.append("; ");
      }
      sb.append(m.usage.getLabel())
        .append(" [")
        .append(m.usage.getId())
        .append("] - ")
        .append(m.reason);
    }

    return sb.toString();
  }
}

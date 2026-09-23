package life.catalogue.matching.person.harvest;

import java.util.List;

import javax.annotation.Nullable;

final class Names {
  private Names() {
  }

  @Nullable
  static String ordered(List<String> parts, @Nullable String label) {
    return parts.isEmpty() ? null : String.join(" ", parts);
  }

  @Nullable
  static String suffix(@Nullable String label) {
    return null;
  }
}

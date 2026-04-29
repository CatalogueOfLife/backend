package life.catalogue.matching;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Parsed representation of the {@code ADD_IDENTIFIERS_FROM} dataset setting.
 *
 * <p>Format: {@code {integer}(LXR|LR)?}
 * <ul>
 *   <li>{@code "1000"} — match against dataset 1000 directly</li>
 *   <li>{@code "2207LR"} — match against the latest public release of project 2207</li>
 *   <li>{@code "2207LXR"} — match against the latest extended release of project 2207</li>
 * </ul>
 *
 * <p>Scope resolution: if the {@link IdentifierScopeResolver} has a configured mapping for the
 * {@link #projectOrDatasetKey} it is used; otherwise the scope falls back to {@code clb{resolvedKey}}.
 */
public class AddIdentifiersSpec {

  public enum Suffix { NONE, LR, LXR }

  private static final Pattern PATTERN = Pattern.compile("^(\\d+)(LXR|LR)?$");

  /** The integer part of the value — the project key for LR/LXR, or the dataset key for NONE. */
  public final int projectOrDatasetKey;
  public final Suffix suffix;

  private AddIdentifiersSpec(int projectOrDatasetKey, Suffix suffix) {
    this.projectOrDatasetKey = projectOrDatasetKey;
    this.suffix = suffix;
  }

  /**
   * Parses the {@code ADD_IDENTIFIERS_FROM} setting value.
   * @throws IllegalArgumentException if the value does not match the expected format
   */
  public static AddIdentifiersSpec parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ADD_IDENTIFIERS_FROM value must not be blank");
    }
    Matcher m = PATTERN.matcher(value.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException("Invalid ADD_IDENTIFIERS_FROM value: '" + value
        + "'. Expected format: {integer}(LXR|LR)?");
    }
    int key = Integer.parseInt(m.group(1));
    String sfx = m.group(2);
    Suffix suffix = sfx == null ? Suffix.NONE : Suffix.valueOf(sfx);
    return new AddIdentifiersSpec(key, suffix);
  }

  /**
   * Resolves the actual dataset key to use for matching.
   * For {@link Suffix#NONE} this is just {@link #projectOrDatasetKey}.
   * For {@link Suffix#LR} and {@link Suffix#LXR} this queries the database for the latest
   * release or extended release of the project.
   *
   * @throws IllegalStateException if no suitable release exists
   */
  public int resolveDatasetKey(SqlSessionFactory sessionFactory) {
    if (suffix == Suffix.NONE) return projectOrDatasetKey;
    try (SqlSession session = sessionFactory.openSession()) {
      return resolveDatasetKey(session.getMapper(DatasetMapper.class));
    }
  }

  public int resolveDatasetKey(DatasetMapper dm) {
    if (suffix == Suffix.NONE) return projectOrDatasetKey;
    DatasetOrigin origin = suffix == Suffix.LR ? DatasetOrigin.RELEASE : DatasetOrigin.XRELEASE;
    Integer resolved = dm.latestRelease(projectOrDatasetKey, true, origin);
    if (resolved == null) {
      throw new IllegalStateException("No " + suffix + " found for project " + projectOrDatasetKey);
    }
    return resolved;
  }

  /**
   * Returns the identifier scope string for the matched usages.
   * Prefers a configured mapping for {@link #projectOrDatasetKey}; falls back to {@code clb{resolvedKey}}.
   */
  public String resolveScope(IdentifierScopeResolver scopeResolver, int resolvedKey) {
    if (scopeResolver != null) {
      String mapped = scopeResolver.resolve(projectOrDatasetKey);
      if (mapped != null) return mapped;
    }
    return "clb" + resolvedKey;
  }

  @Override
  public String toString() {
    return projectOrDatasetKey + (suffix == Suffix.NONE ? "" : suffix.name());
  }
}

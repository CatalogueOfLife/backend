package life.catalogue.api.vocab;

import com.google.common.collect.ImmutableMap;
import life.catalogue.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable ISO 639 3 letter language class to be used as singletons.
 * Uses data from https://iso639-3.sil.org/code_tables/download_tables
 */
public class Language implements Comparable<Language> {
  
  private static final Comparator<Language> NATURAL_ORDER = Comparator.comparing(Language::getCode);
  public static String ISO_VERSION = "_20200130.tab";
  public static String ISO_CODE_FILE = "vocab/language/iso-639-3"+ISO_VERSION;
  public static String OVERRIDES = "vocab/language/iso-639-3-override.tsv";

  public static final Map<String, Language> LANGUAGES = ImmutableMap.copyOf(load());

  public static Collection<Language> values() {
    return LANGUAGES.values();
  }

  private static Map<String, Language> load() {
    // Id	Print_Name	Inverted_Name
    AtomicInteger num = new AtomicInteger();
    Map<String, Language> langs = Resources.tabRows(ISO_CODE_FILE)
        .filter(row -> num.incrementAndGet() > 1 && row != null && !StringUtils.isBlank(row[0]))
        // Id	Part2B	Part2T	Part1	Scope	Language_Type	Ref_Name	Comment
        .map(row -> new Language(row[0], row[6]))
        .collect(Collectors.toMap(l -> l.code, Function.identity()));
    // allow for custom overrides in sensitive political areas
    Resources.tabRows(OVERRIDES).forEach( row -> {
      langs.put(row[0], new Language(row[0], row[1]));
    });
    return langs;
  }

  public static Language byCode(String code) {
    if (code == null) return null;
    return LANGUAGES.getOrDefault(code.toLowerCase(), null);
  }
  
  /**
   * ISO 639 3 letter code.
   */
  private final String code;

  /**
   * The official ICS time span name.
   */
  private final String title;
  
  
  public Language(String code, String title) {
    this.code = code;
    this.title = title;
  }
  
  public String getCode() {
    return code;
  }
  
  public String getTitle() {
    return title;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Language language = (Language) o;
    return Objects.equals(title, language.title) &&
        Objects.equals(code, language.code);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(title, code);
  }
  
  @Override
  public int compareTo(@NotNull Language o) {
    return NATURAL_ORDER.compare(this, o);
  }
}

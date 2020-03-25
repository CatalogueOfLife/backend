package life.catalogue.api.vocab;

import com.google.common.collect.ImmutableMap;
import life.catalogue.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Immutable ISO 639 3 letter language class to be used as singletons.
 * Uses data from https://iso639-3.sil.org/code_tables/download_tables
 */
public class Language implements Comparable<Language> {
  
  private static final Comparator<Language> NATURAL_ORDER = Comparator.comparing(Language::getCode);
  public static String ISO_CODE_FILE = "vocab/language/iso-639-3_Name_Index_20190408.tab";
  
  public static final Map<String, Language> LANGUAGES = ImmutableMap.copyOf(load());

  public static Collection<Language> values() {
    return LANGUAGES.values();
  }

  private static Map<String, Language> load() {
    // Id	Print_Name	Inverted_Name
    BufferedReader br = Resources.reader(ISO_CODE_FILE);
    // we first build a list of all languages as the ISO reference file contains duplicates with slightly different titles
    AtomicInteger num = new AtomicInteger();
    List<Language> all = br.lines()
        .filter(l -> num.incrementAndGet() > 1 && !l.startsWith("#") && !StringUtils.isBlank(l))
        .map( line -> {
          String[] row = line.split("\t");
          return new Language(row[0], row[1]);
        })
        .collect(Collectors.toList());
    Map<String, Language> langs = new HashMap<>();
    for (Language l : all) {
      if (!langs.containsKey(l.code)) {
        langs.put(l.code, l);
      }
    }
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

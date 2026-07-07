package life.catalogue.common.tax;

import life.catalogue.api.model.Name;
import life.catalogue.common.io.Resources;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.util.UnicodeUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import jakarta.validation.constraints.NotNull;

/**
 * Utility to compare scientific name authorships, i.e. the recombination and basionym author and publishing year.
 * Author strings are normalized to ASCII and then compared. As authors are often abbreviated in all kind of ways a shared common substring is accepted
 * as a positive equality.
 * If any of the names given has an empty author & year the results will always be Equality.UNKNOWN.
 * <p>
 * The class exposes two kind of compare methods. A strict one always requiring both year and author to match
 * and a more lax default comparison that only looks at years when the authors differ (as it is quite hard to compare authors)
 */
public class AuthorshipNormalizer {
  private static final Logger LOG = LoggerFactory.getLogger(AuthorshipNormalizer.class);

  private static final Pattern FIL = Pattern.compile("([A-Z][a-z]*)[\\. ]\\s*f(:?il)?\\.?\\b");
  private static final Pattern TRANSLITERATIONS = Pattern.compile("([auo])e", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_INITIALS = Pattern.compile("^(.*[a-z]{2,})((?: [a-z])+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern AUTHOR = Pattern.compile("^((?:[a-z]\\s)*).*?([a-z]+)( (?:filius|fil|fl|f|bis|ter)\\.?)?$");
  private static final String AUTHOR_MAP_FILENAME = "authorship/authormap.txt";
  private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}&&[^,]]+");
  private final Map<String, String> botMap;   // BOT + ANY entries
  private final Map<String, String> zooMap;   // ZOO + ANY entries

  public static final AuthorshipNormalizer INSTANCE = createWithAuthormap();

  public static AuthorshipNormalizer createWithoutAuthormap() {
    return new AuthorshipNormalizer(Maps.newHashMap(), Maps.newHashMap());
  }

  private static AuthorshipNormalizer createWithAuthormap() {
    Map<String, String> bot = new HashMap<>();
    Map<String, String> zoo = new HashMap<>();
    try {
      Resources.tabRows(AUTHOR_MAP_FILENAME).forEach(row -> {
        if (row.length < 3) return; // canonical + code + at least one alias
        String value = row[0];
        String code = row[1].trim().toUpperCase();
        for (int i = 2; i < row.length; i++) {
          if ("BOT".equals(code) || "ANY".equals(code)) putNormalized(bot, row[i], value);
          if ("ZOO".equals(code) || "ANY".equals(code)) putNormalized(zoo, row[i], value);
        }
      });
    } catch (Exception e) {
      LOG.warn("Failed to load author abbreviation map from {}", AUTHOR_MAP_FILENAME);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to load author abbreviation map from {}", AUTHOR_MAP_FILENAME, e);
      }
    }
    return new AuthorshipNormalizer(bot, zoo);
  }

  private static void putNormalized(Map<String, String> map, String rawKey, String rawValue) {
    String key = normalize(rawKey);
    String val = normalize(rawValue);
    if (key != null && val != null) {
      if (map.containsKey(key) && !map.get(key).equals(val)) {
        LOG.warn("Authormap contains duplicate key {} for {} - previous standard value={}", key, val, map.get(key));
      }
      map.put(key, val);
    }
  }

  private AuthorshipNormalizer(Map<String, String> botMap, Map<String, String> zooMap) {
    this.botMap = ImmutableMap.copyOf(botMap);
    this.zooMap = ImmutableMap.copyOf(zooMap);
    LOG.info("Created author normalizer with {} botanical and {} zoological abbreviation entries", botMap.size(), zooMap.size());
  }
  
  /**
   * We combine both regular and ex authors to a list of normalised strings if no code is given.
   * Otherwise the code specific relevant part is only extracted, i.e. the authors/last in botany
   * and the first exAuthors for zoology.
   *
   * @return queue of normalized authors, never null.
   * ascii only, lower cased string without punctuation. Empty string instead of null.
   * Umlaut transliterations reduced to single letter
   */
  public static List<String> normalize(Authorship authorship, NomCode code) {
    if (authorship == null) return Collections.EMPTY_LIST;

    final List<String> authors = new ArrayList<>();
    if (authorship.hasExAuthors()) {
      if (authorship.getAuthors() != null && (code != NomCode.ZOOLOGICAL)) {
        authors.addAll(authorship.getAuthors());
      }
      if (authorship.getExAuthors() != null  && (code == null || code == NomCode.ZOOLOGICAL)) {
        authors.addAll(authorship.getExAuthors());
      }

    } else if (authorship.getAuthors() != null) {
      authors.addAll(authorship.getAuthors());
    }
    if (authors.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    List<String> normed = new ArrayList<>(authors.size());
    for (String x : authors) {
      x = normalize(x);
      // ignore et al authors
      if (x != null && !x.equals("al")) {
        normed.add(x);
      }
    }
    return normed;
  }
  
  /**
   * Shortcut doing author normalization for a name useful for fuzzy comparison.
   * Does ASCII folding, strips punctuation, ex authors, year and initials.
   *
   * Also does a lookup of known authors
   * and finally a alphabetical sorting of unique names only.
   *
   * For names without a parsed authorship normalize the full authorship string but keep its order, not trying to parse it again.
   *
   * See https://github.com/Sp2000/colplus-backend/issues/341
   */
  public List<String> normalizeName(Name n) {
    if (n.hasParsedAuthorship()) {
      // only compare basionym if existing, ignore ex and year
      Authorship authors;
      if (n.hasBasionymAuthorship()) {
        authors = n.getBasionymAuthorship();
      } else {
        authors = n.getCombinationAuthorship();
      }
      return lookup(normalize(authors, null)).stream()
          .map(Author::new)
          .map(a -> a.surname)
          .distinct()
          .sorted()
          .collect(Collectors.toList());
      
    } else if (n.getAuthorship() != null){
      return Lists.newArrayList(normalize(n.getAuthorship()));
    }
    
    return Collections.EMPTY_LIST;
  }
  
  public static String normalize(String x) {
    if (StringUtils.isBlank(x)) {
      return null;
    }
    // fold to ascii
    x = UnicodeUtils.foldToAscii(x);
    // simplify umlauts transliterated properly with additional e
    x = TRANSLITERATIONS.matcher(x).replaceAll("$1");
    // replace all punctuation but commas
    x = PUNCTUATION.matcher(x).replaceAll(" ");
    // norm space
    x = StringUtils.normalizeSpace(x);
    // move initials to front
    var m = TRAILING_INITIALS.matcher(x);
    if (m.find() && !m.group(2).trim().equals("f")) {
      x = m.replaceFirst("$2 $1").trim();
    }
    // normalize filius
    x = FIL.matcher(x).replaceAll("$1 filius");

    if (StringUtils.isBlank(x)) {
      return null;
    }
    return x.toLowerCase();
  }
  
  private Map<String, String> mapFor(NomCode code) {
    return code == NomCode.ZOOLOGICAL ? zooMap : botMap;
  }

  /**
   * Looks up individual authors from an authorship string.
   * Botanical / unknown-code lookup.
   *
   * @return entire authorship string with expanded authors if found
   */
  public String lookup(String normalizedAuthor) {
    return lookup(normalizedAuthor, null);
  }

  public String lookup(String normalizedAuthor, NomCode code) {
    Map<String, String> map = mapFor(code);
    if (normalizedAuthor != null && map.containsKey(normalizedAuthor)) {
      return map.get(normalizedAuthor);
    }
    return normalizedAuthor;
  }

  public List<String> lookup(List<String> authorTeam) {
    return lookup(authorTeam, (NomCode) null);
  }

  public List<String> lookup(List<String> authorTeam, NomCode code) {
    List<String> authors = Lists.newArrayList();
    for (String author : authorTeam) {
      authors.add(lookup(author, code));
    }
    return authors;
  }

  public List<String> lookup(List<String> normalizedAuthorTeam, int minAuthorLengthWithoutLookup) {
    return lookup(normalizedAuthorTeam, minAuthorLengthWithoutLookup, null);
  }

  public List<String> lookup(List<String> normalizedAuthorTeam, int minAuthorLengthWithoutLookup, NomCode code) {
    List<String> authors = Lists.newArrayList();
    for (String author : normalizedAuthorTeam) {
      if (minAuthorLengthWithoutLookup > 0 && author.length() < minAuthorLengthWithoutLookup) {
        authors.add(lookup(author, code));
      } else {
        authors.add(author);
      }
    }
    return authors;
  }


  public static class Author {
    public final @NotNull String fullname;
    public final String initials;
    public final @NotNull String surname;
    public final String suffix;
  
    public Author(String a) {
      fullname = a;
      Matcher m = AUTHOR.matcher(a);
      if (m.find()) {
        initials = trim(m.group(1));
        surname = trim(m.group(2));
        var suff = trim(m.group(3));
        // standardize all filius spellings
        suffix = suff != null && suff.startsWith("f") ? "filius" : suff;
      } else {
        LOG.debug("Cannot parse single author: {}", a);
        initials = "";
        surname = trim(a);
        suffix = "";
      }
    }

    public Author(String fullname, String initials, String surname, String suffix) {
      this.fullname = Preconditions.checkNotNull(fullname);
      this.initials = initials;
      this.surname = Preconditions.checkNotNull(surname);
      this.suffix = suffix;
    }

    private String trim(String x) {
      return x == null ? null : StringUtils.trimToNull(x);
    }
  
    public boolean hasInitials() {
      return initials != null && !initials.isEmpty();
    }

    public boolean initialsOrSuffixDiffer(Author other) {
      return initialsDiffer(other) || suffixDiffer(other);
    }

    private boolean suffixDiffer(Author other) {
      // we only allow very few & selected suffices in the parser regex, so we can be use they mean they are different!
      return !Objects.equals(suffix, other.suffix);
    }

    /**
     * Gracefully compare initials of the first author only
     *
     * @return true if they differ
     */
    private boolean initialsDiffer(Author other) {
      if (hasInitials() && other.hasInitials()) {
        if (initials.equals(other.initials)) {
          return false;
          
        } else {
          // if one set of chars is a subset of the other we consider this a match
          List<Character> smaller = Lists.charactersOf(StringUtils.deleteWhitespace(initials));
          List<Character> larger = Lists.charactersOf(StringUtils.deleteWhitespace(other.initials));
          if (smaller.size() > larger.size()) {
            // swap, the Sets difference method needs the right inputs
            List<Character> tmp = smaller;
            smaller = larger;
            larger = tmp;
          }
          // is the smaller included in the larger, possibly requiring multiple chars?
          if (org.apache.commons.collections4.CollectionUtils.isSubCollection(smaller, larger)) {
            // one is a subset of the other
            return false;
          }
        }
        // they seem to differ
        return true;
        
      } else {
        // no initials in at least one of them
        return false;
      }
    }
    
  }
  
}

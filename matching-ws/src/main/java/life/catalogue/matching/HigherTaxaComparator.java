package life.catalogue.matching;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Closeables;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HigherTaxaComparator {

  private final Logger LOG = LoggerFactory.getLogger(HigherTaxaComparator.class);
  private static final Map<Rank, String> SYNONYM_FILENAMES =
      Map.of(
          Rank.KINGDOM, "kingdom.txt",
          Rank.PHYLUM, "phylum.txt",
          Rank.CLASS, "class.txt",
          Rank.ORDER, "order.txt",
          Rank.FAMILY, "family.txt");
  private static final Set<String> NON_NAMES = new HashSet<>();

  private final Map<Rank, Map<String, String>> syn = new HashMap<>();
  private final Map<String, Kingdom> kingdoms =
      Arrays.stream(Kingdom.values())
          .collect(Collectors.toMap(k -> norm(k.name()), Function.identity()));

  public HigherTaxaComparator() {
    try {
      loadOnlineDicts();
    } catch (Exception e) {
      LOG.error("Failed to load dictionary files from classpath", e);
    }
  }

  /**
   * Compares a single higher rank and returns the matching confidence supplied.
   *
   * @param rank the rank to be compared
   * @param query the classification of the query
   * @param ref the classification of the nub reference usage
   * @param match confidence returned if the classifications match for the given rank
   * @param mismatch confidence returned if the classifications do not match for the given rank
   * @param missing confidence returned if one or both classifications have missing information for
   *     the given rank
   * @return match, mismatch or missing confidence depending on match
   */
  public int compareHigherRank(
      Rank rank,
      LinneanClassification query,
      LinneanClassification ref,
      int match,
      int mismatch,
      int missing) {
    if (!StringUtils.isBlank(query.getHigherRank(rank))
        && !StringUtils.isBlank(ref.getHigherRank(rank))) {
      String querySyn = lookup(query.getHigherRank(rank), rank);
      String refSyn = lookup(ref.getHigherRank(rank), rank);
      if (!StringUtils.isBlank(querySyn)
          && !StringUtils.isBlank(refSyn)
          && querySyn.equalsIgnoreCase(refSyn)) {
        return match;
      } else {
        return mismatch;
      }
    }
    return missing;
  }

  public boolean isInKingdoms(LinneanClassification n, Kingdom... kingdoms) {
    String syn = lookup(n.getKingdom(), Rank.KINGDOM);
    if (Objects.nonNull(syn) && !syn.isEmpty()) {
      for (Kingdom kingdom : kingdoms) {
        if (syn.equalsIgnoreCase(kingdom.name())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Lookup higher taxa synonym dictionary across all ranks and return the first match found
   *
   * @param higherTaxon
   * @return the looked up accepted name or the original higherTaxon
   */
  @VisibleForTesting
  protected String lookup(String higherTaxon) {
    if (higherTaxon == null) {
      return null;
    }
    for (Rank r : syn.keySet()) {
      String result = lookup(higherTaxon, r);
      if (result != null) {
        return result;
      }
    }
    return higherTaxon;
  }

  /**
   * Lookup synonym for given higher rank. Can be null.
   *
   * @param higherTaxon higher rank name, case-insensitive
   * @param rank the rank to lookup for
   * @return the looked up accepted name, null for blacklisted names or the original higherTaxon if
   *     no synonym is known
   */
  public String lookup(String higherTaxon, Rank rank) {
    if (higherTaxon == null) {
      return null;
    }
    if (isBlacklisted(higherTaxon)) {
      return null;
    }
    if (syn.containsKey(rank)) {
      String normedHT = norm(higherTaxon);
      Map<String, String> x = syn.get(rank);
      if (syn.get(rank).containsKey(normedHT)) {
        return syn.get(rank).get(normedHT);
      }
    }
    return higherTaxon;
  }

  /**
   * Check for obvious, blacklisted garbage and return true if thats the case. The underlying set is
   * hosted at http://rs.gbif.org/dictionaries/authority/blacklisted.txt
   */
  public boolean isBlacklisted(String name) {
    if (name != null) {
      name = norm(name);
      if (NON_NAMES.contains(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return non empty uppercased string with normalized whitespace and all non latin letters
   *     replaced. Or null
   */
  protected static String norm(String x) {
    if (x == null) {
      return null;
    }
    Pattern REMOVE_NON_LETTERS = Pattern.compile("[\\W\\d]+");
    x = REMOVE_NON_LETTERS.matcher(x).replaceAll(" ");
    x = StringUtils.normalizeSpace(x).toUpperCase();
    return StringUtils.trimToNull(x);
  }

  /**
   * @param file the synonym file on rs.gbif.org
   * @return
   */
  private Map<String, String> readSynonymUrl(Rank rank, String file) {
    URL url = RsGbifOrg.synonymUrl(file);
    LOG.info("Reading synonyms from " + url.toString());
    try (InputStream synIn = url.openStream()) {
      return IOUtils.streamToMap(synIn, 0, 1, true);
    } catch (IOException e) {
      LOG.warn("Cannot read synonym map from stream for {}. Use empty map instead.", rank, e);
    }
    return readSynonymStream(rank, file);
  }

  private Map<String, String> readSynonymStream(Rank rank, String filePath) {
    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream synIn = classLoader.getResourceAsStream(filePath)) {
      return IOUtils.streamToMap(synIn, 0, 1, true);
    } catch (IOException e) {
      LOG.warn("Cannot read synonym map from stream for {}. Use empty map instead.", rank, e);
    }
    return Map.of();
  }

  /** Reads blacklisted names from rs.gbif.org */
  private void readOnlineBlacklist() {
    try {
      URL url = RsGbifOrg.authorityUrl(RsGbifOrg.FILENAME_BLACKLIST);
      LOG.debug("Reading " + url.toString());
      readBlacklistStream(url.openStream());
    } catch (IOException e) {
      LOG.warn("Cannot read online blacklist.", e);
    }
  }

  /** Reads blacklisted names from stream */
  private void readBlacklistStream(InputStream in) {
    NON_NAMES.clear();
    try {
      NON_NAMES.addAll(IOUtils.streamToSet(in));
    } catch (IOException e) {
      LOG.warn("Cannot read blacklist. Use empty set instead.", e);
    } finally {
      Closeables.closeQuietly(in);
    }
    LOG.debug("loaded " + NON_NAMES.size() + " blacklisted names");
  }

  /**
   * Reads synonym dicts from given classpath root path. File names must be the same as on
   * rs.gbif.org.
   */
  public void loadClasspathDicts(String classpathFolder) throws IOException {
    LOG.info("Reloading dictionary files from classpath ...");

    ClassLoader classLoader = getClass().getClassLoader();

    for (Rank rank : SYNONYM_FILENAMES.keySet()) {
      String filePath = classpathFolder + "/" + SYNONYM_FILENAMES.get(rank);
      try (InputStream synIn = classLoader.getResourceAsStream(filePath)) {
        if (synIn != null) {
          Map<String, String> synonyms = readSynonymStream(rank, filePath);
          setSynonyms(rank, synonyms);
        } else {
          LOG.error("Unable to find resource: {}", filePath);
        }
      }
    }

    // read blacklisted names
    String blacklistFilePath = classpathFolder + "/" + RsGbifOrg.FILENAME_BLACKLIST;
    try (InputStream blackIn = classLoader.getResourceAsStream(blacklistFilePath)) {
      if (blackIn != null) {
        readBlacklistStream(blackIn);
      } else {
        LOG.error("Unable to find resource: {}", blacklistFilePath);
      }
    }
  }

  /** Reloads all synonym files found on rs.gbif.org replacing existing mappings. */
  public void loadOnlineDicts() {
    LOG.info("Reloading dictionary files from rs.gbif.org ...");

    for (Rank rank : SYNONYM_FILENAMES.keySet()) {
      Map<String, String> synonyms = readSynonymUrl(rank, SYNONYM_FILENAMES.get(rank));
      setSynonyms(rank, synonyms);
    }

    // read blacklisted names
    readOnlineBlacklist();
  }

  /**
   * Sets the synonym lookup map for a given rank. Names will be normalised and checked for
   * existance of the same entry as key or value.
   *
   * @param rank
   * @param synonyms
   */
  public void setSynonyms(Rank rank, Map<String, String> synonyms) {
    Map<String, String> synonymsNormed = new HashMap<>();

    // normalise keys
    for (Map.Entry<String, String> entry : synonyms.entrySet()) {
      synonymsNormed.put(norm(entry.getKey()), entry.getValue());
    }

    // test if synonyms show up as accepted too
    Collection<String> syns = new HashSet<>(synonymsNormed.keySet());
    for (String syn : syns) {
      if (synonymsNormed.containsKey(synonymsNormed.get(syn))) {
        LOG.warn(syn + " is both synonym and accepted - ignore synonym.");
        synonymsNormed.remove(syn);
      }
    }

    syn.put(rank, synonymsNormed);
    LOG.debug("Loaded " + synonyms.size() + " " + rank.name() + " synonyms ");

    // also insert kingdom enum lookup in case of kingdom synonyms
    if (Rank.KINGDOM == rank) {
      Map<String, String> map = syn.get(Rank.KINGDOM);
      if (map != null) {
        for (String syn : map.keySet()) {
          Kingdom k = null;
          String key = map.get(syn);
          if (key != null) {
            key = key.toLowerCase();
            key = StringUtils.capitalize(key);
            try {
              k = Kingdom.valueOf(key);
            } catch (Exception e) {
            }
          }
          this.kingdoms.put(norm(syn), k);
        }
      }
      for (Kingdom k : Kingdom.values()) {
        this.kingdoms.put(norm(k.name()), k);
      }
    }
  }

  /** @return the number of entries across all ranks */
  public int size() {
    int all = 0;
    for (Rank r : syn.keySet()) {
      all += size(r);
    }
    return all;
  }

  /** @return the number of entries for a given rank */
  public int size(Rank rank) {
    if (syn.containsKey(rank)) {
      return syn.get(rank).size();
    }
    return 0;
  }

  public Kingdom toKingdom(String kingdom) {
    if (kingdom == null) {
      return null;
    }
    return kingdoms.get(kingdom.trim().toUpperCase());
  }
}

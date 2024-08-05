package life.catalogue.matching.service;

import com.fasterxml.jackson.databind.SerializationFeature;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.index.NameNRank;
import life.catalogue.matching.model.*;
import life.catalogue.matching.similarity.ScientificNameSimilarity;
import life.catalogue.matching.similarity.StringSimilarity;
import life.catalogue.matching.util.CleanupUtils;
import life.catalogue.matching.util.Dictionaries;
import life.catalogue.matching.util.HigherTaxaComparator;
import life.catalogue.matching.util.NameParsers;

import org.gbif.nameparser.api.*;
import org.gbif.nameparser.util.RankUtils;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import lombok.extern.slf4j.Slf4j;

/**
 * Matching service that matches a scientific name to a name usage in the index.
 * This uses a DatasetIndex to query the index and match names.
 * This is higher level service that uses the DatasetIndex to match names.
 */
@Slf4j
@Service
public class MatchingService {

  @Value("${working.path:/tmp/}")
  protected String metadataFilePath;

  @Value("${online.dictionary.url:'https://rs.gbif.org/dictionaries/'}")
  protected String dictionariesUrl = "https://rs.gbif.org/dictionaries/";

  private static final int MIN_CONFIDENCE = 80;
  private static final int MIN_CONFIDENCE_FOR_HIGHER_MATCHES = 90;
  private static final int MIN_CONFIDENCE_ACROSS_RANKS = 1;
  private static final Set<Kingdom> VAGUE_KINGDOMS =
      Set.of(
          Kingdom.ARCHAEA,
          Kingdom.BACTERIA,
          Kingdom.FUNGI,
          Kingdom.CHROMISTA,
          Kingdom.PROTOZOA,
          Kingdom.INCERTAE_SEDIS);
  private static final List<Rank> DWC_RANKS_REVERSE =
      ImmutableList.copyOf(Lists.reverse(Rank.DWC_RANKS));
  private static final ConfidenceOrder CONFIDENCE_ORDER = new ConfidenceOrder();

  final HigherTaxaComparator htComp;
  private final StringSimilarity sim = new ScientificNameSimilarity();

  private static final Set<NameType> STRICT_MATCH_TYPES =
      Set.of(NameType.OTU, NameType.VIRUS, NameType.HYBRID_FORMULA);
  private static final List<Rank> HIGHER_QUERY_RANK =
      List.of(
          Rank.SPECIES, Rank.GENUS, Rank.FAMILY, Rank.ORDER, Rank.CLASS, Rank.PHYLUM, Rank.KINGDOM);
  // https://github.com/CatalogueOfLife/backend/issues/1314
  public static final Map<TaxonomicStatus, Integer> STATUS_SCORE =
      Map.of(
          TaxonomicStatus.ACCEPTED, 1,
          TaxonomicStatus.SYNONYM, 0,
          TaxonomicStatus.AMBIGUOUS_SYNONYM, -1,
          TaxonomicStatus.PROVISIONALLY_ACCEPTED, -5,
          TaxonomicStatus.MISAPPLIED, -10);

  private static final Pattern FIRST_WORD = Pattern.compile("^(.+?)\\b");
  private static final List<Rank> HIGHER_RANKS;

  static {
    List<Rank> ranks = Lists.newArrayList(Rank.LINNEAN_RANKS);
    ranks.remove(Rank.SPECIES);
    HIGHER_RANKS = ImmutableList.copyOf(ranks);
  }

  private final AuthorComparator authComp;

  private final static Pattern TAB_PAT = Pattern.compile("\t");

  private AuthorComparator createAuthorComparator() {
    Map<String, String> map = new HashMap<>();
    URL url = dictionaries.authorityUrl( "authormap.txt");
    try (
      InputStream inputStream = new BufferedInputStream(url.openStream());
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      log.debug("Loading author abbreviation map from {}", url);
      reader.lines()
        .map(TAB_PAT::split)
        .forEach(row -> {
          if (row.length >= 3) {
            map.put(row[0], row[2]);
            map.put(row[1], row[2]);
          } else {
            log.warn("Invalid row format: {}", Arrays.toString(row));
          }
        });
    } catch (IOException e) {
      log.error("Failed to load author abbreviation map from {}", "authormap.txt", e);
    } catch (Exception e) {
      log.error("Unexpected error while loading author abbreviation map from {}", "authormap.txt", e);
    }

    return new AuthorComparator(new AuthorshipNormalizer(map));
  }

  /**
   * The matching mode to use.
   */
  protected enum MatchingMode {
    /**
     * Fuzzy matching mode that allows for some minor differences in the scientific name.
     */
    FUZZY,
    /**
     * Strict matching mode that requires an exact match of the scientific name.
     */
    STRICT,
    /**
     * Match to higher ranks only.
     */
    HIGHER
  }

  DatasetIndex datasetIndex;
  Dictionaries dictionaries;

  public MatchingService(DatasetIndex datasetIndex, HigherTaxaComparator htComp, Dictionaries dictionaries) {
    this.datasetIndex = datasetIndex;
    this.htComp = htComp;
    this.dictionaries = dictionaries;
    this.authComp = createAuthorComparator();
  }

  /**
   * Get the metadata for the API and index.
   *
   * @return the metadata or empty if it could not be read or generated
   */
  public Optional<APIMetadata> getAPIMetadata() {

    // read JSON from file, if not available generate from datasetIndex
    if (!datasetIndex.getIsInitialised()) {
      return Optional.empty();
    }

    File metadata = new File(metadataFilePath + "/index-metadata.json");
    try {
      if (!metadata.exists() ) {
        APIMetadata metadata1 = datasetIndex.getAPIMetadata();
        //serialise to file
        ObjectMapper mapper = new ObjectMapper();
        FileWriter writer = new FileWriter(metadata);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(writer, metadata1);
        return Optional.of(metadata1);
      } else {
        // read from file
        ObjectMapper mapper = new ObjectMapper();
        return Optional.of(mapper.readValue(metadata, APIMetadata.class));
      }
    } catch (Exception e) {
      log.error("Failed to read index metadata from {}", metadata, e);
    }
    return Optional.empty();
  }

  private static boolean isMatch(@Nullable NameUsageMatch match) {
    return match !=null && MatchType.NONE != match.getDiagnostics().getMatchType();
  }

  private static NameUsageMatch higherMatch(NameUsageMatch match, NameUsageMatch firstMatch) {
    match.getDiagnostics().setMatchType(MatchType.HIGHERRANK);
    // FIXME
    addAlternatives(match, firstMatch.getDiagnostics().getAlternatives());
    return match;
  }

  /**
   * Adds the given alternatives to the alternatives existing in the match, making sure we dont get
   * infinite recursions my clearing all alternate matches on the arguments
   */
  private static void addAlternatives(NameUsageMatch match, List<NameUsageMatch> alts) {
    if (match.getDiagnostics().getAlternatives() != null && alts != null) {
      alts.addAll(match.getDiagnostics().getAlternatives());
    }
    setAlternatives(match, alts);
  }

  /**
   * Sets the alternative on a match making sure we dont get infinite recursions my clearing all
   * alternate matches on the arguments
   */
  private static void setAlternatives(NameUsageMatch match, List<NameUsageMatch> alts) {
    if (alts != null) {
      Set<String> keys =
          new HashSet<>(); // remember keys and make unique - we can have the same usages in here
      ListIterator<NameUsageMatch> iter = alts.listIterator();
      while (iter.hasNext()) {
        NameUsageMatch m = iter.next();
        if (m.getUsage() != null
                && match.getUsage() != null
                && Objects.equals(m.getUsage().getKey(), match.getUsage().getKey())
            || keys.contains(m.getUsage().getKey())) {
          // same usage, remove!
          iter.remove();
        } else if (m.getDiagnostics().getAlternatives() != null
          && !m.getDiagnostics().getAlternatives().isEmpty()) {
          m.getDiagnostics().setAlternatives(List.of());
        }
        keys.add(m.getUsage().getKey());
      }
    }
    match.getDiagnostics().setAlternatives(alts);
  }

  /**
   * Match an external ID to a name usage.
   * @param identifier the external ID to match
   * @return the list of matches
   */
  public List<ExternalID> matchID(String identifier){
    return datasetIndex.lookupIdentifier(identifier);
  }

  public List<ExternalID> lookupJoins(String identifier){
    return datasetIndex.lookupJoins(identifier);
  }

  /**
   * Match an external ID to a name usage using .
   * @param datasetID the dataset key
   * @param identifier the external ID to match
   * @return the list of matches
   */
  public List<ExternalID> matchID(String datasetID, String identifier){
    return datasetIndex.lookupIdentifier(datasetID, identifier);
  }

  public NameUsageMatch match(
      @Nullable String scientificName,
      @Nullable LinneanClassification classification,
      boolean strict) {
    return match(
        null, null, null, null, scientificName, null, null, null, null, null, classification, null, strict, false);
  }

  public NameUsageMatch match(
      @Nullable String scientificName,
      @Nullable Rank rank,
      @Nullable LinneanClassification classification,
      boolean strict) {
    return match(
        null, null, null, null, scientificName, null, null, null, null, rank, classification, null, strict, false);
  }

  public NameUsageMatch match(
    @Nullable String usageKey,
    @Nullable String taxonID,
    @Nullable String taxonConceptID,
    @Nullable String scientificNameID,
    @Nullable String scientificName,
    @Nullable String authorship,
    @Nullable String genericName,
    @Nullable String specificEpithet,
    @Nullable String infraSpecificEpithet,
    @Nullable Rank rank,
    @Nullable LinneanClassification classification,
    Set<String> exclude,
    boolean strict,
    boolean verbose) {

    StopWatch watch = new StopWatch();
    watch.start();

    // When provided a usageKey is used exclusively
    if (StringUtils.isNotBlank(usageKey)) {
      NameUsageMatch match = datasetIndex.matchByUsageKey(usageKey);
      match
        .getDiagnostics()
        .setNote("All provided names were ignored since the usageKey was provided");
      watch.stop();
      match.getDiagnostics().setTimeTaken(watch.getTime());
      log.debug(
        "{} Match of usageKey[{}] in {}", match.getDiagnostics().getMatchType(), usageKey, watch);
      return match;
    }

    // match by scientific name + classification
    NameUsageMatch sciNameMatch = matchByClassification(scientificName,
      authorship, genericName, specificEpithet,
      infraSpecificEpithet, rank, classification,
      exclude, strict, verbose);

    if (isMatch(sciNameMatch)) {
      log.debug(
        "{} Match of {} >{}< to {} [{}] in {}",
        sciNameMatch.getDiagnostics().getMatchType(),
        rank,
        scientificName,
        sciNameMatch.getUsage().getKey(),
        sciNameMatch.getUsage().getName(),
        watch);
    }

    // Match with taxonID
    if (StringUtils.isNotBlank(taxonID)) {
      NameUsageMatch idMatch = datasetIndex.matchByExternalKey(
        taxonID,
        Issue.TAXON_ID_NOT_FOUND,
        Issue.TAXON_MATCH_TAXON_ID_IGNORED
        );
      log.debug(
        "{} Match of taxonConceptID[{}] in {}", idMatch.getDiagnostics().getMatchType(), taxonConceptID, watch);

      if (isMatch(idMatch)) {
          checkScientificNameAndIDConsistency(idMatch, scientificName, rank);
          checkConsistencyWithClassificationMatch(idMatch, sciNameMatch);
          watch.stop();
          idMatch.getDiagnostics().setTimeTaken(watch.getTime());
          return idMatch;
      } else {
        sciNameMatch.addMatchIssue(Issue.TAXON_ID_NOT_FOUND);
      }
    }

    // Match with taxonConceptID
    if (StringUtils.isNotBlank(taxonConceptID)) {
      NameUsageMatch idMatch = datasetIndex.matchByExternalKey(
        taxonConceptID, Issue.TAXON_CONCEPT_ID_NOT_FOUND, Issue.TAXON_MATCH_TAXON_CONCEPT_ID_IGNORED);
      log.debug(
        "{} Match of taxonConceptID[{}] in {}", idMatch.getDiagnostics().getMatchType(), taxonConceptID, watch);
      if (isMatch(idMatch)){
        checkScientificNameAndIDConsistency(idMatch, scientificName, rank);
        checkConsistencyWithClassificationMatch(idMatch, sciNameMatch);
        watch.stop();
        idMatch.getDiagnostics().setTimeTaken(watch.getTime());
        return idMatch;
      } else {
        sciNameMatch.addMatchIssue(Issue.TAXON_CONCEPT_ID_NOT_FOUND);
      }
    }

    // Match with scientificNameID
    if (StringUtils.isNotBlank(scientificNameID)) {
      NameUsageMatch idMatch = datasetIndex.matchByExternalKey(scientificNameID,
        Issue.SCIENTIFIC_NAME_ID_NOT_FOUND, Issue.TAXON_MATCH_SCIENTIFIC_NAME_ID_IGNORED);
      log.debug(
        "{} Match of scientificNameID[{}] in {}", idMatch.getDiagnostics().getMatchType(), scientificNameID, watch);
      if (isMatch(idMatch)) {
        checkScientificNameAndIDConsistency(idMatch, scientificName, rank);
        checkConsistencyWithClassificationMatch(idMatch, sciNameMatch);
        watch.stop();
        idMatch.getDiagnostics().setTimeTaken(watch.getTime());
        return idMatch;
      } else {
        sciNameMatch.addMatchIssue(Issue.SCIENTIFIC_NAME_ID_NOT_FOUND);
      }
    }

    watch.stop();
    sciNameMatch.getDiagnostics().setTimeTaken(watch.getTime());
    return sciNameMatch;
  }

  /**
   * Check if the taxonID match is consistent with the scientific name match.
   * If not, add an issue to the diagnostics.
   * @param idMatch
   * @param sciNameMatch
   */
  private void checkConsistencyWithClassificationMatch(NameUsageMatch idMatch, NameUsageMatch sciNameMatch) {
    if (isMatch(idMatch) && isMatch(sciNameMatch)) {
      if (!Objects.equals(idMatch.getUsage().getKey(), sciNameMatch.getUsage().getKey())) {
        log.warn("Inconsistent match for taxonID[{}]: {} vs {}",
          idMatch.getUsage().getKey(), idMatch.getUsage().getCanonicalName(), sciNameMatch.getUsage().getCanonicalName());
        idMatch.addMatchIssue(Issue.TAXON_MATCH_NAME_AND_ID_AMBIGUOUS);
      }
    }
  }

  /**
   * Check if the scientific name provided in the match is consistent with the scientific name provided in the request.
   * If not, add an issue to the diagnostics.
   * @param idMatch
   * @param scientificName
   * @param rank
   */
  private void checkScientificNameAndIDConsistency(@Nullable NameUsageMatch idMatch, @Nullable String scientificName, @Nullable Rank rank) {

    if (idMatch != null && scientificName != null) {
      try {
        ParsedName name = NameParsers.INSTANCE.parse(scientificName, rank, null);
        String canonicalName = name.canonicalNameMinimal();
        if (!idMatch.getUsage().getCanonicalName().equalsIgnoreCase(canonicalName)) {
          log.warn("Inconsistent scientific name for taxonID[{}]: {} vs {}",
            idMatch.getUsage().getKey(), idMatch.getUsage().getCanonicalName(), scientificName);
          idMatch.addMatchIssue(Issue.SCIENTIFIC_NAME_AND_ID_INCONSISTENT);
        }
      } catch (Exception e){
        log.warn("Failed to parse scientific name in consistency check {}", scientificName, e);
      }
    }
  }

  private NameUsageMatch matchByClassification(@Nullable String suppliedScientificName,
                                               @Nullable String authorship,
                                               @Nullable String genericName,
                                               @Nullable String specificEpithet,
                                               @Nullable String infraSpecificEpithet,
                                               @Nullable Rank suppliedRank,
                                               @Nullable LinneanClassification classification,
                                               Set<String> exclude, boolean strict, boolean verbose) {

    // construct the best name and rank we can with the supplied values
    NameNRank nr =
      NameNRank.build(
        suppliedScientificName,
        authorship,
        genericName,
        specificEpithet,
        infraSpecificEpithet,
        suppliedRank,
        classification);

    String scientificName = nr.name;
    Rank rank = nr.rank;
    ParsedName parsedName = null;
    NameType queryNameType;
    MatchingMode mainMatchingMode = strict ? MatchingMode.STRICT : MatchingMode.FUZZY;

    // clean strings, replacing odd whitespace, iso controls and trimming
    scientificName = CleanupUtils.clean(scientificName);
    if (classification == null) {
      classification = new Classification();
    } else {
      cleanClassification(classification);
    }

    // treat names that are all upper or lower case special - they cannot be parsed properly so
    // rather use them as they are!
    if (scientificName != null
        && (scientificName.toLowerCase().equals(scientificName)
            || scientificName.toUpperCase().equals(scientificName))) {
      log.debug("All upper or lower case name found. Don't try to parse: {}", scientificName);
      queryNameType = null;
      if (mainMatchingMode != MatchingMode.STRICT) {
        // turn off fuzzy matching
        mainMatchingMode = MatchingMode.STRICT;
      }
      if (rank == null) {
        rank = Rank.UNRANKED;
      }

    } else {
      try {
        // use name parser to make the name a canonical one
        // we build the name with flags manually as we wanna exclude indet. names such as "Abies
        // spec." and rather match them to Abies only
        Rank npRank = rank == null ? null : Rank.valueOf(rank.name());
        parsedName = NameParsers.INSTANCE.parse(scientificName, npRank, null);
        queryNameType = NameType.valueOf(parsedName.getType().name());
        scientificName = parsedName.canonicalNameMinimal();

        // parsed genus provided for a name lower than genus?
        if (classification.getGenus() == null
            && getGenusOrAbove(parsedName) != null
            && parsedName.getRank() != null
            && parsedName.getRank().isInfragenericStrictly()) {
          classification.setGenus(getGenusOrAbove(parsedName));
        }

        // used parsed rank if not given explicitly, but only for bi+trinomials
        // see https://github.com/CatalogueOfLife/backend/issues/1316
        if (rank == null) {
          if (parsedName.isBinomial()
              || parsedName.isTrinomial()
              || (parsedName.getRank() != null && parsedName.getRank().ordinal() >= Rank.SPECIES.ordinal())) {
            rank = Rank.valueOf(parsedName.getRank().name());
          }
        }

        // hybrid names, virus names, OTU & blacklisted ones don't provide any parsed name
        if (mainMatchingMode != MatchingMode.STRICT && !parsedName.getType().isParsable()) {
          // turn off fuzzy matching
          mainMatchingMode = MatchingMode.STRICT;
          log.debug(
              "Unparsable {} name, turn off fuzzy matching for {}", parsedName.getType(), scientificName);
        }

      } catch (UnparsableNameException e) {
        // hybrid names, virus names & blacklisted ones - dont provide any parsed name
        queryNameType = NameType.valueOf(e.getType().name());
        // we assign all OTUs unranked
        if (NameType.OTU == queryNameType) {
          rank = Rank.UNRANKED;
        }
        if (mainMatchingMode != MatchingMode.STRICT) {
          // turn off fuzzy matching
          mainMatchingMode = MatchingMode.STRICT;
          log.debug(
              "Unparsable {} name, turn off fuzzy matching for {}", queryNameType, scientificName);
        } else {
          log.debug("Unparsable {} name: {}", queryNameType, scientificName);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    // run the initial match
    NameUsageMatch match1 =
        match(
            queryNameType,
            parsedName,
            scientificName,
            rank,
            classification,
            exclude,
            mainMatchingMode,
            verbose);

    // use genus higher match instead of fuzzy one?
    // https://github.com/gbif/portal-feedback/issues/2930
    if (match1.getDiagnostics().getMatchType() == MatchType.VARIANT
        && match1.getUsage().getRank() != null
        && match1.getUsage().getRank().isSpeciesOrBelow()
        && parsedName != null
        && !match1.getUsage().getCanonicalName().startsWith(getGenusOrAbove(parsedName) + " ")
        && nextAboveGenusDiffers(classification, match1)) {
      NameUsageMatch genusMatch =
          match(
              NameType.valueOf(parsedName.getType().name()),
              null,
              getGenusOrAbove(parsedName),
              Rank.GENUS,
              classification,
              exclude,
              MatchingMode.HIGHER,
              verbose);
      if (isMatch(genusMatch) && genusMatch.getUsage().getRank() == Rank.GENUS) {
        return higherMatch(genusMatch, match1);
      }
    }

    // for strict matching do not try higher ranks
    if (isMatch(match1) || strict) {
      return match1;
    }

    // try to MATCH TO HIGHER RANKS if we can
    // include species or genus only matches from parsed name?
    NameUsageMatch match;
    boolean supraGenericOnly = false;
    if (parsedName != null && getGenusOrAbove(parsedName) != null) {
      if (parsedName.getSpecificEpithet() != null || (rank != null && rank.isInfrageneric())) {
        if (parsedName.getInfraspecificEpithet() != null || (rank != null && rank.isInfraspecific())) {
          // try with species
          String species = parsedName.getGenus() + " " + parsedName.getSpecificEpithet();
          match =
              match(
                  parsedName.getType(),
                  null,
                  species,
                  Rank.SPECIES,
                  classification,
                  exclude,
                  MatchingMode.FUZZY,
                  verbose);
          if (isMatch(match)) {
            return higherMatch(match, match1);
          }
        }

        // try with genus
        // we're not sure if this is really a genus, so don't set the rank
        // we get non species names sometimes like "Chaetognatha eyecount" that refer to a phylum
        // called "Chaetognatha"
        match =
            match(
                parsedName.getType(),
                null,
                getGenusOrAbove(parsedName),
                null,
                classification,
                exclude,
                MatchingMode.HIGHER,
                verbose);
        if (isMatch(match)) {
          return higherMatch(match, match1);
        }
        supraGenericOnly = true;
      }
    }

    // use classification query strings
    for (Rank qr : HIGHER_QUERY_RANK) {
      if (supraGenericOnly && !qr.isSuprageneric()) continue;
      String name = classification.getHigherRank(qr);
      if (!StringUtils.isEmpty(name)) {
        match =
            match(
                (NameType) null,
                null,
                name,
                qr,
                classification,
                exclude,
                MatchingMode.HIGHER,
                verbose);
        if (isMatch(match)) {
          return higherMatch(match, match1);
        }
      }
    }

    // if finally we cant find anything, return empty match object - but not null!
    log.debug("No match for name {}", scientificName);
    return noMatch(
        100,
        match1.getDiagnostics().getIssues(),
        match1.getDiagnostics().getNote(),
        verbose ? match1.getDiagnostics().getAlternatives() : null);
  }

  private boolean nextAboveGenusDiffers(LinneanClassification cl, NameUsageMatch cl2) {
    for (Rank r = RankUtils.nextHigherLinneanRank(Rank.GENUS);
         r != null;
         r = RankUtils.nextHigherLinneanRank(r)) {
      String h1 = cl.getHigherRank(r);
      String h2 = getHigherRank(cl2, r);
      if (h1 != null && h2 != null) {
        return !Objects.equals(h1, h2);
      }
    }
    return false;
  }

  static String getHigherRank(NameUsageMatch match, Rank rank) {
    if (rank != null) {
      return nameForRank(match, rank);
    }
    return null;
  }

  public static String nameForRank(NameUsageMatch match, Rank rank) {
    return match.getClassification().stream()
        .filter(c -> c.getRank().equals(rank))
        .findFirst()
        .map(c -> c.getName())
        .orElse(null);
  }

  private void cleanClassification(LinneanClassification cl) {
    for (Rank rank : HIGHER_RANKS) {
      if (cl.getHigherRank(rank) != null) {
        String val = CleanupUtils.clean(cl.getHigherRank(rank));
        if (val != null) {
          Matcher m = FIRST_WORD.matcher(val);
          if (m.find()) {
            cl.setHigherRank(m.group(1), rank);
          }
        }
      }
    }
  }

  public String getGenusOrAbove(ParsedName parsedName) {
    if (parsedName.getGenus() != null) {
      return parsedName.getGenus();
    }
    return parsedName.getUninomial();
  }

  private List<NameUsageMatch> queryIndex(Rank rank, String canonicalName, boolean fuzzy) {
    List<NameUsageMatch> matches = datasetIndex.matchByName(canonicalName, fuzzy, 50);
    // flag aggregate matches, see https://github.com/gbif/portal-feedback/issues/2935
    final int before = matches.size();
    matches.removeIf(
        m -> {
          if (m.getDiagnostics().getMatchType() == MatchType.EXACT
              && rank == Rank.SPECIES_AGGREGATE
              && m.getUsage().getRank() != Rank.SPECIES_AGGREGATE) {
            log.info(
                "Species aggregate match found for {} {}. Ignore and prefer higher matches",
                m.getUsage().getRank(),
                m.getUsage().getName());
            return true;
          }
          return false;
        });
    // did we remove matches because of aggregates? Then also remove any fuzzy matches
    if (matches.size() < before) {
      matches.removeIf(
          m -> {
            if (m.getDiagnostics().getMatchType() == MatchType.VARIANT) {
              log.info(
                  "Species aggregate match found for {}. Ignore also fuzzy match {} {}",
                  canonicalName,
                  m.getUsage().getRank(),
                  m.getUsage().getName());
              return true;
            }
            return false;
          });
    }
    return matches;
  }

  private List<NameUsageMatch> queryFuzzy(
      @Nullable NameType queryNameType,
      ParsedName pn,
      String canonicalName,
      Rank rank,
      LinneanClassification lc,
      boolean verbose) {
    // do a lucene matching
    List<NameUsageMatch> matches = queryIndex(rank, canonicalName, true);
    for (NameUsageMatch m : matches) {
      // 0 - +120
      final int nameSimilarity = nameSimilarity(queryNameType, canonicalName, m);
      // -36 - +40
      final int authorSimilarity = incNegScore(authorSimilarity(pn, m) * 2, 2);
      // -50 - +50
      final int classificationSimilarity = classificationSimilarity(lc, m);
      // -10 - +5
      final int rankSimilarity = rankSimilarity(rank, m.getUsage().getRank());
      // -5 - +1
      final int statusScore = STATUS_SCORE.get(m.getDiagnostics().getStatus());
      // -25 - 0
      final int fuzzyMatchUnlikely = fuzzyMatchUnlikelyhood(canonicalName, m);

      // preliminary total score, -5 - 20 distance to next best match coming below!
      m.getDiagnostics()
          .setConfidence(
              nameSimilarity
                  + authorSimilarity
                  + classificationSimilarity
                  + rankSimilarity
                  + statusScore
                  + fuzzyMatchUnlikely);

      if (verbose) {
        addNote(m, "Similarity: name=" + nameSimilarity);
        addNote(m, "authorship=" + authorSimilarity);
        addNote(m, "classification=" + classificationSimilarity);
        addNote(m, "rank=" + rankSimilarity);
        addNote(m, "status=" + statusScore);
        if (fuzzyMatchUnlikely < 0) {
          addNote(m, "fuzzy match unlikely=" + fuzzyMatchUnlikely);
        }
      }
    }

    return matches;
  }

  private List<NameUsageMatch> queryHigher(
      String canonicalName, Rank rank, LinneanClassification lc, boolean verbose) {
    // do a lucene matching
    List<NameUsageMatch> matches = queryIndex(rank, canonicalName, false);
    for (NameUsageMatch m : matches) {
      // 0 - +100
      final int nameSimilarity = nameSimilarity(null, canonicalName, m);
      // -50 - +50
      final int classificationSimilarity = classificationSimilarity(lc, m);
      // -10 - +5
      final int rankSimilarity = rankSimilarity(rank, m.getUsage().getRank()) * 2;
      // -5 - +1
      final int statusScore = STATUS_SCORE.get(m.getDiagnostics().getStatus());

      // preliminary total score, -5 - 20 distance to next best match coming below!
      m.getDiagnostics()
          .setConfidence(nameSimilarity + classificationSimilarity + rankSimilarity + statusScore);

      if (verbose) {
        addNote(m, "Similarity: name=" + nameSimilarity);
        addNote(m, "classification=" + classificationSimilarity);
        addNote(m, "rank=" + rankSimilarity);
        addNote(m, "status=" + statusScore);
      }
    }

    return matches;
  }

  private List<NameUsageMatch> queryStrict(
      @Nullable NameType queryNameType,
      ParsedName pn,
      String canonicalName,
      Rank rank,
      LinneanClassification lc,
      boolean verbose) {
    // do a lucene matching
    List<NameUsageMatch> matches = queryIndex(rank, canonicalName, false);
    for (NameUsageMatch m : matches) {
      // 0 - +120
      final int nameSimilarity = nameSimilarity(queryNameType, canonicalName, m);
      // -28 - +40
      final int authorSimilarity = incNegScore(authorSimilarity(pn, m) * 4, 8);
      // -50 - +50
      final int kingdomSimilarity =
          incNegScore(
              kingdomSimilarity(
                  htComp.toKingdom(lc.getKingdom()), htComp.toKingdom(m.getKingdom())),
              10);
      // -10 - +5
      final int rankSimilarity = incNegScore(rankSimilarity(rank, m.getUsage().getRank()), 10);
      // -5 - +1
      final int statusScore = STATUS_SCORE.get(m.getDiagnostics().getStatus());

      // preliminary total score, -5 - 20 distance to next best match coming below!
      m.getDiagnostics()
          .setConfidence(
              nameSimilarity + authorSimilarity + kingdomSimilarity + rankSimilarity + statusScore);

      if (verbose) {
        addNote(m, "Similarity: name=" + nameSimilarity);
        addNote(m, "authorship=" + authorSimilarity);
        addNote(m, "kingdom=" + kingdomSimilarity);
        addNote(m, "rank=" + rankSimilarity);
        addNote(m, "status=" + statusScore);
      }
    }

    return matches;
  }

  private int incNegScore(int score, int factor) {
    return score < 0 ? score * factor : score;
  }

  /**
   * Use our custom similarity algorithm and compare the higher classifications to select the best
   * match
   *
   * @param queryNameType the name type to match against
   * @param pn the parsed name to match against
   * @param canonicalName the canonical name to match against
   * @param rank the rank to match against
   * @param lc the classification to match against
   * @param exclude the list of keys to exclude
   * @param mode the matching mode to use
   * @param verbose if true, add notes to the match object
   * @return the best match, might contain no usageKey
   */
  @VisibleForTesting
  protected NameUsageMatch match(
      @Nullable NameType queryNameType,
      @Nullable ParsedName pn,
      @Nullable String canonicalName,
      Rank rank,
      LinneanClassification lc,
      Set<String> exclude,
      @NotNull final MatchingMode mode,
      final boolean verbose) {

    if (Strings.isNullOrEmpty(canonicalName)) {
      return noMatch(100, ProcessFlag.NO_NAME_SUPPLIED, "No name given", null);
    }

    // do the matching
    List<NameUsageMatch> matches = null;
    switch (mode) {
      case FUZZY:
        matches = queryFuzzy(queryNameType, pn, canonicalName, rank, lc, verbose);
        break;
      case STRICT:
        matches = queryStrict(queryNameType, pn, canonicalName, rank, lc, verbose);
        break;
      case HIGHER:
        matches = queryHigher(canonicalName, rank, lc, verbose);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + mode);
    }

    // exclude any matches against the explicit exclusion list
    if (exclude != null && !exclude.isEmpty()) {
      for (NameUsageMatch m : matches) {
        if (exclude.contains(m.getUsage().getKey())) {
          m.getDiagnostics().setConfidence(0);
          addNote(m, "excluded by " + m.getUsage().getKey());
        } else {
          for (Rank r : Rank.DWC_RANKS) {
            if (exclude.contains(m.getHigherRankKey(r))) {
              m.getDiagnostics().setConfidence(0);
              addNote(m, "excluded by " + m.getHigherRankKey(r));
              break;
            }
          }
        }
      }
    }

    // order by confidence
    Collections.sort(matches, CONFIDENCE_ORDER);

    // having the pre-normalized confidence is necessary to understand usage selection in some cases
    if (verbose) {
      for (NameUsageMatch match : matches) {
        addNote(match, "score=" + match.getDiagnostics().getConfidence());
      }
    }

    if (!matches.isEmpty()) {
      // add 0 - 5 confidence based on distance to next best match
      NameUsageMatch best = matches.get(0);
      int bestConfidence = best.getDiagnostics().getConfidence();
      int nextMatchDistance;

      if (matches.size() == 1) {
        // boost results with a single match to pick from
        nextMatchDistance = 5;
        if (verbose) {
          addNote(best, "singleMatch=" + nextMatchDistance);
        }

      } else {
        // we have more than one match to choose from
        int secondBestConfidence = matches.get(1).getDiagnostics().getConfidence();

        // Do our results fall within the confidence score range AND differ across classes?
        boolean ambiguousAcrossClasses =
            similarButSpanRank(matches, MIN_CONFIDENCE_ACROSS_RANKS, Rank.CLASS);

        if (bestConfidence == secondBestConfidence || ambiguousAcrossClasses) {
          // similarly good matches, happens when there are homonyms in the nub as synonyms only

          // If we have similar results spanning classes, compare them all
          int threshold = ambiguousAcrossClasses ? MIN_CONFIDENCE_ACROSS_RANKS : 0;
          List<NameUsageMatch> suitableMatches = extractMatchesOfInterest(matches, threshold);
          boolean sameClassification = true;
          for (NameUsageMatch m : suitableMatches) {
            if (!equalClassification(best, m)) {
              sameClassification = false;
              break;
            }
          }
          if (sameClassification) {
            // if they both have the same classification pick the one with the lowest, hence oldest
            // id!
            // FIXME keys are no longer numeric
            //            Collections.sort(suitableMatches, USAGE_KEY_ORDER);
            best = suitableMatches.get(0);
            addNote(best, suitableMatches.size() + " synonym homonyms");
          } else {
            best = matchLowestDenominator(canonicalName, suitableMatches);
            if (!isMatch(best)) {
              return noMatch(
                  99,
                ProcessFlag.MULTIPLE_MATCHES_SAME_CONFIDENCE,
                  "Multiple equal matches for " + canonicalName,
                  verbose ? matches : null);
            }
          }
        }

        // boost up to 5 based on distance to next match
        nextMatchDistance = Math.min(5, (bestConfidence - secondBestConfidence) / 2);
        if (verbose) {
          addNote(best, "nextMatch=" + nextMatchDistance);
        }
      }
      // normalize confidence values into the range of 0 to 100
      best.getDiagnostics().setConfidence(normConfidence(bestConfidence + nextMatchDistance));

      // finally check if match is good enough
      if (best.getDiagnostics().getConfidence()
          < (mode == MatchingMode.HIGHER ? MIN_CONFIDENCE_FOR_HIGHER_MATCHES : MIN_CONFIDENCE)) {
        return noMatch(
            99,
          ProcessFlag.LOW_CONFIDENCE,
            "No match because of too little confidence",
            verbose ? matches : null);
      }

      // verbose and alternatives?
      if (verbose && matches.size() > 1) {
        // remove best match
        matches.remove(best);
        setAlternatives(best, matches);
        for (NameUsageMatch alt : matches) {
          alt.getDiagnostics().setConfidence(normConfidence(alt.getDiagnostics().getConfidence()));
        }
      }

      return best;
    }

    return noMatch(100, ProcessFlag.NO_MATCH, null, null);
  }

  /**
   * Returns true when the preferred match has a classification that differs to the other matches
   * within the confidence threshold, when compared to the stated rank.
   * @param matches the list of matches to compare
   * @param confidenceThreshold the threshold to consider a match as similar
   * @param rank the rank to compare the classification to
   */
  @VisibleForTesting
  public boolean similarButSpanRank(List<NameUsageMatch> matches, int confidenceThreshold, Rank rank) {
    boolean similarButSpanRanks = false;
    if (matches.size() > 1) {
      NameUsageMatch best = matches.get(0);
      for (int i = 1; i < matches.size(); i++) {
        NameUsageMatch curr = matches.get(i);

        if (best.getDiagnostics().getConfidence() - curr.getDiagnostics().getConfidence()
            <= confidenceThreshold) {
          if (!equalClassification(best, curr, rank)) {
            similarButSpanRanks =
                true; // within confidence threshold but higher classifications differ
            break;
          }
        } else {
          break; // we're past the confidence threshold
        }
      }
    }
    return similarButSpanRanks;
  }

  /** Tries to match to the lowest common higher rank from all best equal matches */
  private NameUsageMatch matchLowestDenominator(
      String canonicalName, List<NameUsageMatch> matches) {
    for (Rank r : DWC_RANKS_REVERSE) {
      String higherKey = matches.get(0).getHigherRankKey(r);
      if (higherKey == null) continue;

      for (NameUsageMatch m : matches) {
        if (!Objects.equals(higherKey, m.getHigherRankKey(r))) {
          higherKey = null;
          break;
        }
      }
      // did all equal matches have the same higherKey?
      if (higherKey != null) {
        // NPE safetly first - maybe the key is missing in the index
        NameUsageMatch match = datasetIndex.matchByUsageKey(higherKey);
        if (match != null) {
          match.getDiagnostics().setMatchType(MatchType.HIGHERRANK);
          return match;
        }
      }
    }
    return noMatch(
        99,
      ProcessFlag.NO_LOWEST_DENOMINATOR,
        "No lowest denominator in equal matches for " + canonicalName,
        null);
  }

  // -12 to 8
  private int authorSimilarity(@Nullable ParsedName pn, NameUsageMatch m) {
    int similarity = 0;
    if (pn != null) {
      try {
        ParsedName mpn =
            NameParsers.INSTANCE.parse(m.getUsage().getName(), m.getUsage().getRank(), null);
        // authorship comparison was requested!
        Equality recomb =
            authComp.compareAuthorsFirst(
                pn.getCombinationAuthorship(), mpn.getCombinationAuthorship());
        Equality bracket =
            authComp.compareAuthorsFirst(pn.getBasionymAuthorship(), mpn.getBasionymAuthorship());
        if (bracket == Equality.UNKNOWN) {
          // we don't have 2 bracket authors to compare. Try with combination authors as brackets
          // are sometimes forgotten or wrong
          if (pn.getBasionymAuthorship() != null) {
            bracket = authComp.compare(pn.getBasionymAuthorship(), mpn.getCombinationAuthorship());
          } else if (mpn.getBasionymAuthorship() != null) {
            bracket = authComp.compare(pn.getCombinationAuthorship(), mpn.getBasionymAuthorship());
          }
          if (bracket == Equality.EQUAL) {
            similarity -= 1;
          } else if (bracket == Equality.DIFFERENT) {
            similarity += 1;
          }
        }

        similarity += equality2Similarity(recomb, 3);
        similarity += equality2Similarity(bracket, 1);

      } catch (UnparsableNameException e) {
        if (e.getType().isParsable()) {
          log.warn("Failed to parse name: {}", m.getUsage().getName());
        }
      } catch (Exception e) {
        log.error("Error comparing authorship", e);
      }
    }

    return similarity;
  }

  private int equality2Similarity(Equality eq, int factor) {
    switch (eq) {
      case EQUAL:
        return 2 * factor;
      case DIFFERENT:
        return -3 * factor;
    }
    return 0;
  }

  private boolean equalClassification(LinneanClassification best, LinneanClassification m) {
    return equalClassification(best, m, null);
  }

  /** Compares classifications starting from kingdom stopping after the stopRank if provided. */
  private boolean equalClassification(
      LinneanClassification best, LinneanClassification m, Rank stopRank) {
    for (Rank r : Rank.LINNEAN_RANKS) {
      if (stopRank != null && stopRank.higherThan(r)) {
        break;

      } else if (best.getHigherRank(r) == null) {
        if (m.getHigherRank(r) != null) {
          return false;
        }

      } else {
        if (m.getHigherRank(r) == null || !best.getHigherRank(r).equals(m.getHigherRank(r))) {
          return false;
        }
      }
    }
    return true;
  }

  /** Returns all matches that are within the given threshold of the best. */
  private List<NameUsageMatch> extractMatchesOfInterest(
      List<NameUsageMatch> matches, int threshold) {
    List<NameUsageMatch> target = new ArrayList<>();
    if (!matches.isEmpty()) {
      final int conf = matches.get(0).getDiagnostics().getConfidence();
      for (NameUsageMatch m : matches) {
        if (conf - m.getDiagnostics().getConfidence() <= threshold) {
          target.add(m);
        } else {
          // matches are sorted by confidence!
          break;
        }
      }
    }
    return target;
  }

  private static void addNote(NameUsageMatch m, String note) {
    if (m.getDiagnostics().getNote() == null) {
      m.getDiagnostics().setNote(note);
    } else {
      m.getDiagnostics().setNote(m.getDiagnostics().getNote() + "; " + note);
    }
  }

  private static NameUsageMatch noMatch(
      int confidence,
      @NotNull ProcessFlag issue,
      String note,
      List<NameUsageMatch> alternatives) {

    return NameUsageMatch.builder()
        .diagnostics(
            NameUsageMatch.Diagnostics.builder()
                .matchType(MatchType.NONE)
                .confidence(confidence)
                .issues(new ArrayList<>())
                .processingFlags(new ArrayList<>(List.of(issue)))
                .note(note)
                .alternatives(alternatives)
                .build())
        .build();
  }

  private static NameUsageMatch noMatch(
    int confidence,
    @NotNull List<Issue> issues,
    String note,
    List<NameUsageMatch> alternatives) {
    return NameUsageMatch.builder()
      .diagnostics(
        NameUsageMatch.Diagnostics.builder()
          .matchType(MatchType.NONE)
          .confidence(confidence)
          .issues(issues)
          .note(note)
          .alternatives(alternatives)
          .build())
      .build();
  }

  private int fuzzyMatchUnlikelyhood(String canonicalName, NameUsageMatch m) {
    // ignore fuzzy matches with a terminal epithet of "indet" meaning usually indeterminate
    if (m.getDiagnostics().getMatchType() == MatchType.VARIANT
        && m.getUsage().getRank().isSpeciesOrBelow()
        && canonicalName.endsWith(" indet")) {
      return -25;
    }
    return 0;
  }

  private int nameSimilarity(
      @Nullable NameType queryNameType, String canonicalName, NameUsageMatch m) {
    // calculate name distance
    int confidence;
    if (canonicalName.equalsIgnoreCase(m.getUsage().getCanonicalName())) {
      // straight match
      confidence = 100;
      // binomial straight match? That is pretty trustworthy
      if (queryNameType != null && STRICT_MATCH_TYPES.contains(queryNameType)) {
        confidence += 20;
      } else if (canonicalName.contains(" ")) {
        confidence += 10;
      }

    } else {
      // fuzzy - be careful!
      confidence = (int) sim.getSimilarity(canonicalName, m.getUsage().getCanonicalName()) - 5;
      // fuzzy OTU match? That is dangerous, often one character/number means sth entirely different
      if (queryNameType == NameType.OTU) {
        confidence -= 50;
      }

      // modify confidence according to genus comparison in bionomials.
      // slightly trust binomials with a matching genus more, and trust less if we matched a
      // different genus name
      int spaceIdx = m.getUsage().getCanonicalName().indexOf(" ");
      if (spaceIdx > 0) {
        String genus = m.getUsage().getName().substring(0, spaceIdx);
        if (canonicalName.startsWith(genus)) {
          confidence += 5;
        } else {
          confidence -= 10;
        }
      }
    }
    return confidence;
  }

  @VisibleForTesting
  protected int classificationSimilarity(
      LinneanClassification query, LinneanClassification reference) {
    // kingdom is super important
    int rate = htComp.compareHigherRank(Rank.KINGDOM, query, reference, 5, -10, -1);
    if (rate == -10) {
      // plant and animal kingdoms are better delimited than Chromista, Fungi, etc. , so punish
      // those mismatches higher
      if (htComp.isInKingdoms(query, Kingdom.ANIMALIA, Kingdom.PLANTAE)
          && htComp.isInKingdoms(reference, Kingdom.ANIMALIA, Kingdom.PLANTAE)) {
        rate = -51;
        // plant and animal kingdoms should not be confused with Bacteria, Archaea or Viruses
      } else if (htComp.isInKingdoms(query, Kingdom.ANIMALIA, Kingdom.PLANTAE)
          && htComp.isInKingdoms(reference, Kingdom.BACTERIA, Kingdom.ARCHAEA, Kingdom.VIRUSES)) {
        rate = -31;
      }
    }
    // we rarely ever have a virus name, punish these a little more to avoid false virus matches
    if (htComp.isInKingdoms(reference, Kingdom.VIRUSES)) {
      rate -= 10;
    }
    // phylum to family
    rate += htComp.compareHigherRank(Rank.PHYLUM, query, reference, 10, -10, -1);
    rate += htComp.compareHigherRank(Rank.CLASS, query, reference, 15, -10, 0);
    rate += htComp.compareHigherRank(Rank.ORDER, query, reference, 15, -10, 0);
    rate += htComp.compareHigherRank(Rank.FAMILY, query, reference, 25, -15, 0);
    // we compare the genus only for minimal adjustments as it is part of the binomen usually
    // it helps to disambiguate in some cases though
    rate += htComp.compareHigherRank(Rank.GENUS, query, reference, 2, 1, 0);

    return minMax(-60, 50, rate);
  }

  @VisibleForTesting
  // rate ranks from -25 to +5, zero if nothing is know
  public static int rankSimilarity(Rank query, Rank ref) {
    int similarity = 0;
    if (ref != null) {
      // rate ranks lower that are not represented in the canonical, e.g. cultivars
      if (ref.isRestrictedToCode() == NomCode.CULTIVARS) {
        similarity -= 7;
      } else if (Rank.STRAIN == ref) {
        similarity -= 7;
      }

      if (ref.isUncomparable()) {
        // this also includes informal again
        similarity -= 3;
      }

      if (query != null) {
        // both ranks exist. Compare directly
        if (query.equals(ref)) {
          similarity += 10;

        } else if (either(query, ref, r -> r == Rank.INFRASPECIFIC_NAME, Rank::isInfraspecific)
            || either(query, ref, r -> r == Rank.INFRASUBSPECIFIC_NAME, Rank::isInfrasubspecific)
            || either(query, ref, r -> r == Rank.INFRAGENERIC_NAME, Rank::isInfrageneric)) {
          // unspecific rank matching its group
          similarity += 5;

        } else if (either(query, ref, r -> r == Rank.INFRAGENERIC_NAME, r -> r == Rank.GENUS)) {
          similarity += 4;

        } else if (either(query, ref, not(Rank::notOtherOrUnranked))) {
          // unranked
          similarity = 0;

        } else if (either(
            query, ref, (r1, r2) -> r1 == Rank.SPECIES && r2 == Rank.SPECIES_AGGREGATE)) {
          similarity += 2;

        } else if (either(
            query,
            ref,
            (r1, r2) ->
                (r1 == Rank.SPECIES || r1 == Rank.SPECIES_AGGREGATE) && r2.isInfraspecific()
                    || r1.isSupraspecific()
                        && r1 != Rank.SPECIES_AGGREGATE
                        && r2.isSpeciesOrBelow())) {
          // not good, different number of epithets means very unalike
          similarity -= 30;

        } else if (either(query, ref, r -> !r.isSuprageneric(), Rank::isSuprageneric)) {
          // we often have genus "homonyms" with higher taxa, e.g. Vertebrata, Dinosauria. Avoid
          // this
          similarity -= 35;

        } else {
          similarity -= Math.abs(ref.ordinal() - query.ordinal());
        }
      }

    } else if (query != null) {
      // reference has no rank, rate it lower
      similarity -= 1;
    }
    return minMax(-35, 6, similarity);
  }

  private static Predicate<Rank> not(Predicate<Rank> predicate) {
    return predicate.negate();
  }

  private static boolean either(Rank r1, Rank r2, Predicate<Rank> p) {
    return p.test(r1) || p.test(r2);
  }

  private static boolean either(Rank r1, Rank r2, BiFunction<Rank, Rank, Boolean> evaluator) {
    return evaluator.apply(r1, r2) || evaluator.apply(r2, r1);
  }

  private static boolean either(Rank r1, Rank r2, Predicate<Rank> p1, Predicate<Rank> p2) {
    return p1.test(r1) && p2.test(r2) || p2.test(r1) && p1.test(r2);
  }

  // rate kingdoms from -10 to +10, zero if nothing is know
  private int kingdomSimilarity(@Nullable Kingdom k1, @Nullable Kingdom k2) {
    if (k1 == null || k2 == null) {
      return 0;
    }
    if (k1 == Kingdom.INCERTAE_SEDIS || k2 == Kingdom.INCERTAE_SEDIS) {
      return 7;
    }

    if (k1 == k2) {
      return 10;
    }
    if (VAGUE_KINGDOMS.contains(k1) && VAGUE_KINGDOMS.contains(k2)) {
      return 8;
    }
    return -10;
  }

  /**
   * Produces a value between 0 and 100 by taking the not properly normalized confidence in the
   * expected range of 0 to 175. This function is optimized to deal with acceptable matches being
   * above 80, good matches above 90 and very good matches incl and above 100. The maximum of 100 is
   * reached for an input of 175 or above.
   */
  @VisibleForTesting
  public static int normConfidence(int s) {
    return minMax(
        0, 100, s <= 80 ? s : (int) Math.round(75.8 + (26d * (Math.log10((s - 70d) * 1.5) - 1))));
  }

  private static int minMax(int min, int max, int value) {
    return Math.max(min, Math.min(max, value));
  }

  /** Ordering based on match confidence and scientific name secondly. */
  public static class ConfidenceOrder implements Comparator<NameUsageMatch> {

    @Override
    public int compare(NameUsageMatch o1, NameUsageMatch o2) {
      return ComparisonChain.start()
          .compare(
              o1.getDiagnostics().getConfidence(),
              o2.getDiagnostics().getConfidence(),
              Ordering.natural().reverse().nullsLast())
          .compare(
              o1.getUsage().getCanonicalName(),
              o2.getUsage().getCanonicalName(),
              Ordering.natural().nullsLast())
          .result();
    }
  }
}

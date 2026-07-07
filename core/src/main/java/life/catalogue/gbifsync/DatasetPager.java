package life.catalogue.gbifsync;

import life.catalogue.api.jackson.UUIDSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.config.GbifConfig;
import life.catalogue.metadata.eml.EmlParser;
import life.catalogue.parser.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import de.undercouch.citeproc.csl.CSLType;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

import static life.catalogue.api.util.ObjectUtils.coalesce;
/**
 *
 */
public class DatasetPager {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPager.class);
  private static final Pattern SUFFIX_KEY = Pattern.compile("^(.+)-(\\d+)$");
  private static final Pattern domain = Pattern.compile("([^.]+)\\.[a-z]+$", Pattern.CASE_INSENSITIVE);
  private static final int MAX_OFFSET = 100_000;
  // bounded retry backoff for slow/timing-out GBIF pages - seconds, not minutes, so one bad page cannot stall the whole sync
  private static final int MAX_PAGE_TRIES = 6;
  private static final long BACKOFF_BASE_SECONDS = 2;
  private static final long BACKOFF_MAX_SECONDS = 60;

  private final Page page = new Page(100);
  private boolean hasNext = true;
  final WebTarget dataset;
  final WebTarget datasets;
  final Client client;
  private final GbifRegistryCache registry;
  private final Set<UUID> articlePublishers;
  private final Set<UUID> articleHostInstallations;
  private final Set<UUID> blockedDatasets;
  private final LocalDate since;

  /**
   * Convenience constructor that builds its own (un-shared) registry cache.
   * Mostly for tests and standalone use - the sync jobs pass a shared {@link GbifRegistryCache}.
   */
  public DatasetPager(Client client, GbifConfig gbif, @Nullable LocalDate since) {
    this(client, gbif, since, new GbifRegistryCache(client, gbif));
  }

  public DatasetPager(Client client, GbifConfig gbif, @Nullable LocalDate since, GbifRegistryCache registry) {
    this.client = client;
    this.since = since;
    this.registry = registry;
    articlePublishers = Set.copyOf(gbif.articlePublishers);
    articleHostInstallations = Set.copyOf(gbif.articleHostInstallations);
    blockedDatasets = Set.copyOf(gbif.blockedDatasets);
    dataset = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"));
    datasets = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"))
        .queryParam("type", "CHECKLIST");
    LOG.info("Created dataset pager for {}", datasets.getUri());
  }

  /**
   * @return true if there are potentially more pages to retrieve from GBIF, based purely on GBIF's endOfRecords flag.
   *   This is independent of how many usable datasets {@link #next()} actually returns: a page can be filtered away
   *   entirely (all blocked, constituents, non-checklists, or without a DWCA/COLDP endpoint), so {@link #next()} may
   *   return an empty list while this still returns true. Consumers must loop on {@code hasNext()} and must not treat
   *   an empty page from {@link #next()} as the end of the data.
   */
  public boolean hasNext() {
    return hasNext;
  }
  
  private WebTarget datasetPage() {
    var wt = datasets
        .queryParam("offset", page.getOffset())
        .queryParam("limit", page.getLimit());
    if (since != null) {
      wt = wt.queryParam("modified", since.toString());
    }
    return wt;
  }
  
  public int currPageNumber() {
    return 1 + page.getOffset() / page.getLimit();
  }
  
  protected GbifDataset get(UUID gbifKey) {
    if (blockedDatasets.contains(gbifKey)) {
      LOG.warn("Skip blocked dataset {}", gbifKey);
      return null;
    }
    LOG.debug("retrieve {}", gbifKey);
    return dataset.path(gbifKey.toString())
        .request()
        .accept(MediaType.APPLICATION_JSON_TYPE)
        .rx()
        .get(GDataset.class)
        .thenApply(this::convert)
        .toCompletableFuture()
        .join();
  }

  /**
   * @return total number of datasets to page through
   */
  public int count() {
    var wt = datasets.queryParam("limit", 0);
    if (since != null) {
      wt = wt.queryParam("modified", since.toString());
    }
    return wt
      .request()
      .accept(MediaType.APPLICATION_JSON_TYPE)
      .get(GResp.class)
      .count;
  }

  /**
   * Retrieves and converts the next page of datasets from GBIF, advancing the internal page offset.
   * The returned list contains only usable datasets, so it can be empty (e.g. an entire page of blocked datasets)
   * even when {@link #hasNext()} still returns true - see {@link #hasNext()} for the paging contract.
   */
  protected List<GbifDataset> next() throws InterruptedException {
    LOG.debug("retrieve {}", page);
    int tries = 0;
    while (tries < MAX_PAGE_TRIES) {
      try {
        GResp resp = datasetPage()
          .request()
          .accept(MediaType.APPLICATION_JSON_TYPE)
          .get(GResp.class);
        var list = processPage(resp);
        nextPage();
        return list;

      } catch (Exception e) {
        tries++;
        LOG.warn("Paging error {}: {}", page, e.getMessage(), e);
        if (tries < MAX_PAGE_TRIES) {
          TimeUnit.SECONDS.sleep(backoffSeconds(tries));
        }
      }
    }
    LOG.error("Reached maximum retries for page {}", page);
    nextPage();
    return Collections.emptyList();
  }

  /** Bounded exponential backoff in seconds with jitter (2,4,8,16,32, capped at 60), so a slow GBIF page can never stall the sync for minutes. */
  @VisibleForTesting
  static long backoffSeconds(int tries) {
    long secs = Math.min(BACKOFF_MAX_SECONDS, BACKOFF_BASE_SECONDS * (1L << (tries - 1)));
    return secs + ThreadLocalRandom.current().nextLong(secs / 2 + 1);
  }

  /**
   * Converts a single raw GBIF response page into the list of usable datasets and updates {@link #hasNext()}
   * from the page's endOfRecords flag. Blocked datasets are removed before {@link #convert(GDataset)}, so a page
   * consisting entirely of blocked (or otherwise unusable) datasets returns an empty list while {@code hasNext()}
   * keeps reflecting whether GBIF has further pages.
   */
  @VisibleForTesting
  List<GbifDataset> processPage(GResp resp) {
    hasNext = !resp.endOfRecords;
    return resp.results.stream()
      .filter(gd -> gd.key == null || !blockedDatasets.contains(gd.key))
      .map(this::convert)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private void nextPage() {
    page.next();
    if (page.getOffset() > MAX_OFFSET) {
      hasNext = false;
      LOG.warn("Stop paging. We've hit the maximum page offset {}: {}", MAX_OFFSET, page);
    }
  }

  protected static class GbifDataset {
    final Dataset dataset = new Dataset();
    final DatasetSettings settings = new DatasetSettings();
    Integer clbDatasetKey;
    // identifier keys for just the CLB_DATASET_KEY entries
    final List<Integer> identifierKeys = new ArrayList<>();
    // raw GBIF dataset kept for lazy agent resolution, see DatasetPager#resolveAgents
    GDataset src;
    // GBIF registry modified timestamp (UTC), used to skip datasets that have not changed since the last sync
    LocalDateTime modified;

    public Integer getKey() {
      return dataset.getKey();
    }

    public LocalDateTime getModified() {
      return modified;
    }

    public UUID getGbifKey() {
      return dataset.getGbifKey();
    }

    public UUID getGbifPublisherKey() {
      return dataset.getGbifPublisherKey();
    }

    public String getTitle() {
      return dataset.getTitle();
    }

    public URI getDataAccess() {
      return settings.getURI(Setting.DATA_ACCESS);
    }

    public DataFormat getDataFormat() {
      return settings.getEnum(Setting.DATA_FORMAT);
    }
  }

  /**
   * Maps all dataset metadata that is already contained in the paged GBIF response, without hitting the
   * registry organisation/installation endpoints. The publisher, host and contact agents are resolved
   * lazily in {@link #resolveAgents(GbifDataset)} only for datasets that are new or have changed.
   * @return converted dataset or NULL if illegitimate
   */
  private GbifDataset convert(GDataset g) {
    if (g.parentDatasetKey != null) {
      LOG.debug("Skip constituent dataset: {} - {}", g.key, g.title);
      return null;
    } else if (!"CHECKLIST".equalsIgnoreCase(g.type)) {
      LOG.debug("Skip {} dataset: {} - {}", g.type, g.key, g.title);
      return null;
    }

    GbifDataset d = new GbifDataset();
    d.src = g;
    d.modified = g.modified;
    d.dataset.setPrivat(false);
    d.dataset.setGbifKey(g.key);
    d.dataset.setGbifPublisherKey(g.publishingOrganizationKey);
    d.dataset.setTitle(g.title);
    d.dataset.setDescription(g.description);
    DOI.parse(g.doi).ifPresent(doi -> d.dataset.addIdentifier(new Identifier(doi)));
    Optional<GEndpoint> coldp = g.endpoints.stream().filter(e -> e.type.equalsIgnoreCase("COLDP")).findFirst();
    Optional<GEndpoint> dwca = g.endpoints.stream().filter(e -> e.type.equalsIgnoreCase("DWC_ARCHIVE")).findFirst();
    if (coldp.isPresent() || dwca.isPresent()) {
      d.dataset.setOrigin(DatasetOrigin.EXTERNAL);
      if (coldp.isPresent()) {
        d.settings.put(Setting.DATA_FORMAT, DataFormat.COLDP);
        d.settings.put(Setting.DATA_ACCESS, uri(coldp.get().url));
      } else {
        d.settings.put(Setting.DATA_FORMAT, DataFormat.DWCA);
        d.settings.put(Setting.DATA_ACCESS, uri(dwca.get().url));
      }
    } else {
      LOG.info("Skip dataset without DWCA or COLDP access: {} - {}", d.dataset.getGbifKey(), d.dataset.getTitle());
      return null;
    }
    // type
    if (d.dataset.getGbifPublisherKey() != null && articlePublishers.contains(d.dataset.getGbifPublisherKey())
        || g.installationKey != null && articleHostInstallations.contains(g.installationKey)
    ) {
      d.dataset.setType(DatasetType.ARTICLE);

    } else if (g.subtype != null) {
      d.dataset.setType(EmlParser.parseType(g.subtype));
    } else {
      d.dataset.setType(DatasetType.OTHER);
    }
    d.dataset.setUrl(uri(g.homepage));
    d.dataset.setLicense(SafeParser.parse(LicenseParser.PARSER, g.license).orElse(License.UNSPECIFIED, License.OTHER));
    d.dataset.setGeographicScope(coverage(g.geographicCoverages));
    d.dataset.setTaxonomicScope(coverage(g.taxonomicCoverages));
    d.dataset.setTemporalScope(coverage(g.temporalCoverages));
    addIdentifiers(d, g.key, g.identifiers);
    d.dataset.setSource(toSource(g.bibliographicCitations));
    //d.setNotes(toNotes(g.comments));
    d.dataset.setIssued(g.pubDate);
    d.dataset.setCreated(LocalDateTime.now());
    LOG.debug("Dataset {} converted: {}", g.key, g.title);
    return d;
  }

  /**
   * Resolves the publisher and host organisations and the dataset contacts from the GBIF registry.
   * This is the only part of the conversion that hits the slow registry organisation/installation
   * endpoints, so it is called lazily - only for datasets that are new or have actually changed.
   */
  public void resolveAgents(GbifDataset d) {
    GDataset g = d.src;
    d.dataset.setPublisher(registry.publisher(g.publishingOrganizationKey));
    d.dataset.addContributor(registry.host(g.installationKey));
    // convert contact and authors based on contact type: https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/ContactType.java
    // Not mapped: PUBLISHER,DISTRIBUTOR,METADATA_AUTHOR,TECHNICAL_POINT_OF_CONTACT,OWNER,PROCESSOR,USER,PROGRAMMER,DATA_ADMINISTRATOR,SYSTEM_ADMINISTRATOR,HEAD_OF_DELEGATION,TEMPORARY_HEAD_OF_DELEGATION,ADDITIONAL_DELEGATE,TEMPORARY_DELEGATE,REGIONAL_NODE_REPRESENTATIVE,NODE_MANAGER,NODE_STAFF
    var contacts = byType(g.contacts, "POINT_OF_CONTACT", "ADMINISTRATIVE_POINT_OF_CONTACT");
    if (!contacts.isEmpty()) {
      d.dataset.setContact(contacts.get(0));
    }
    d.dataset.setCreator(byType(g.contacts, "ORIGINATOR", "PRINCIPAL_INVESTIGATOR", "AUTHOR", "CONTENT_PROVIDER"));
    d.dataset.setEditor(byType(g.contacts, "EDITOR", "CURATOR", "CUSTODIAN_STEWARD"));
    List<Agent> contributors = g.contacts.stream()
                                         .map(GAgent::toAgent)
                                         .filter(a -> a != null
                                                      && !d.dataset.getCreator().contains(a)
                                                      && !d.dataset.getEditor().contains(a)
                                                      && !Objects.equals(d.dataset.getContact(), a)
                                                      && !Objects.equals(d.dataset.getPublisher(), a))
                                         .collect(Collectors.toList());
    d.dataset.setContributor(contributors);
  }

  private List<Citation> toSource(List<GCitation> bibliographicCitations) {
    if (bibliographicCitations == null || bibliographicCitations.isEmpty()) {
      return null;
    }
    List<Citation> citations = new ArrayList<>();
    for (var b : bibliographicCitations) {
      if (!StringUtils.isBlank(b.identifier)) {
        Citation c = new Citation();
        c.setType(CSLType.BOOK);
        c.setTitle(b.text);
        c.setId(b.identifier);

        if (DOI.isParsable(b.identifier)) {
          var doi = DOI.parse(b.identifier).orElse(null);
          c.setDoi(doi);
        }
        citations.add(c);
      }
    }
    return citations;
  }

  private void addIdentifiers(GbifDataset d, UUID uuid, List<GIdentifier> ids) {
    if (ids != null && !ids.isEmpty()) {
      List<Identifier> list = new ArrayList<>();
      for (GIdentifier id : ids) {
        if (StringUtils.isBlank(id.type) || StringUtils.isBlank(id.identifier)) continue;

        Identifier ident = null;
        switch (id.type) {
          case "DOI":
            // normalize DOIs
            Optional<DOI> doi = DOI.parse(id.identifier);
            if (doi.isPresent()) {
              ident = new Identifier(doi.get());
            } else {
              LOG.warn("Ignore bad DOI GBIF identifier {} found in dataset {}", id.identifier, uuid);
            }
            break;

          case "URL":
            // make sure we have some http(s)
            if(!id.identifier.startsWith("http") && !id.identifier.startsWith("https")){
              id.identifier = "http://" + id.identifier;
            }
            ident = new Identifier(Identifier.Scope.URN, id.identifier);
            break;

          case "UUID":
            try {
              // normalize UUIDs
              UUID uid = UUIDSerde.from(id.identifier);
              ident = new Identifier(Identifier.Scope.GBIF, uid.toString());

            } catch (IllegalArgumentException e) {
              LOG.warn("Ignore bad UUID GBIF identifier {} found in dataset {}", id.identifier, uuid);
            }
            break;

          case GbifSyncJob.CLB_DATASET_KEY:
            // track all keys for existing clb dataset key identifiers, but do not add them as real identifiers
            if (id.key != null) {
              d.identifierKeys.add(id.key);
            }
            try {
              Integer clbKey = Integer.parseInt(id.identifier);
              if (d.clbDatasetKey == null) {
                d.clbDatasetKey = clbKey;
              } else if (!clbKey.equals( d.clbDatasetKey) ) {
                LOG.warn("Multiple ChecklistBank identifiers for dataset {} in GBIF", uuid);
              }
            } catch (NumberFormatException e) {
              LOG.warn("ChecklistBank identifier for dataset {} in GBIF is not an integer: {}", uuid, id.identifier);
            }
            continue;

          case "GBIF_PORTAL":
            // we dont need those - they are prehistoric
            continue;

          default:
            ident = new Identifier(id.type, id.identifier);
        }

        if (ident != null) {
          list.add(ident);
        }
      }
      // we enforce unique identifiers - there are often duplicates in GBIF
      if (list.size()>1) {
        list = list.stream().distinct().collect(Collectors.toUnmodifiableList());
      }
      d.dataset.setIdentifier(list);
    }
  }

  static String toNotes(List<GComment> comments) {
    if (comments == null || comments.isEmpty()) {
      return null;
    }
    return comments.stream()
                   .filter(c -> !StringUtils.isBlank(c.content))
                   .map(c -> c.content)
                   .collect(Collectors.joining("; "));
  }

  static List<Agent> byType(List<GAgent> contacts, String... type) {
    List<Agent> people = new ArrayList<>();
    var types = Set.of(type);
    for (GAgent c : contacts) {
      if (c.type != null && types.contains(c.type.toUpperCase())) {
        people.add(c.toAgent());
      }
    }
    return people;
  }
  
  static URI uri(String url) {
    return SafeParser.parse(UriParser.PARSER, url).orNull();
  }
  
  static String coverage(List<GCoverage> coverages) {
    return coverages == null ? null : StringUtils.trimToNull(coverages.stream()
        .filter(c -> c!= null && !StringUtils.isBlank(c.description))
        .map(c -> c.description)
        .collect(Collectors.joining("; ")));
  }
  
  static class GResp {
    public boolean endOfRecords;
    public int count;
    public List<GDataset> results;
  }
  
  static class GDataset {
    public UUID key;
    public UUID parentDatasetKey;
    public UUID installationKey;
    public UUID publishingOrganizationKey;
    public String doi;
    public String type;
    public String subtype;
    public String title;
    public String description;
    public String homepage;
    public GCitation citation;
    public List<GCoverage> taxonomicCoverages;
    public List<GCoverage> geographicCoverages;
    public List<GCoverage> temporalCoverages;
    public String license;
    public FuzzyDate pubDate;
    // the GBIF registry modified timestamp (UTC), used to detect datasets that changed since the last sync
    public LocalDateTime modified;
    public List<GEndpoint> endpoints;
    public List<GAgent> contacts;
    public List<GComment> comments;
    public List<GIdentifier> identifiers;
    public List<GCitation> bibliographicCitations;

    public void setPubDate(String pubDate) {
      try {
        this.pubDate = DateParser.PARSER.parse(pubDate).orElse(null);
      } catch (UnparsableException e) {
        LOG.warn("Failed to parse pubDate {}", pubDate, e);
      }
    }

    public void setModified(String modified) {
      this.modified = parseGbifDateTime(modified);
    }
  }

  /**
   * Parses a GBIF ISO-8601 timestamp with offset (e.g. 2026-06-09T20:04:21.795+00:00) into a UTC LocalDateTime.
   * @return the UTC LocalDateTime or null if blank/unparsable
   */
  static LocalDateTime parseGbifDateTime(String s) {
    if (StringUtils.isBlank(s)) {
      return null;
    }
    try {
      return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (DateTimeParseException e) {
      LOG.warn("Failed to parse GBIF datetime {}", s);
      return null;
    }
  }

  static class GIdentifier {
    public Integer key;
    public String type;
    public String identifier;
  }

  static class GCitation {
    public String text;
    public String identifier;
    public String url;
  }
  
  static class GCoverage {
    public String description;
  }

  static class GEndpoint {
    public String type;
    public String url;
  }

  static class GComment {
    public String content;
    public String createdBy;
    public String modifiedBy;
  }

  static class GKeywords {
    public String content;
    public String createdBy;
    public String modifiedBy;
  }

  static class GInstallation {
    public UUID key;
    public UUID organizationKey;
    public String title;
    public List<GEndpoint> endpoints;
    public List<GAgent> contacts;
  }

  static class GAgent {
    public String key;
    public String type;
    public String title;
    // organisation description, only populated for publisher/host organisations (see GbifRegistryCache)
    public String description;
    public String firstName;
    public String lastName;
    public List<String> position;
    public String organization;
    public List<String> address;
    public String city;
    public String postalCode;
    public String province;
    public String country;
    public String latitude;
    public String longitude;
    public List<String> homepage;
    public List<String> email;
    public List<String> phone;
    public List<GAgent> contacts;
    public List<String> userId;

    public Agent toAgent(){
      Agent org = new Agent(firstOrcid(), lastName, firstName,
            null, coalesce(organization, title), null, city, province, CountryParser.PARSER.parseOrNull(country),
            firstEmail(), firstHomepage(), null);
      return org.getName() != null ? org : null;
    }

    String firstOrcid() {
      if (userId != null) {
        for (String uid : userId) {
          // figure out if we have an ORCID !!!
          var orcid = Agent.parseORCID(uid);
          if (orcid.isPresent()) {
            return orcid.get();
          }
        }
      }
      return null;
    }

    String firstHomepage() {
      return homepage == null || homepage.isEmpty() ? null : homepage.get(0);
    }

    String firstEmail() {
      if (email != null) {
        for (String add : email) {
          // deal with <>, e.g. Scratchpad Team <scratchpad@nhm.ac.uk>
          String clean = life.catalogue.common.text.StringUtils.extractEmail(add);
          if (clean != null) {
            return clean;
          }
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return "GAgent{" +
          "address=" + address +
          ", city='" + city + '\'' +
          ", country='" + country + '\'' +
          ", homepage=" + homepage +
          ", email=" + email +
          ", phone=" + phone +
          '}';
    }
  }
}

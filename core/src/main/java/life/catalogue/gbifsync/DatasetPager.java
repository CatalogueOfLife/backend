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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
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

  private final Page page = new Page(100);
  private boolean hasNext = true;
  final WebTarget dataset;
  final WebTarget datasets;
  final WebTarget organization;
  final WebTarget installation;
  final Client client;
  private final LoadingCache<UUID, Agent> publisherCache;
  private final LoadingCache<UUID, Agent> hostCache;
  private final Set<UUID> articlePublishers;
  private final Set<UUID> articleHostInstallations;
  private final LocalDate since;

  public DatasetPager(Client client, GbifConfig gbif, @Nullable LocalDate since) {
    this.client = client;
    this.since = since;
    articlePublishers = Set.copyOf(gbif.articlePublishers);
    articleHostInstallations = Set.copyOf(gbif.articleHostInstallations);
    dataset = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"));
    datasets = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"))
        .queryParam("type", "CHECKLIST");
    organization = client.target(UriBuilder.fromUri(gbif.api).path("/organization/"));
    installation = client.target(UriBuilder.fromUri(gbif.api).path("/installation/"));
    publisherCache = Caffeine.newBuilder()
                             .maximumSize(1000)
                             .build(this::loadPublisher);
    hostCache = Caffeine.newBuilder()
                        .maximumSize(1000)
                        .build(this::loadHost);
    LOG.info("Created dataset pager for {}", datasets.getUri());
  }

  private Agent loadPublisher(UUID key) {
    WebTarget pubDetail = organization.path(key.toString());
    LOG.debug("Retrieve organization {}", pubDetail.getUri());
    GAgent p = pubDetail.request()
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .get(GAgent.class);
    return p.toAgent();
  }

  private Agent loadHost(UUID key) {
    WebTarget insDetail = installation.path(key.toString());
    LOG.debug("Retrieve installation {}", insDetail.getUri());
    GInstallation ins = insDetail.request()
                                 .accept(MediaType.APPLICATION_JSON_TYPE)
                                 .get(GInstallation.class);
    if (ins != null && ins.organizationKey != null) {
      var host = publisherCache.get(ins.organizationKey);
      host.setNote("Host");
      return host;
    }
    return null;
  }

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
  
  public GbifDataset get(UUID gbifKey) {
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

  public List<GbifDataset> next() {
    LOG.debug("retrieve {}", page);
    try {
      return datasetPage()
          .request()
          .accept(MediaType.APPLICATION_JSON_TYPE)
          .rx()
          .get(GResp.class)
          .thenApply(resp -> {
            hasNext = !resp.endOfRecords;
            return resp;
          })
          .thenApply(resp -> resp.results.stream()
              .map(this::convert)
              .filter(Objects::nonNull)
              .collect(Collectors.toList())
          )
          .toCompletableFuture()
          .join();
      
    } catch (Exception e) {
      hasNext = false;
      throw new IllegalStateException(e);
      
    } finally {
      page.next();
    }
  }

  protected static class GbifDataset {
    final Dataset dataset = new Dataset();
    final DatasetSettings settings = new DatasetSettings();
    Integer clbDatasetKey;
    // identifier keys for just the CLB_DATASET_KEY entries
    final List<Integer> identifierKeys = new ArrayList<>();

    public Integer getKey() {
      return dataset.getKey();
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
    d.dataset.setPrivat(false);
    d.dataset.setGbifKey(g.key);
    d.dataset.setGbifPublisherKey(g.publishingOrganizationKey);
    d.dataset.setPublisher(publisherCache.get(g.publishingOrganizationKey));
    d.dataset.addContributor(hostCache.get(g.installationKey));
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
    addIdentifiers(d, g.key, g.identifiers);
    d.dataset.setSource(toSource(g.bibliographicCitations));
    //d.setNotes(toNotes(g.comments));
    d.dataset.setIssued(g.pubDate);
    d.dataset.setCreated(LocalDateTime.now());
    LOG.debug("Dataset {} converted: {}", g.key, g.title);
    return d;
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

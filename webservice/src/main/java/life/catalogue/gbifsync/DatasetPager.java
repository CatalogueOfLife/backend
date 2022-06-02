package life.catalogue.gbifsync;

import life.catalogue.api.jackson.UUIDSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.config.GbifConfig;
import life.catalogue.parser.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;

import de.undercouch.citeproc.csl.CSLType;

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

  public DatasetPager(Client client, GbifConfig gbif) {
    this.client = client;
    articlePublishers = Set.copyOf(gbif.articlePublishers);
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

  <T> T getFirst(List<T> vals) {
    for (T val : vals) {
      if (val != null) return val;
    }
    return null;
  }

  public boolean hasNext() {
    return hasNext;
  }
  
  private WebTarget datasetPage() {
    return datasets
        .queryParam("offset", page.getOffset())
        .queryParam("limit", page.getLimit());
  }
  
  public int currPageNumber() {
    return 1 + page.getOffset() / page.getLimit();
  }
  
  public DatasetWithSettings get(UUID gbifKey) {
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

  public List<DatasetWithSettings> next() {
    LOG.info("retrieve {}", page);
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
  
  /**
   * @return converted dataset or NULL if illegitimate
   */
  private DatasetWithSettings convert(GDataset g) {
    if (g.parentDatasetKey != null) {
      LOG.debug("Skip constituent dataset: {} - {}", g.key, g.title);
      return null;
    } else if (!"CHECKLIST".equalsIgnoreCase(g.type)) {
      LOG.debug("Skip {} dataset: {} - {}", g.type, g.key, g.title);
      return null;
    }

    DatasetWithSettings d = new DatasetWithSettings();
    d.setGbifKey(g.key);
    d.setGbifPublisherKey(g.publishingOrganizationKey);
    d.setPublisher(publisherCache.get(g.publishingOrganizationKey));
    d.getDataset().addContributor(hostCache.get(g.installationKey));
    d.setTitle(g.title);
    d.setDescription(g.description);
    DOI.parse(g.doi).ifPresent(d::setDoi);
    Optional<GEndpoint> dwca = g.endpoints.stream().filter(e -> e.type.equalsIgnoreCase("DWC_ARCHIVE")).findFirst();
    if (dwca.isPresent()) {
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.setDataFormat(DataFormat.DWCA);
      d.setDataAccess(uri(dwca.get().url));
    } else {
      LOG.info("Skip dataset without DWCA access: {} - {}", d.getGbifKey(), d.getTitle());
      return null;
    }
    // type
    if (d.getGbifPublisherKey() != null && articlePublishers.contains(d.getGbifPublisherKey())) {
      d.setType(DatasetType.ARTICLE);

    } else if (g.subtype != null) {
      switch (g.subtype) {
        case "NOMENCLATOR_AUTHORITY":
          d.setType(DatasetType.NOMENCLATURAL);
          break;
        case "TAXONOMIC_AUTHORITY":
        case "GLOBAL_SPECIES_DATASET":
        case "INVENTORY_REGIONAL":
          d.setType(DatasetType.TAXONOMIC);
          break;
        case "INVENTORY_THEMATIC":
          d.setType(DatasetType.THEMATIC);
          break;
        case "TREATMENT_ARTICLE":
          d.setType(DatasetType.ARTICLE);
          break;
        default:
          d.setType(DatasetType.OTHER);
        }
    } else {
      d.setType(DatasetType.OTHER);
    }
    d.setUrl(uri(g.homepage));
    d.setLicense(SafeParser.parse(LicenseParser.PARSER, g.license).orElse(License.UNSPECIFIED, License.OTHER));
    d.setGeographicScope(coverage(g.geographicCoverages));
    d.setTaxonomicScope(coverage(g.taxonomicCoverages));
    d.setTemporalScope(coverage(g.temporalCoverages));
    // convert contact and authors based on contact type: https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/ContactType.java
    // Not mapped: PUBLISHER,DISTRIBUTOR,METADATA_AUTHOR,TECHNICAL_POINT_OF_CONTACT,OWNER,PROCESSOR,USER,PROGRAMMER,DATA_ADMINISTRATOR,SYSTEM_ADMINISTRATOR,HEAD_OF_DELEGATION,TEMPORARY_HEAD_OF_DELEGATION,ADDITIONAL_DELEGATE,TEMPORARY_DELEGATE,REGIONAL_NODE_REPRESENTATIVE,NODE_MANAGER,NODE_STAFF
    var contacts = byType(g.contacts, "POINT_OF_CONTACT", "ADMINISTRATIVE_POINT_OF_CONTACT");
    if (!contacts.isEmpty()) {
      d.setContact(contacts.get(0));
    }
    d.setCreator(byType(g.contacts, "ORIGINATOR", "PRINCIPAL_INVESTIGATOR", "AUTHOR", "CONTENT_PROVIDER"));
    d.setEditor(byType(g.contacts, "EDITOR", "CURATOR", "CUSTODIAN_STEWARD"));
    List<Agent> contributors = g.contacts.stream()
                                         .map(GAgent::toAgent)
                                         .filter(a -> a != null
                                                      && !d.getCreator().contains(a)
                                                      && !d.getEditor().contains(a)
                                                      && !Objects.equals(d.getContact(), a)
                                                      && !Objects.equals(d.getPublisher(), a))
                                         .collect(Collectors.toList());
    d.setContributor(contributors);
    d.setIdentifier(toIdentifier(g.key, g.identifiers));
    d.setSource(toSource(g.bibliographicCitations));
    d.setNotes(toNotes(g.comments));
    d.setIssued(g.pubDate);
    d.setCreated(LocalDateTime.now());
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

  static Map<String, String> toIdentifier(UUID uuid, List<GIdentifier> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    // we enforce unique identifiers - there are often duploicates in GBIF
    final Set<String> values = new HashSet<>();
    final Map<String, String> map = new HashMap<>();
    final Map<String, Integer> suffices = new HashMap<>();
    for (GIdentifier id : ids) {
      if (StringUtils.isBlank(id.type) || StringUtils.isBlank(id.identifier)) continue;

      String key = null;
      switch (id.type) {
        case "DOI":
          // normalize DOIs
          Optional<DOI> doi = DOI.parse(id.identifier);
          if (doi.isPresent()) {
            id.identifier = doi.get().toString();
            key = uniqueKey("DOI", map, suffices);
          } else {
            LOG.warn("Ignore bad DOI GBIF identifier {} found in dataset {}", id.identifier, uuid);
          }
          break;

        case "URL":
          // make sure we have some http(s)
          if(!id.identifier.startsWith("http") && !id.identifier.startsWith("https")){
            id.identifier = "http://" + id.identifier;
          }
          key = uniqueKey(extractDomain(id.identifier), map, suffices);
          break;

        case "UUID":
          try {
            // normalize UUIDs
            UUID uid = UUIDSerde.from(id.identifier);
            id.identifier = uid.toString();
            key = uniqueKey("GBIF", map, suffices);

          } catch (IllegalArgumentException e) {
            LOG.warn("Ignore bad UUID GBIF identifier {} found in dataset {}", id.identifier, uuid);
          }
          break;

        case "GBIF_PORTAL":
          // we dont need those - they are prehistoric
          continue;
        default:
          LOG.warn("Unknown GBIF identifier type {} found in dataset {}", id.type, uuid);
      }

      if (key != null) {
        if (values.contains(id.identifier.toUpperCase())) {
          LOG.debug("Ignore duplicate identifier {} of type {} found in dataset {}", id.identifier, id.type, uuid);
          // reduce suffix if it was such a key
          var m = SUFFIX_KEY.matcher(key);
          if (m.find()) {
            var origKey = m.group(1);
            suffices.put(origKey, suffices.get(origKey)-1);
          }
        } else {
          values.add(id.identifier.toUpperCase());
          map.put(key, id.identifier);
        }
      }
    }
    return map;
  }

  static String uniqueKey(final String key, Map<String, String> map, Map<String, Integer> suffices) {
    int num = suffices.getOrDefault(key, 2);
    if (map.containsKey(key)) {
      String key2 = key + "-" + num++;
      suffices.put(key, num);
      return key2;
    } else {
      return key;
    }
  }

  @VisibleForTesting
  protected static String extractDomain(String url) {
    try {
      URI uri = URI.create(url);
      if (uri.getHost() != null) {
        Matcher m = domain.matcher(uri.getHost());
        if (m.find()) {
          return m.group(1);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.info("Bad GBIF URL identifier {}", url);
    }
    return "URL";
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
      return email == null || email.isEmpty() ? null : email.get(0);
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

package life.catalogue.gbifsync;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.config.GbifConfig;
import life.catalogue.parser.LicenseParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.UriParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 *
 */
public class DatasetPager {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPager.class);
  
  private final Page page = new Page(100);
  private boolean hasNext = true;
  final WebTarget dataset;
  final WebTarget datasets;
  final WebTarget publisher;
  final Client client;
  private final LoadingCache<UUID, String> pCache;
  
  public DatasetPager(Client client, GbifConfig gbif) {
    this.client = client;
    dataset = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"));
    datasets = client.target(UriBuilder.fromUri(gbif.api).path("/dataset"))
        .queryParam("type", "CHECKLIST");
    publisher = client.target(UriBuilder.fromUri(gbif.api).path("/organization/"));
    
    pCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build(new CacheLoader<UUID, String>() {
                 @Override
                 public String load(UUID key) throws Exception {
                   WebTarget pubDetail = publisher.path(key.toString());
                   LOG.info("Retrieve publisher {}", pubDetail.getUri());
                   GPublisher p = pubDetail.request()
                       .accept(MediaType.APPLICATION_JSON_TYPE)
                       .get(GPublisher.class);
                   return p == null ? null : p.title;
                 }
               }
        );
    LOG.info("Created dataset pager for {}", datasets.getUri());
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
  
  private String publisher(final UUID key) {
    try {
      return pCache.get(key);
    } catch (ExecutionException e) {
      LOG.error("Failed to retrieve publisher {} from cache", key, e);
      throw new IllegalStateException(e);
    }
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
    }

    DatasetWithSettings d = new DatasetWithSettings();
    d.setGbifKey(g.key);
    d.setGbifPublisherKey(g.publishingOrganizationKey);
    d.getOrganisations().add(publisher(g.publishingOrganizationKey));
    d.setTitle(g.title);
    d.setDescription(g.description);
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
    if (GbifSync.PLAZI_KEY.equals(d.getGbifPublisherKey())) {
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
    d.setWebsite(uri(g.homepage));
    d.setLicense(SafeParser.parse(LicenseParser.PARSER, g.license).orElse(License.UNSPECIFIED, License.OTHER));
    d.setGeographicScope(coverage(g.geographicCoverages));
    // convert contact and authors based on contact type: https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/ContactType.java
    // Not mapped: PUBLISHER,DISTRIBUTOR,METADATA_AUTHOR,TECHNICAL_POINT_OF_CONTACT,OWNER,PROCESSOR,USER,PROGRAMMER,DATA_ADMINISTRATOR,SYSTEM_ADMINISTRATOR,HEAD_OF_DELEGATION,TEMPORARY_HEAD_OF_DELEGATION,ADDITIONAL_DELEGATE,TEMPORARY_DELEGATE,REGIONAL_NODE_REPRESENTATIVE,NODE_MANAGER,NODE_STAFF
    var contacts = byType(g.contacts, "POINT_OF_CONTACT", "ADMINISTRATIVE_POINT_OF_CONTACT");
    if (!contacts.isEmpty()) {
      d.setContact(contacts.get(0));
    }
    d.setAuthors(byType(g.contacts, "ORIGINATOR", "PRINCIPAL_INVESTIGATOR", "AUTHOR", "CONTENT_PROVIDER"));
    d.setEditors(byType(g.contacts, "EDITOR", "CURATOR", "CUSTODIAN_STEWARD"));
    d.setNotes(null);
    d.setVersion(opt(g.pubDate));
    d.setCreated(LocalDateTime.now());
    LOG.debug("Dataset {} converted: {}", g.key, g.title);
    return d;
  }

  static List<Person> byType(List<GContact> contacts, String... type) {
    List<Person> people = new ArrayList<>();
    var types = Set.of(type);
    for (GContact c : contacts) {
      if (c.type != null && types.contains(c.type.toUpperCase())) {
        people.add(new Person(c.firstName, c.lastName, c.email == null ? null : c.email.get(0), null));
      }
    }
    return people;
  }
  static String opt(Object obj) {
    return obj == null ? null : obj.toString();
  }
  
  static URI uri(String url) {
    return SafeParser.parse(UriParser.PARSER, url).orNull();
  }
  
  static String coverage(List<GCoverage> coverages) {
    return coverages == null ? null : coverages.stream()
        .filter(c -> c!= null && !StringUtils.isBlank(c.description))
        .map(c -> c.description)
        .collect(Collectors.joining("; "));
  }
  
  static class GResp {
    public boolean endOfRecords;
    public List<GDataset> results;
  }
  
  static class GDataset {
    public UUID key;
    public UUID parentDatasetKey;
    public UUID publishingOrganizationKey;
    public String doi;
    public String subtype;
    public String title;
    public String description;
    public String homepage;
    public GCitation citation;
    public List<GCoverage> geographicCoverages;
    public String license;
    public LocalDate pubDate;
    public List<GEndpoint> endpoints;
    public List<GContact> contacts;
  }
  
  static class GCitation {
    public String text;
    public String url;
  }
  
  static class GCoverage {
    public String description;
  }

  static class GEndpoint {
    public String type;
    public String url;
  }
  
  static class GContact extends GAgent {
    public String type;
    public String firstName;
    public String lastName;
    public List<String> position;
    public String organization;
  }
  
  static class GPublisher extends GAgent {
    public UUID key;
    public String title;
    public String description;
    public String latitude;
    public String longitude;
    public List<GContact> contacts;
  }
  
  static abstract class GAgent {
    public List<String> address;
    public String city;
    public String country;
    public List<String> homepage;
    public List<String> email;
    public List<String> phone;
    
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

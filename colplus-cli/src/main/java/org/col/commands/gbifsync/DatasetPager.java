package org.col.commands.gbifsync;

import org.col.api.Dataset;
import org.col.api.Page;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.License;
import org.col.commands.config.GbifConfig;
import org.col.parser.LicenseParser;
import org.col.parser.SafeParser;
import org.col.parser.UriParser;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.RxWebTarget;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 *
 */
public class DatasetPager {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPager.class);

  private final Page page = new Page(100);
  private boolean hasNext = true;
  final RxWebTarget<RxCompletionStageInvoker> datasets;
  final RxWebTarget<RxCompletionStageInvoker> publisher;
  final RxClient<RxCompletionStageInvoker> client;

  public DatasetPager(RxClient<RxCompletionStageInvoker> client, GbifConfig gbif) {
    UriBuilder uriBuilder = UriBuilder.fromUri(gbif.api);
    this.client = client;
    datasets = client
        .target(uriBuilder.path("/dataset"))
        .queryParam("type", "CHECKLIST");
    publisher = client
        .target(uriBuilder.path("/organization/"));
    LOG.info("Created dataset pager for {}", datasets.getUri());
  }

  public boolean hasNext() {
    return hasNext;
  }

  private RxWebTarget<RxCompletionStageInvoker> applyPage(RxWebTarget<RxCompletionStageInvoker> target) {
    return target
        .queryParam("offset", page.getOffset())
        .queryParam("limit", page.getLimit());
  }

  public List<Dataset> next() {
    LOG.info("retrieve {}", page);
    try {
      return applyPage(datasets)
          .request()
          .accept(MediaType.APPLICATION_JSON_TYPE)
          .rx()
          .get(GResp.class)
          .thenApply(resp -> {
            hasNext = !resp.endOfRecords;
            return resp;
          })
          .thenApplyAsync(resp -> resp.results.parallelStream().map(DatasetPager :: convert).collect(Collectors.toList()))
          .toCompletableFuture()
          .get();

    } catch (Exception e) {
      hasNext = false;
      throw new IllegalStateException(e);

    } finally {
      page.next();
    }
  }

  private static Dataset convert(GDataset g) {
    Dataset d = new Dataset();
    d.setGbifKey(g.key);
    d.setTitle(g.title);
    d.setDescription(g.description);
    Optional<GEndpoint> dwca = g.endpoints.stream().filter(e -> e.type.equalsIgnoreCase("DWC_ARCHIVE")).findFirst();
    if (dwca.isPresent()) {
      d.setDataFormat(DataFormat.DWCA);
      d.setDataAccess(uri(dwca.get().url));
    } else {
      LOG.warn("Dataset without DWCA access: {} - {}", d.getGbifKey(), d.getTitle());
    }
    d.setHomepage(uri(g.homepage));
    d.setLicense(SafeParser.parse(LicenseParser.PARSER, g.license).orElse(License.UNSPECIFIED, License.UNSUPPORTED));
    //TODO retrieve real publisher title
    d.setOrganisation(opt(g.publishingOrganizationKey));
    //TODO: convert contact and authors
    d.setContactPerson(null);
    d.setAuthorsAndEditors(null);
    d.setNotes(null);
    d.setVersion(opt(g.pubDate));
    d.setCreated(LocalDateTime.now());
    return d;
  }

  static String opt(Object obj) {
    return obj == null ? null : obj.toString();
  }

  static URI uri(String url) {
    return SafeParser.parse(UriParser.PARSER, url).orNull();
  }

  static class GResp {
    public boolean endOfRecords;
    public List<GDataset> results;
  }
  static class GDataset {
    public UUID key;
    public UUID publishingOrganizationKey;
    public String doi;
    public String title;
    public String description;
    public String homepage;
    public GCitation citation;
    public String license;
    public String modified;
    public LocalDate pubDate;
    public List<GEndpoint> endpoints;
    public List<GContact> contacts;
  }
  static class GCitation{
    public String text;
    public String url;
  }
  static class GEndpoint {
    public String type;
    public String url;
  }
  static class GContact extends GAgent{
    public String type;
    public String firstName;
    public String lastName;
    public List<String> position;
    public String organization;
  }
  static class GPublisher extends GAgent{
    public UUID key;
    public String title;
    public String description;
    public String latitude;
    public String longitude;
    public String modified;
    public List<GContact> contacts;
  }
  static abstract class GAgent{
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

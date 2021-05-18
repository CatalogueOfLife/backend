package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.doi.datacite.model.Creator;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.datacite.model.Title;

import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts COL metadata into DataCite metadata.
 * This currently only implements the core basics and waits for the new metadata model to be implemented.
 */
public class DataCiteConverter {
  private final URI portal;
  private final UriBuilder clbBuilder;
  private final UriBuilder clbSourceBuilder;
  private final UriBuilder portalSourceBuilder;

  public DataCiteConverter(WsServerConfig cfg) {
    portal = UriBuilder.fromUri(cfg.portalURI).path("data/metadata").build();
    portalSourceBuilder = UriBuilder.fromUri(cfg.portalURI).path("data/dataset/{key}");
    clbBuilder = UriBuilder.fromUri(cfg.clbURI).path("dataset/{key}/overview");
    clbSourceBuilder = UriBuilder.fromUri(cfg.clbURI).path("dataset/{projectKey}/source/{key}");
  }

  public DoiAttributes release(ArchivedDataset release, boolean latest) {
    DoiAttributes attr = new DoiAttributes(release.getDoi());
    attr.setPublisher("Catalogue of Life");
    attr.setPublicationYear(release.getReleased().getYear());
    attr.setTitles(List.of(new Title(release.getTitle())));
    attr.setCreators(release.getAuthors().stream()
      .map(a -> new Creator(a.getGivenName(), a.getFamilyName()))
      .collect(Collectors.toList())
    );
    attr.setUrl(releaseURI(release.getKey(), latest).toString());
    return attr;
  }

  public DoiAttributes source(ArchivedDataset source, Dataset project, boolean latest) {
    DoiAttributes attr = release(source, latest);
    attr.setUrl(sourceURI(project.getKey(), source.getKey(), latest).toString());
    return attr;
  }

  public URI releaseURI(int datasetKey, boolean latest) {
    return latest ? portal : clbBuilder.build(datasetKey);
  }

  public URI sourceURI(int projectKey, int sourceKey, boolean latest) {
    return latest ? portalSourceBuilder.build(sourceKey) : clbSourceBuilder.build(projectKey, sourceKey);
  }
}
package life.catalogue.doi.service;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.doi.datacite.model.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.ValidationException;
import javax.ws.rs.core.UriBuilder;

/**
 * Converts COL metadata into DataCite metadata.
 * This currently only implements the core basics and waits for the new metadata model to be implemented.
 */
public class DatasetConverter {
  private final URI portal;
  private final UriBuilder clbBuilder;
  private final UriBuilder clbSourceBuilder;
  private final UriBuilder portalSourceBuilder;

  public DatasetConverter(URI portalURI, URI clbURI) {
    portal = UriBuilder.fromUri(portalURI).path("data/metadata").build();
    portalSourceBuilder = UriBuilder.fromUri(portalURI).path("data/dataset/{key}");
    clbBuilder = UriBuilder.fromUri(clbURI).path("dataset/{key}/overview");
    clbSourceBuilder = UriBuilder.fromUri(clbURI).path("dataset/{projectKey}/source/{key}");
  }

  public DoiAttributes release(ArchivedDataset release, boolean latest) {
    DoiAttributes attr = new DoiAttributes(release.getDoi());
    attr.setPublisher("Catalogue of Life");
    if (release.getReleased() != null) {
      attr.setPublicationYear(release.getReleased().getYear());
    }
    attr.setTitles(List.of(new Title(release.getTitle())));
    if (release.getAuthors() != null) {
      attr.setCreators(release.getAuthors().stream()
        .map(a -> new Creator(a.getGivenName(), a.getFamilyName()))
        .collect(Collectors.toList())
      );
    }
    if (release.getEditors() != null) {
      attr.setContributors(release.getEditors().stream()
        .map(a -> new Contributor(a.getGivenName(), a.getFamilyName(), ContributorType.EDITOR))
        .collect(Collectors.toList())
      );
    }
    attr.setUrl(datasetURI(release.getKey(), latest).toString());
    return attr;
  }

  public DoiAttributes source(ArchivedDataset source, Dataset project, boolean latest) {
    DoiAttributes attr = release(source, latest);
    attr.setUrl(sourceURI(project.getKey(), source.getKey(), latest).toString());
    return attr;
  }

  public URI datasetURI(int datasetKey, boolean portal) {
    return portal ? this.portal : clbBuilder.build(datasetKey);
  }

  public URI sourceURI(int projectKey, int sourceKey, boolean portal) {
    return portal ? portalSourceBuilder.build(sourceKey) : clbSourceBuilder.build(projectKey, sourceKey);
  }
}

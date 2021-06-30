package life.catalogue.doi.service;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.doi.datacite.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

/**
 * Converts COL metadata into DataCite metadata.
 * This currently only implements the core basics and waits for the new metadata model to be implemented.
 */
public class DatasetConverter {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetConverter.class);
  private final URI portal;
  private final UriBuilder clbBuilder;
  private final UriBuilder clbSourceBuilder;
  private final UriBuilder portalSourceBuilder;
  private final Function<Integer, User> userByID;

  public DatasetConverter(URI portalURI, URI clbURI, Function<Integer, User> userByID) {
    portal = UriBuilder.fromUri(portalURI).path("data/metadata").build();
    portalSourceBuilder = UriBuilder.fromUri(portalURI).path("data/dataset/{key}");
    clbBuilder = UriBuilder.fromUri(clbURI).path("dataset/{key}");
    clbSourceBuilder = UriBuilder.fromUri(clbURI).path("dataset/{projectKey}/source/{key}");
    this.userByID = userByID;
  }

  /**
   * Populates mandatory attributes:
   *  - title
   *  - publisher
   *  - publicationYear
   *  - creator
   *  - url
   *
   * @param release
   * @param latest if true points to the portal, not checklist bank
   * @return
   */
  public DoiAttributes release(Dataset release, boolean latest, @Nullable DOI project, @Nullable DOI previousVersion) {
    DoiAttributes attr = common(release, latest, previousVersion);
    // other relations
    if (project != null) {
      for (RelationType rt : List.of(RelationType.IS_VERSION_OF, RelationType.IS_DERIVED_FROM)) {
        RelatedIdentifier id = new RelatedIdentifier();
        id.setRelatedIdentifier(project.getDoiName());
        id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
        id.setRelationType(rt);
        attr.getRelatedIdentifiers().add(id);
      }
    }
    if (release.getSource() != null) {
      for (var src : release.getSource()) {
        if (src.getDoi() != null) {
          RelatedIdentifier id = new RelatedIdentifier();
          id.setRelatedIdentifier(src.getDoi().getDoiName());
          id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
          id.setRelationType(RelationType.HAS_PART);
          attr.getRelatedIdentifiers().add(id);
        }
      }
    }
    return attr;
  }

  public DoiAttributes source(Dataset source, @Nullable DOI originalSourceDOI, Dataset release, boolean latest) {
    DoiAttributes attr = common(source, latest, null);
    attr.setUrl(sourceURI(release.getKey(), source.getKey(), latest).toString());
    // release relation
    if (release.getDoi() != null) {
      RelatedIdentifier id = new RelatedIdentifier();
      id.setRelatedIdentifier(release.getDoi().getDoiName());
      id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
      id.setRelationType(RelationType.IS_PART_OF);
      attr.getRelatedIdentifiers().add(id);
    }
    if (originalSourceDOI != null) {
      RelatedIdentifier id = new RelatedIdentifier();
      id.setRelatedIdentifier(originalSourceDOI.getDoiName());
      id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
      id.setRelationType(RelationType.IS_DERIVED_FROM);
      attr.getRelatedIdentifiers().add(id);
    }
    // source relations
    if (source.getSource() != null) {
      for (var src : source.getSource()) {
        if (src.getDoi() != null) {
          RelatedIdentifier id = new RelatedIdentifier();
          id.setRelatedIdentifier(src.getDoi().getDoiName());
          id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
          id.setRelationType(RelationType.IS_DERIVED_FROM);
          attr.getRelatedIdentifiers().add(id);
        }
      }
    }
    return attr;
  }

  private DoiAttributes common(Dataset release, boolean latest, @Nullable DOI previousVersion) {
    DoiAttributes attr = new DoiAttributes(release.getDoi());
    // title
    attr.setTitles(List.of(new Title(release.getTitle())));
    // publisher
    if (release.getPublisher() != null) {
      attr.setPublisher(release.getPublisher().getName());
    }
    // PublicationYear
    if (release.getIssued() != null) {
      attr.setPublicationYear(release.getIssued().getYear());
    } else {
      LOG.warn("No release date given. Use today instead");
      attr.setPublicationYear(LocalDate.now().getYear());
    }
    // version
    attr.setVersion(release.getVersion());
    // creator
    if (release.getCreator() != null) {
      attr.setCreators(release.getCreator().stream()
                              .map(a -> {
                                if (a.isPerson()) {
                                  return new Creator(a.getGiven(), a.getFamily(), a.getOrcid());
                                }
                                return new Creator(a.getName(), NameType.ORGANIZATIONAL);
                              })
                              .collect(Collectors.toList())
      );
    } else {
      LOG.warn("No authors given. Use dataset creator instead");
      User user = userByID.apply(release.getCreatedBy());
      Creator creator;
      if (user.getLastname() != null) {
        creator = new Creator(user.getFirstname(), user.getLastname());
      } else {
        creator = new Creator(user.getUsername(), NameType.PERSONAL);
      }
      creator.setNameIdentifier(List.of(NameIdentifier.gbif(user.getUsername())));
      attr.setCreators(List.of(creator));
    }
    // contributors
    List<Contributor> contribs = new ArrayList<>();
    if (release.getContributor() != null) {
      contribs.addAll(release.getContributor().stream()
                             .map(a -> agent2Contributor(a, null))
                             .collect(Collectors.toList())
      );
    }
    if (release.getEditor() != null) {
      contribs.addAll(release.getEditor().stream()
                             .map(a -> agent2Contributor(a, ContributorType.EDITOR))
                             .collect(Collectors.toList())
      );
    }
    attr.setContributors(contribs);
    // url
    attr.setUrl(datasetURI(release.getKey(), latest).toString());
    // ids
    if (release.getIdentifier() != null) {
      List<Identifier> ids = new ArrayList<>();
      for (var entry : release.getIdentifier().entrySet()) {
        Identifier id = null;
        // we can only map DOI, URL or URNs
        var doi = DOI.parse(entry.getValue());
        if (doi.isPresent()) {
          id = new Identifier();
          id.setIdentifier(doi.toString());
          id.setIdentifierType(Identifier.DOI_TYPE);
        } else if (entry.getValue().toLowerCase().startsWith("urn:")) {
          id = new Identifier();
          id.setIdentifier(entry.getValue());
          id.setIdentifierType("URN");
        } else if (entry.getValue().toLowerCase().startsWith("http")) {
          id = new Identifier();
          id.setIdentifier(entry.getValue());
          id.setIdentifierType("URL");
        }

        if (id != null) {
          ids.add(id);
        }
      }
      attr.setIdentifiers(ids);
    }
    // relations
    List<RelatedIdentifier> ids = new ArrayList<>();
    if (previousVersion != null) {
      RelatedIdentifier id = new RelatedIdentifier();
      id.setRelatedIdentifier(previousVersion.getDoiName());
      id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
      id.setRelationType(RelationType.IS_NEW_VERSION_OF);
      ids.add(id);
    }
    attr.setRelatedIdentifiers(ids);
    return attr;
  }

  Contributor agent2Contributor(Agent a, @Nullable ContributorType type) {
    if (type == null) {
      if (a.getNote() != null) {
        try {
          type = ContributorType.fromValue(a.getNote());
        } catch (IllegalArgumentException e) {
          // TODO: try known ones
        }
      }
      if (type == null) {
        type = ContributorType.OTHER;
      }
    }
    if (a.isPerson()) {
      return new Contributor(a.getGiven(), a.getFamily(), a.getOrcid(), type);
    }
    return new Contributor(a.getName(), NameType.ORGANIZATIONAL, type);
  }


  public URI datasetURI(int datasetKey, boolean portal) {
    return portal ? this.portal : clbBuilder.build(datasetKey);
  }

  public URI sourceURI(int projectKey, int sourceKey, boolean portal) {
    return portal ? portalSourceBuilder.build(sourceKey) : clbSourceBuilder.build(projectKey, sourceKey);
  }
}

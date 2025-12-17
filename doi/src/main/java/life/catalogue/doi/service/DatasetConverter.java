package life.catalogue.doi.service;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.doi.datacite.model.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.UriBuilder;

/**
 * Converts COL metadata into DataCite metadata.
 * This currently only implements the core basics and waits for the new metadata model to be implemented.
 */
public class DatasetConverter {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetConverter.class);
  private final URI portal;
  private final UriBuilder clbBuilder;
  private final UriBuilder clbSourceBuilder;
  private final UriBuilder clbAttemptBuilder;
  private final UriBuilder portalSourceBuilder;
  private final Function<Integer, User> userByID;

  public DatasetConverter(URI portalURI, URI clbURI, Function<Integer, User> userByID) {
    portal = UriBuilder.fromUri(portalURI).path("data/metadata").build();
    portalSourceBuilder = UriBuilder.fromUri(portalURI).path("data/dataset/{key}");
    clbBuilder = UriBuilder.fromUri(clbURI).path("dataset/{key}");
    clbSourceBuilder = UriBuilder.fromUri(clbURI).path("dataset/{projectKey}/source/{key}");
    clbAttemptBuilder= UriBuilder.fromUri(clbURI).path("dataset/{datasetKey}/imports/{attempt}");
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
   * @param portal if true points to the COL portal, not checklist bank
   * @return
   */
  public DoiAttributes release(Dataset release, boolean portal, @Nullable DOI previousVersion, @Nullable DOI nextVersion) {
    DoiAttributes attr = common(release.getDoi(), release, previousVersion, nextVersion);
    attr.setUrl(releaseURI(release.getKey(), portal).toString());
    return attr;
  }

  public DoiAttributes dataset(Dataset d) {
    DoiAttributes attr = common(d.getDoi(), d, null, null);
    attr.setUrl(datasetURI(d.getKey()).toString());
    return attr;
  }

  public DoiAttributes datasetAttempt(Dataset d, @Nullable DOI previousAttempt, @Nullable DOI nextAttempt) {
    DoiAttributes attr = common(d.getVersionDoi(), d, previousAttempt, nextAttempt);
    attr.setUrl(attemptURI(d.getKey(), d.getAttempt()).toString());
    return attr;
  }

  /**
   * Does not set any URL !!!
   * @param d
   * @param previousVersion
   * @param nextVersion
   * @return
   */
  private DoiAttributes common(DOI doi, Dataset d, @Nullable DOI previousVersion, @Nullable DOI nextVersion) {
    Preconditions.checkNotNull(doi, "DOI required");
    DoiAttributes attr = new DoiAttributes(doi);
    // title
    attr.setTitles(List.of(new Title(d.getTitle())));
    // publisher
    if (d.getPublisher() != null) {
      attr.setPublisher(d.getPublisher().getName());
    } else {
      // this is required !!!
      LOG.info("No required DOI publisher given, use ChecklistBank instead");
      attr.setPublisher("ChecklistBank");
    }
    // PublicationYear
    if (d.getIssued() != null) {
      attr.setPublicationYear(d.getIssued().getYear());
      // add issued data if missing - important for DataCite
    } else if (d.getCreated() != null) {
      attr.setPublicationYear(d.getCreated().getYear());
    } else {
      LOG.warn("No issued or created date given. Use today instead");
      attr.setPublicationYear(LocalDate.now().getYear());
    }
    // version
    attr.setVersion(d.getVersion());
    // creator
    if (d.getCreator() != null && !d.getCreator().isEmpty()) {
      attr.setCreators(toCreators(d.getCreator()));
    } else if (d.getEditor() != null && !d.getEditor().isEmpty()) {
      LOG.info("No authors given. Use dataset editors instead");
      attr.setCreators(toCreators(d.getEditor()));
    } else {
      LOG.info("No authors given. Use dataset creator instead");
      User user = userByID.apply(d.getCreatedBy());
      Creator creator;
      if (user.getLastname() != null) {
        creator = new Creator(user.getFirstname(), user.getLastname());
      } else {
        creator = new Creator(user.getUsername(), NameType.PERSONAL);
      }
      creator.setNameIdentifiers(List.of(NameIdentifier.gbif(user.getUsername())));
      attr.setCreators(List.of(creator));
    }
    // contributors
    List<Contributor> contribs = new ArrayList<>();
    if (d.getContributor() != null) {
      contribs.addAll(d.getContributor().stream()
                             .map(a -> agent2Contributor(a, null))
                             .filter(Objects::nonNull)
                             .collect(Collectors.toList())
      );
    }
    if (d.getEditor() != null) {
      contribs.addAll(d.getEditor().stream()
                             .map(a -> agent2Contributor(a, ContributorType.EDITOR))
                             .filter(Objects::nonNull)
                             .collect(Collectors.toList())
      );
    }
    attr.setContributors(contribs);
    // alternative ids
    if (d.getIdentifier() != null) {
      List<Identifier> ids = new ArrayList<>();
      for (var did : d.getIdentifier()) {
        Identifier id = new Identifier();
        id.setIdentifier(did.getId());
        id.setIdentifierType(did.getScope());
        ids.add(id);
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
    if (nextVersion != null) {
      RelatedIdentifier id = new RelatedIdentifier();
      id.setRelatedIdentifier(nextVersion.getDoiName());
      id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
      id.setRelationType(RelationType.IS_PREVIOUS_VERSION_OF);
      ids.add(id);
    }
    // sources
    if (d.getSource() != null) {
      for (var src : d.getSource()) {
        if (src.getDoi() != null) {
          RelatedIdentifier id = new RelatedIdentifier();
          id.setRelatedIdentifier(src.getDoi().getDoiName());
          id.setRelatedIdentifierType(RelatedIdentifierType.DOI);
          id.setRelationType(RelationType.IS_DERIVED_FROM);
          ids.add(id);
        }
      }
    }
    attr.setRelatedIdentifiers(ids);
    return attr;
  }

  static <T extends Creator> void  addAffiliation(T c, Agent a) {
    if (!StringUtils.isBlank(a.getOrganisation())) {
      c.setAffiliation(List.of(new Affiliation(a)));
    }
  }

  public List<Creator> toCreators(List<Agent> agents) {
    return agents.stream()
                 .map(a -> {
                   if (a.isPerson()) {
                     Creator c = new Creator(StringUtils.trimToNull(a.getGiven()), StringUtils.trimToNull(a.getFamily()), StringUtils.trimToNull(a.getOrcid()));
                     addAffiliation(c, a);
                     return c;
                   }
                   return new Creator(a.getName(), NameType.ORGANIZATIONAL);
                 })
                 .collect(Collectors.toList());
  }

  public List<Contributor> toContributor(List<Agent> agents, @Nullable ContributorType type) {
    return agents.stream()
                 .map(a -> agent2Contributor(a, type))
                 .collect(Collectors.toList());
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
      Contributor c = new Contributor(StringUtils.trimToNull(a.getGiven()), StringUtils.trimToNull(a.getFamily()), StringUtils.trimToNull(a.getOrcid()), type);
      addAffiliation(c, a);
      return c;
    } else if (a.getName() != null) {
      return new Contributor(a.getName(), NameType.ORGANIZATIONAL, type);
    }
    return null;
  }

  public URI releaseURI(int datasetKey, boolean portal) {
    return portal ? this.portal : datasetURI(datasetKey);
  }

  public URI datasetURI(int datasetKey) {
    return clbBuilder.build(datasetKey);
  }

  public URI sourceURI(int projectKey, int sourceKey) {
    return clbSourceBuilder.build(projectKey, sourceKey);
  }

  public URI attemptURI(int datasetKey, int attempt) {
    return clbAttemptBuilder.build(datasetKey, attempt);
  }
}

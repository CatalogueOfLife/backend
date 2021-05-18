package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;
import life.catalogue.common.id.IdConverter;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.datacite.model.EventType;

import java.net.URI;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.Map;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class DataCiteService implements DoiService {
  private static final Logger LOG = LoggerFactory.getLogger(DataCiteService.class);
  private static final String DATASET_PATH = "ds";
  private static final String DOWNLOAD_PATH = "dl";

  private final DoiConfig cfg;
  private final WebTarget dois;
  private final String auth;


  public DataCiteService(DoiConfig cfg, Client client) {
    LOG.info("Setting up DataCite DOI service with user {} and API {}", cfg.username, cfg.api);
    Preconditions.checkArgument(cfg.api.startsWith("https"), "SSL required to use the DataCite API");
    this.cfg = cfg;
    dois = client.target(UriBuilder.fromUri(cfg.api).path("dois").build());
    auth = BasicAuthenticator.basicAuthentication(cfg.username, cfg.password);
  }

  Invocation.Builder request(){
    return request(dois);
  }

  Invocation.Builder request(DOI doi){
    return request(dois.path(doi.getDoiName()));
  }

  Invocation.Builder request(WebTarget uri){
    return uri
      .request(MediaType.APPLICATION_JSON_TYPE)
      .header(HttpHeaders.AUTHORIZATION, auth);
  }

  @Override
  public DOI fromDataset(int datasetKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey);
    return new DOI(cfg.prefix, suffix);
  }

  @Override
  public DOI fromDatasetSource(int datasetKey, int sourceKey) {
    String suffix = DATASET_PATH + IdConverter.LATIN29.encode(datasetKey) + "-" + IdConverter.LATIN29.encode(sourceKey);
    return new DOI(cfg.prefix, suffix);
  }

  @Override
  public DoiAttributes resolve(DOI doi) {
    LOG.debug("retrieve {}", doi);
    try {
      return request(doi).get(DataCiteWrapper.class).getAttributes();

    } catch (NotFoundException e) {
      return null;
    }
  }

  private static DoiAttributes addRequired(DoiAttributes attr) {
    attr.setTypes(Map.of("resourceTypeGeneral", "Dataset",  "resourceType", "Dataset"));
    if (attr.getPublisher() == null) {
      attr.setPublisher("GBIF");
    }
    if (attr.getPublicationYear() == null) {
      attr.setPublicationYear(Year.now().get(ChronoField.YEAR));
    }
    return attr;
  }

  @Override
  public void create(DOI doi) throws DoiException {
    LOG.info("create new draft DOI {}", doi);
    DoiAttributes attr = addRequired(new DoiAttributes(doi));
    try {
      var resp = request().post(Entity.json(new DataCiteWrapper(attr)));
      if (resp.getStatus() != 201) {
        if (resp.getEntity() != null) {
          String message = resp.readEntity(String.class);
          throw new DoiHttpException(resp.getStatus(), doi, message);
        }
        throw new DoiHttpException(resp.getStatus(), doi);
      }

    } catch (RuntimeException e) {
      throw new DoiException(doi, e);
    }
  }

  @Override
  public boolean delete(DOI doi) throws DoiException {
    Preconditions.checkNotNull(doi);
    Preconditions.checkArgument(doi.isComplete(), "DOI suffix required");
    LOG.info("delete DOI {}", doi);
    try {
      var attr = resolve(doi);
      if (attr != null) {
        if (DoiState.DRAFT == attr.getState()) {
          var resp = request(doi).delete();
          if (resp.getStatus() != 200 && resp.getStatus() != 204) {
            if (resp.getEntity() != null) {
              String message = resp.readEntity(String.class);
              throw new DoiHttpException(resp.getStatus(), doi, message);
            }
            throw new DoiHttpException(resp.getStatus(), doi);
          }
        } else {
          // hide
          DoiAttributes attr2 = new DoiAttributes(doi);
          attr2.setEvent(EventType.HIDE);
          update(attr2);
          return false;
        }
      }

    } catch (RuntimeException e) {
      throw new DoiException(doi, e);
    }
    return true;
  }

  @Override
  public void publish(DOI doi) throws DoiException {
    LOG.info("publish DOI {}", doi);
    DoiAttributes attr = new DoiAttributes(doi);
    attr.setEvent(EventType.PUBLISH);
    update(attr);
  }

  @Override
  public void update(DoiAttributes attr) throws DoiException {
    Preconditions.checkNotNull(attr.getDoi());
    Preconditions.checkArgument(attr.getDoi().isComplete(), "DOI suffix required");

    LOG.info("update metadata for DOI {}", attr);
    try {
      var resp = request(attr.getDoi()).put(Entity.json(new DataCiteWrapper(addRequired(attr))));

      if (resp.getStatus() != 200 && resp.getStatus() != 204) {
        if (resp.getEntity() != null) {
          String message = resp.readEntity(String.class);
          throw new DoiHttpException(resp.getStatus(), attr.getDoi(), message);
        }
        throw new DoiHttpException(resp.getStatus(), attr.getDoi());
      }

    } catch (RuntimeException e) {
      throw new DoiException(attr.getDoi(), e);
    }
  }

  @Override
  public void update(DOI doi, URI target) throws DoiException {
    DoiAttributes attr = new DoiAttributes(doi);
    attr.setUrl(target.toString());
    update(attr);
  }

}

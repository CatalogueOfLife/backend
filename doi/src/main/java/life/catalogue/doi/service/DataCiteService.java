package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;
import life.catalogue.doi.datacite.model.DoiAttributes;

import java.net.URI;

import javax.validation.constraints.NotNull;
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

  private final DoiConfig cfg;
  private final Client client;
  private final WebTarget dois;
  private final MediaType MEDIA_TYPE = new MediaType("application", "vnd.api+json");
  private final String auth;


  public DataCiteService(DoiConfig cfg, Client client) {
    Preconditions.checkArgument(cfg.api.startsWith("https"), "SSL required to use the DataCite API");
    this.cfg = cfg;
    this.client = client;
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
  public @NotNull DoiAttributes resolve(DOI doi) throws NotFoundException {
    LOG.debug("retrieve {}", doi);
    try {
      return request(doi).get(DataCiteWrapper.class).getAttributes();

    } catch (RuntimeException e) {
      throw e;
    }
  }

  @Override
  public void create(DOI doi) throws DoiException {
    LOG.info("create new draft DOI {}", doi);
    DoiAttributes attr = new DoiAttributes(doi);
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
    LOG.debug("delete DOI {}", doi);
    try {
      var resp = request(doi).delete();

      if (resp.getStatus() != 200 && resp.getStatus() != 204) {
        if (resp.getEntity() != null) {
          String message = resp.readEntity(String.class);
          throw new DoiHttpException(resp.getStatus(), doi, message);
        }
        throw new DoiHttpException(resp.getStatus(), doi);
      } else {
        String message = resp.readEntity(String.class);
        System.out.println(message);
      }

    } catch (RuntimeException e) {
      throw new DoiException(doi, e);
    }

    return false;
  }

  @Override
  public void update(DoiAttributes attr) throws DoiException {
    Preconditions.checkNotNull(attr.getDoi());
    Preconditions.checkArgument(attr.getDoi().isComplete(), "DOI suffix required");

    LOG.debug("update metadata for DOI {}", attr);
    try {
      var resp = request(attr.getDoi()).put(Entity.json(new DataCiteWrapper(attr)));

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

package life.catalogue.doi.service;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DOI;
import life.catalogue.doi.datacite.model.DoiAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

public class DataCiteService implements DoiService {
  private static final Logger LOG = LoggerFactory.getLogger(DataCiteService.class);

  private final DoiConfig cfg;
  private final Client client;
  private final WebTarget dois;


  public DataCiteService(DoiConfig cfg, Client client) {
    this.cfg = cfg;
    dois = client.target(UriBuilder.fromUri(cfg.api).path("dois").build());
    this.client = client;
  }

  @Override
  public @NotNull DoiAttributes resolve(DOI doi) throws NotFoundException {
    LOG.debug("retrieve {}", doi);
    try {
      DataCiteWrapper data = dois.path(doi.getDoiName())
        .request()
        .accept(MediaType.APPLICATION_JSON_TYPE)
        .get(DataCiteWrapper.class);
      if (data == null) {
        throw NotFoundException.notFound(doi);
      }
      return data.getAttributes();

    } catch (RuntimeException e) {
      throw e;
    }
  }

  @Override
  public void create(DOI doi) throws DoiException {

  }

  @Override
  public boolean delete(DOI doi) throws DoiException {
    return false;
  }

  @Override
  public void update(DoiAttributes doi) throws DoiException {

  }

  @Override
  public void update(DOI doi, URI target) throws DoiException {

  }
}

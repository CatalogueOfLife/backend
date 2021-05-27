package life.catalogue.doi.service;

import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DOI;
import life.catalogue.common.id.IdConverter;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.datacite.model.DoiState;
import life.catalogue.doi.datacite.model.EventType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
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
  private final Mailer mailer;
  private final String onErrorTo;
  private final String onErrorFrom;


  public DataCiteService(DoiConfig cfg, Client client) {
    this(cfg, client, null, null, null);
  }

  public DataCiteService(DoiConfig cfg, Client client, @Nullable Mailer mailer, @Nullable String onErrorTo, @Nullable String onErrorFrom) {
    LOG.info("Setting up DataCite DOI service with user {} and API {}", cfg.username, cfg.api);
    Preconditions.checkArgument(cfg.api.startsWith("https"), "SSL required to use the DataCite API");
    this.cfg = cfg;
    dois = client.target(UriBuilder.fromUri(cfg.api).path("dois").build());
    auth = BasicAuthenticator.basicAuthentication(cfg.username, cfg.password);
    this.mailer = mailer;
    this.onErrorTo = onErrorTo;
    this.onErrorFrom = onErrorFrom;
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

  @Override
  public void create(DoiAttributes attr) throws DoiException {
    LOG.info("create new draft DOI {}", attr.getDoi());
    try {
      DataCiteWrapper data = new DataCiteWrapper(attr);
      LOG.debug("DOI {} JSON: {}", attr.getDoi(), ApiModule.MAPPER.writeValueAsString(data));
      var resp = request().post(Entity.json(data));
      if (resp.getStatus() != 201) {
        if (resp.getEntity() != null) {
          String message = resp.readEntity(String.class);
          throw new DoiHttpException(resp.getStatus(), attr.getDoi(), message);
        }
        throw new DoiHttpException(resp.getStatus(), attr.getDoi());
      }

    } catch (RuntimeException | JsonProcessingException e) {
      throw new DoiException(attr.getDoi(), e);
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
      DataCiteWrapper data = new DataCiteWrapper(attr);
      LOG.debug("DOI {} JSON: {}", attr.getDoi(), ApiModule.MAPPER.writeValueAsString(data));
      var resp = request(attr.getDoi()).put(Entity.json(data));

      if (resp.getStatus() != 200 && resp.getStatus() != 204) {
        if (resp.getEntity() != null) {
          String message = resp.readEntity(String.class);
          throw new DoiHttpException(resp.getStatus(), attr.getDoi(), message);
        }
        throw new DoiHttpException(resp.getStatus(), attr.getDoi());
      }

    } catch (RuntimeException | JsonProcessingException e) {
      throw new DoiException(attr.getDoi(), e);
    }
  }

  @Override
  public void update(DOI doi, URI target) throws DoiException {
    DoiAttributes attr = new DoiAttributes(doi);
    attr.setUrl(target.toString());
    update(attr);
  }

  @Override
  public void notifyException(DOI doi, String action, Exception e) {
    if (mailer != null) {
      StringWriter sw = new StringWriter();
      sw.write(action);
      sw.write(" for DOI " + doi + " has failed.");
      if (e != null) {
        sw.write(" " + e.getClass().getSimpleName()+":\n\n");
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
      } else {
        sw.write(".\n");
      }

      Email mail = EmailBuilder.startingBlank()
        .to(onErrorTo)
        .from(onErrorFrom)
        .withSubject(String.format("DOI error %s", doi))
        .withPlainText(sw.toString())
        .buildEmail();
      mailer.sendMail(mail, true);
    }
  }
}

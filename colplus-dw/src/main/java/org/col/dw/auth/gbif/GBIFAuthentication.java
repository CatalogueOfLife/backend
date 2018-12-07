package org.col.dw.auth.gbif;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.HttpHeaders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.api.model.ColUser;
import org.col.api.vocab.Country;
import org.col.dw.auth.AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication provider that delegates authentication to the BASIC scheme using the
 * GBIF id service via http.
 * <p>
 * An HttpClient MUST be set before the service is used.
 */
public class GBIFAuthentication implements AuthenticationProvider {
  private static final Logger LOG = LoggerFactory.getLogger(GBIFAuthentication.class);
  private static final String SETTING_COUNTRY = "country";
  private static final String SYS_SETTING_ORCID = "auth.orcid.id";
  
  private final URI loginUri;
  private final URI userUri;
  private CloseableHttpClient http;
  private final GbifTrustedAuth gbifAuth;
  private final static ObjectMapper OM = configure(new ObjectMapper());
  
  private static ObjectMapper configure(ObjectMapper mapper) {
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }
  
  public GBIFAuthentication(GBIFAuthenticationFactory cfg) {
    gbifAuth = new GbifTrustedAuth(cfg);
    try {
      loginUri = new URI(cfg.api).resolve("user/login");
      userUri = new URI(cfg.api).resolve("admin/user/");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    LOG.info("Accessing GBIF user accounts at {}", cfg.api);
  }
  
  @Override
  public void setClient(CloseableHttpClient http) {
    this.http = http;
  }
  

  
  @Override
  public Optional<ColUser> authenticate(String username, String password) {
    if (authenticateGBIF(username, password)) {
      // GBIF authentication does not provide us with the full user, we need to look it up again
      ColUser user = getFullGbifUser(username);
      if (user == null) {
        user = new ColUser();
        user.setUsername(username);
        user.setFirstname("?");
        user.setLastname("?");
      }
      return Optional.of(user);
    } else {
      LOG.debug("GBIF authentication failed for user {}", username);
    }
    return Optional.empty();
  }
  
  /**
   * Checks if Basic Auth against GBIF API is working.
   * We avoid the native httpclient Basic authentication which uses ASCII to encode the password
   * into Base64 octets. But GBIF requires ISO_8859_1! See:
   * https://github.com/gbif/registry/issues/67
   * https://stackoverflow.com/questions/7242316/what-encoding-should-i-use-for-http-basic-authentication
   * https://tools.ietf.org/html/rfc7617#section-2.1
   */
  @VisibleForTesting
  boolean authenticateGBIF(String username, String password) {
    HttpGet get = new HttpGet(loginUri);
    get.addHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(username, password));
    try (CloseableHttpResponse resp = http.execute(get)) {
      LOG.debug("GBIF authentication response for {}: {}", username, resp);
      return resp.getStatusLine().getStatusCode() == 200;
    } catch (Exception e) {
      LOG.error("GBIF BasicAuth error for user {}", username, e);
    }
    return false;
  }
  
  public static String basicAuthHeader(String username, String password) {
    String cred = username + ":" + password;
    String base64 = BaseEncoding.base64().encode(cred.getBytes(StandardCharsets.UTF_8));
    return "Basic " + base64;
  }
  
  @VisibleForTesting
  ColUser getFullGbifUser(String username) {
    HttpGet get = new HttpGet(userUri.resolve(username));
    gbifAuth.signRequest(get);
    try (CloseableHttpResponse resp = http.execute(get)) {
      if (resp.getStatusLine().getStatusCode() == 200) {
        return fromJson(resp.getEntity().getContent());
      }
      LOG.info("No success retrieving GBIF user {}: {}", username, resp.getStatusLine());
    } catch (Exception e) {
      LOG.info("Failed to retrieve GBIF user {}", username, e);
    }
    return null;
  }
  
  @VisibleForTesting
  static ColUser fromJson(InputStream json) throws IOException {
    GUser gbif = OM.readValue(json, GUser.class);
    ColUser user = new ColUser();
    user.setUsername(gbif.userName);
    user.setFirstname(gbif.firstName);
    user.setLastname(gbif.lastName);
    user.setEmail(gbif.email);
    if (gbif.roles != null) {
      for (String r : gbif.roles) {
        ColUser.Role role = colRole(r);
        if (role != null) user.addRole(role);
      }
    }
    if (gbif.settings != null && gbif.settings.containsKey(SETTING_COUNTRY)) {
      Country.fromIsoCode(gbif.settings.get(SETTING_COUNTRY)).ifPresent(user::setCountry);
    }
    if (gbif.systemSettings != null) {
      user.setOrcid(gbif.systemSettings.getOrDefault(SYS_SETTING_ORCID, null));
    }
    return user;
  }
  
  private static ColUser.Role colRole(String gbif) {
    if ("col_admin".equalsIgnoreCase(gbif)) return ColUser.Role.ADMIN;
    if ("col_editor".equalsIgnoreCase(gbif)) return ColUser.Role.EDITOR;
    if ("user".equalsIgnoreCase(gbif)) return ColUser.Role.USER;
    return null;
  }
  
  static class GUser {
    public Integer key;
    public String userName;
    public String firstName;
    public String lastName;
    public String email;
    public List<String> roles;
    public Map<String, String> settings;
    public Map<String, String> systemSettings;
    public Boolean challengeCodePresent;
  }
}

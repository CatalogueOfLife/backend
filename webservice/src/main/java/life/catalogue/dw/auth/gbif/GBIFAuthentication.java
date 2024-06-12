package life.catalogue.dw.auth.gbif;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Country;
import life.catalogue.dw.auth.AuthenticationProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.ws.rs.core.HttpHeaders;

import org.apache.hc.core5.http.client.methods.CloseableHttpResponse;
import org.apache.hc.core5.http.client.methods.HttpGet;
import org.apache.hc.core5.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;

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
  private final String verificationUser;
  
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
    verificationUser = cfg.verificationUser;
    LOG.info("GBIF authentication created using API at {}", cfg.api);
  }
  
  @Override
  public void setClient(CloseableHttpClient http) {
    this.http = http;
  }
  

  public void verifyGbifAuth() {
    // test gbif auth configs
    if (verificationUser != null && getFullGbifUser(verificationUser) == null) {
      LOG.error("Failed to retrieve user {} to verify GBIF authentication", verificationUser);
      throw new IllegalStateException("Failed to verify GBIF authentication");
    }
  }
  
  @Override
  public Optional<User> authenticate(String username, String password) {
    String usernameStrict = authenticateGBIF(username, password);
    if (usernameStrict != null) {
      // GBIF authentication does not provide us with the full user, we need to look it up again
      User user = getFullGbifUser(usernameStrict);
      return Optional.ofNullable(user);
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
   *
   * @return the username or null if auth failed
   */
  @VisibleForTesting
  String authenticateGBIF(String username, String password) {
    HttpGet get = new HttpGet(loginUri);
    get.addHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(username, password));
    try (CloseableHttpResponse resp = http.execute(get)) {
      if (resp.getStatusLine().getStatusCode() == 200) {
        // we retrieve the username from the response as we can also authenticate with the email address
        GUser gbif = OM.readValue(resp.getEntity().getContent(), GUser.class);
        if (gbif != null && gbif.userName != null && !username.equalsIgnoreCase(gbif.userName)) {
          LOG.debug("GBIF user for {} is {}", username, gbif.userName);
          username = gbif.userName;
        }
        LOG.debug("GBIF authentication response for {}: {}", username, resp);
        return username;
      }
    } catch (Exception e) {
      LOG.error("GBIF BasicAuth error for user {}", username, e);
    }
    return null;
  }
  
  public static String basicAuthHeader(String username, String password) {
    String cred = username + ":" + password;
    String base64 = BaseEncoding.base64().encode(cred.getBytes(StandardCharsets.UTF_8));
    return "Basic " + base64;
  }
  
  @VisibleForTesting
  User getFullGbifUser(String username) {
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
  static User fromJson(InputStream json) throws IOException {
    GUser gbif = OM.readValue(json, GUser.class);
    User user = new User();
    user.setUsername(gbif.userName);
    user.setFirstname(gbif.firstName);
    user.setLastname(gbif.lastName);
    user.setEmail(gbif.email);
    if (gbif.roles != null) {
      for (String r : gbif.roles) {
        User.Role role = colRole(r);
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
  
  private static User.Role colRole(String gbif) {
    if ("col_admin".equalsIgnoreCase(gbif)) return User.Role.ADMIN;
    if ("col_editor".equalsIgnoreCase(gbif)) return User.Role.EDITOR;
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

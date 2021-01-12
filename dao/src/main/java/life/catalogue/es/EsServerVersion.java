package life.catalogue.es;

import java.util.Arrays;
import java.util.HashMap;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import static life.catalogue.es.EsUtil.executeRequest;
import static life.catalogue.es.EsUtil.readFromResponse;

public class EsServerVersion {

  private static EsServerVersion instance;

  public static EsServerVersion getInstance(RestClient client) {
    if (instance == null) {
      instance = new EsServerVersion(client);
    }
    return instance;
  }

  private final int[] version;
  private final String versionString;

  private EsServerVersion(RestClient client) {
    Request request = new Request("GET", "/");
    Response response = executeRequest(client, request);
    HashMap<String, String> data = readFromResponse(response, "version");
    versionString = data.get("number").replace("-SNAPSHOT", "");
    version = Arrays.stream(versionString.split("\\.")).mapToInt(Integer::parseInt).toArray();
  }

  public boolean is(int majorVersion) {
    return version[0] == majorVersion;
  }

  public boolean is(int majorVersion, int minorVersion) {
    return is(majorVersion) && version[1] == minorVersion;
  }

  public boolean is(int majorVersion, int minorVersion, int patchVersion) {
    return is(majorVersion, minorVersion) && version[2] == patchVersion;
  }

  public boolean is(int[] version) {
    int idx = 0;
    for (int v : version) {
      if (this.version.length <= idx || this.version[idx] != v) {
        return false;
      }
      idx++;
    }
    return true;
  }

  /**
   * @return semantic version as int array
   */
  public int[] getVersion() {
    return version;
  }

  public String getVersionString() {
    return versionString;
  }
}

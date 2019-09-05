package org.col.es;

import java.io.IOException;
import java.util.HashMap;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import static org.col.es.EsUtil.executeRequest;
import static org.col.es.EsUtil.readFromResponse;

public class EsServerVersion {

  private static EsServerVersion instance;

  public static EsServerVersion getInstance(RestClient client) throws IOException {
    if (instance == null) {
      instance = new EsServerVersion(client);
    }
    return instance;
  }

  private final int majorVersion;
  private final String versionString;

  private EsServerVersion(RestClient client) throws IOException {
    Request request = new Request("GET", "/");
    Response response = executeRequest(client, request);
    HashMap<String, String> data = readFromResponse(response, "version");
    versionString = data.get("number");
    majorVersion = Integer.parseInt(versionString.substring(0, versionString.indexOf(".")));
  }

  public boolean is(int majorVersion) {
    return this.majorVersion == majorVersion;
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public String getVersionString() {
    return versionString;
  }

}

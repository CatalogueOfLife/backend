package life.catalogue.dw.metrics;


import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.conn.socket.ConnectionSocketFactory;
import org.apache.hc.core5.http.protocol.HttpRequestExecutor;

import com.codahale.metrics.httpclient.InstrumentedHttpClientConnectionManager;
import com.google.common.annotations.VisibleForTesting;

import io.dropwizard.setup.Environment;

/**
 * Http client builder that overrides the default Dropwizard one to not use a user agent with version as the name
 * for metric names to gain a more stable metric naming across builds.
 */
public class HttpClientBuilder extends io.dropwizard.client.HttpClientBuilder {

  public HttpClientBuilder(Environment environment) {
    super(environment);
  }

  @VisibleForTesting
  static String simpleName(String name) {
    if (name.contains("/")) {
      return name.substring(0, name.indexOf('/'));
    }
    return name;
  }

  @Override
  protected HttpRequestExecutor createRequestExecutor(String name) {
    return super.createRequestExecutor(simpleName(name));
  }

  @Override
  protected InstrumentedHttpClientConnectionManager createConnectionManager(Registry<ConnectionSocketFactory> registry, String name) {
    return super.createConnectionManager(registry, simpleName(name));
  }
}

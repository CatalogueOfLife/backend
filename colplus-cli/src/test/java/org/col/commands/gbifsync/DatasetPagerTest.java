package org.col.commands.gbifsync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.col.commands.config.GbifConfig;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.rx.Rx;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.ws.rs.client.ClientBuilder;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 */
@Ignore("GBIF service needs to be mocked - this uses live services")
public class DatasetPagerTest {
  @Test
  public void next() throws Exception {
    SLF4JBridgeHandler.install();

    final JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

    RxClient<RxCompletionStageInvoker> client = Rx.from(ClientBuilder.newClient(cfg), RxCompletionStageInvoker.class);

    DatasetPager pager = new DatasetPager(client, new GbifConfig());
    while(pager.hasNext()) {
      pager.next().forEach(System.out::println);
    }
  }

}
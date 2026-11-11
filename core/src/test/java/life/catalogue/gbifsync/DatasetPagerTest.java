package life.catalogue.gbifsync;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.GbifConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.Test;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


/**
 *
 */
public class DatasetPagerTest {

  @Test
  @Disabled @Ignore("GBIF service needs to be mocked - this uses live services")
  public void datasetPager() throws Exception {
    final JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider(ApiModule.MAPPER);
    ClientConfig cfg = new ClientConfig(jacksonJsonProvider);
    cfg.register(new LoggingFeature(Logger.getLogger(getClass().getName()), Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024));

    final Client client = ClientBuilder.newClient(cfg);

    DatasetPager pager = new DatasetPager(client, new GbifConfig(), LocalDate.of(2023, 5, 23));

    // test VASCAN
    var vascan = pager.get(UUID.fromString("3f8a1297-3259-4700-91fc-acc4170b27ce"));
    System.out.println(vascan);

    while (pager.hasNext()) {
      pager.next().forEach(System.out::println);
    }
  }

  /**
   * A page consisting entirely of blocked datasets must not stop paging: the page yields no usable datasets,
   * but hasNext() must keep reflecting GBIF's endOfRecords flag so the next page is still requested.
   * Blocked datasets are filtered out before convert(), so this needs no live GBIF lookups.
   */
  @Test
  public void blockedPageDoesNotStopPaging() {
    UUID blocked1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID blocked2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    GbifConfig cfg = new GbifConfig();
    cfg.blockedDatasets = Set.of(blocked1, blocked2);

    final Client client = ClientBuilder.newClient();
    try {
      DatasetPager pager = new DatasetPager(client, cfg, null);

      DatasetPager.GResp resp = new DatasetPager.GResp();
      resp.results = new ArrayList<>();
      resp.results.add(gdataset(blocked1));
      resp.results.add(gdataset(blocked2));

      // fully blocked page, but GBIF still has more records to page through
      resp.endOfRecords = false;
      List<DatasetPager.GbifDataset> page = pager.processPage(resp);
      assertEquals(0, page.size());
      assertTrue("a fully blocked page must not end paging while GBIF has more records", pager.hasNext());

      // same fully blocked page, but now it is GBIF's last page
      resp.endOfRecords = true;
      page = pager.processPage(resp);
      assertEquals(0, page.size());
      assertFalse(pager.hasNext());
    } finally {
      client.close();
    }
  }

  /**
   * Datasets hosted by ChecklistBank's own GBIF installation are passed on flagged as hosted whatever their endpoints,
   * so the sync knows them and never deletes them, while any other dataset without a COLDP or DwC-A endpoint is dropped.
   */
  @Test
  public void hostedDatasetsAreKept() {
    UUID installation = UUID.fromString("3298f964-ee79-44d3-9ac3-4236aa62c5f9");
    GbifConfig cfg = new GbifConfig();
    cfg.installationKey = installation;

    final Client client = ClientBuilder.newClient();
    try {
      DatasetPager pager = new DatasetPager(client, cfg, null);

      DatasetPager.GDataset hosted = checklist(UUID.randomUUID(), "TEXT_TREE");
      hosted.installationKey = installation;
      DatasetPager.GDataset other = checklist(UUID.randomUUID(), "TEXT_TREE");
      other.installationKey = UUID.randomUUID();

      DatasetPager.GResp resp = new DatasetPager.GResp();
      resp.results = new ArrayList<>(List.of(hosted, other));
      resp.endOfRecords = true;
      List<DatasetPager.GbifDataset> page = pager.processPage(resp);
      assertEquals(1, page.size());
      assertEquals(hosted.key, page.get(0).getGbifKey());
      assertTrue(page.get(0).isHosted());
    } finally {
      client.close();
    }
  }

  private static DatasetPager.GDataset checklist(UUID key, String endpointType) {
    DatasetPager.GDataset d = gdataset(key);
    d.type = "CHECKLIST";
    d.title = "Checklist " + key;
    DatasetPager.GEndpoint e = new DatasetPager.GEndpoint();
    e.type = endpointType;
    e.url = "https://api.checklistbank.org/dataset/1/archive.zip";
    d.endpoints = new ArrayList<>(List.of(e));
    return d;
  }

  private static DatasetPager.GDataset gdataset(UUID key) {
    DatasetPager.GDataset d = new DatasetPager.GDataset();
    d.key = key;
    return d;
  }

  /**
   * The retry backoff must stay in the seconds range (and never grow into minutes like the old tries*tries minutes),
   * so a single slow GBIF page can never stall the whole sync.
   */
  @Test
  public void backoffSeconds() {
    for (int tries = 1; tries <= 6; tries++) {
      long secs = DatasetPager.backoffSeconds(tries);
      assertTrue("backoff must be positive", secs >= 2);
      // capped at 60s plus up to 50% jitter - never minutes-long like the old tries*tries minutes
      assertTrue("backoff " + secs + "s for try " + tries + " must stay within seconds", secs <= 90);
    }
  }

  @Test
  public void parseGbifDateTime() {
    // GBIF returns ISO-8601 with offset; we store as UTC LocalDateTime
    assertEquals(LocalDateTime.of(2026, 6, 9, 20, 4, 21, 795_000_000),
      DatasetPager.parseGbifDateTime("2026-06-09T20:04:21.795+00:00"));
    // a +02:00 offset is normalised to UTC
    assertEquals(LocalDateTime.of(2026, 6, 9, 18, 4, 21),
      DatasetPager.parseGbifDateTime("2026-06-09T20:04:21+02:00"));
    assertNull(DatasetPager.parseGbifDateTime(null));
    assertNull(DatasetPager.parseGbifDateTime("  "));
    assertNull(DatasetPager.parseGbifDateTime("not a date"));
  }

}
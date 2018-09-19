package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameUsageIndexServiceTest extends EsReadWriteTestBase {

  private static final String NAME_USAGE_TEST = "name_usage_test";

  @Test
  public void indexBulk() throws IOException, EsException, InterruptedException {
    try (RestClient client = getEsClient()) {
      EsUtil.deleteIndex(client, NAME_USAGE_TEST);
      EsUtil.createIndex(client, NAME_USAGE_TEST, getEsConfig().nameUsage);
      List<NameUsage> nus = new ArrayList<>();
      BareName bn = new BareName();
      Name name = new Name();
      name.setGenus("Santa");
      name.setSpecificEpithet("clausa");
      bn.setName(name);
      nus.add(bn);
      nus.add(TestEntityGenerator.SYN1);
      nus.add(TestEntityGenerator.SYN2);
      nus.add(TestEntityGenerator.TAXON1);
      nus.add(TestEntityGenerator.TAXON2);
      NameUsageIndexService.indexBulk(client, NAME_USAGE_TEST, nus);
      // Wait a short while because indexing is async
      Thread.sleep(500);
      EsUtil.refreshIndex(client, NAME_USAGE_TEST);
      assertEquals(5, EsUtil.count(client, NAME_USAGE_TEST));
    }
  }

}

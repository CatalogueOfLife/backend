package org.col.es;

import org.col.common.util.YamlUtils;
import org.junit.rules.ExternalResource;

public class EsSetupRule extends ExternalResource {

  private EsClientFactory esClientFactory;

  @Override
  protected void before() throws Throwable {
    super.before();
    EsConfig cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    esClientFactory = new EsClientFactory(cfg);
  }

  public EsClientFactory getClientFactory() {
    return esClientFactory;
  }

  @Override
  protected void after() {
    super.after();
  }

}

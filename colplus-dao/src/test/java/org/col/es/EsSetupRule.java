package org.col.es;

import org.col.common.util.YamlUtils;
import org.junit.rules.ExternalResource;

public class EsSetupRule extends ExternalResource {

  private EsConfig cfg;
  private EsClientFactory esClientFactory;

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    esClientFactory = new EsClientFactory(cfg);
  }

  public EsClientFactory getClientFactory() {
    return esClientFactory;
  }

  public EsConfig getEsConfig() {
    return cfg;
  }

  @Override
  protected void after() {
    super.after();
  }

}

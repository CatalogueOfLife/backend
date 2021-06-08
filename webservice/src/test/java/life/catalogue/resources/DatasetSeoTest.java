package life.catalogue.resources;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.License;
import life.catalogue.exporter.FmUtil;

import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

import freemarker.template.Template;

public class DatasetSeoTest {

  @Test
  public void streamFreemarker() throws Exception {
    Dataset d = new Dataset();
    d.setKey(100);
    d.setTitle("My Title");
    test(d);

    d.addCreator(Agent.parse("Albert Einstein"));
    test(d);

    d.setLicense(License.OTHER);
    test(d);

    test(d);
    d.setLicense(License.CC_BY);
  }

  void test(Dataset d) throws Exception {
    Writer out = new StringWriter();
    Template temp = FmUtil.FMK.getTemplate("seo/dataset-seo.ftl");
    temp.process(d, out);
    System.out.println(out);
  }
}
package life.catalogue.resources;

import freemarker.template.Template;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.License;
import life.catalogue.exporter.FmUtil;

import org.junit.Test;

import java.io.StringWriter;
import java.io.Writer;

public class DatasetSeoTest {

  @Test
  public void streamFreemarker() throws Exception {
    Dataset d = new Dataset();
    d.setKey(100);
    d.setTitle("My Title");
    test(d);

    d.addCreator(Person.parse("Albert Einstein"));
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
package life.catalogue.resources;

import freemarker.template.Template;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.exporter.FmUtil;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static org.junit.Assert.*;

public class DatasetSeoTest {

  @Test
  public void streamFreemarker() throws Exception {
    ArchivedDataset d = new ArchivedDataset();
    d.setKey(100);
    d.setTitle("My Title");
    test(d);

    d.addAuthor(Person.parse("Albert Einstein"));
    test(d);

    d.setLicense(License.OTHER);
    test(d);

    test(d);
    d.setLicense(License.CC_BY);
  }

  void test(ArchivedDataset d) throws Exception {
    Writer out = new StringWriter();
    Template temp = FmUtil.FMK.getTemplate("seo/dataset-seo.ftl");
    temp.process(d, out);
    System.out.println(out);
  }
}
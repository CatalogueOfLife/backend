package life.catalogue.common.text;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetType;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class SimpleTemplateTest {

  @Test
  public void render() {
    Dataset d = new Dataset();
    d.setTitle("Catalogue of Life");
    d.setAlias("CoL");
    d.setType(DatasetType.TAXONOMIC);
    d.setReleased(LocalDate.of(1999, 5, 15));

    assertEquals("col", SimpleTemplate.render("col", d));
    assertEquals("Catalogue of Life (CoL)", SimpleTemplate.render("{title} ({alias})", d));
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date}", d, Locale.UK));
    sdf = new SimpleDateFormat("MMMM yyyy");
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date,MMMM yyyy}", d, Locale.UK));
  }
}
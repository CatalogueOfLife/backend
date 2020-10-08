package life.catalogue.common.text;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetType;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date}", d));
    sdf = new SimpleDateFormat("MMMM yyyy");
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date,MMMM yyyy}", d));

    sdf = new SimpleDateFormat("yy.M");
    assertEquals("20.5", sdf.format(new Date(2020, 04, 21)));
    assertEquals(sdf.format(new Date()), SimpleTemplate.render("{date,yy.M}", d));
  }

  @Test
  public void renderMap() {
    Map<String, Object> d = new HashMap<>();
    d.put("title", "Catalogue of Life");
    d.put("alias", "CoL");
    d.put("type", DatasetType.TAXONOMIC);
    d.put("released", LocalDate.of(1999, 5, 15));

    assertEquals("col", SimpleTemplate.render("col", d));
    assertEquals("Catalogue of Life (CoL)", SimpleTemplate.render("{title} ({alias})", d));
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date}", d));
    sdf = new SimpleDateFormat("MMMM yyyy");
    assertEquals("Catalogue of Life, "+sdf.format(new Date()), SimpleTemplate.render("{title}, {date,MMMM yyyy}", d));

    sdf = new SimpleDateFormat("yy.M");
    assertEquals("20.5", sdf.format(new Date(2020, 04, 21)));
    assertEquals(sdf.format(new Date()), SimpleTemplate.render("{date,yy.M}", d));
  }

  @Test
  public void renderSpecialDay() {
    LocalDateTime d = LocalDateTime.of(2020, 6, 21,1,1);
    Map data = Map.of("d", d);
    assertEquals("21st", SimpleTemplate.render("{d,ddd}", data));
    assertEquals("21st June", SimpleTemplate.render("{d,ddd MMMM}", data));
    assertEquals("21 June", SimpleTemplate.render("{d,dd MMMM}", data));
    assertEquals("21st June 2020", SimpleTemplate.render("{d,ddd MMMM yyyy}", data));
    assertEquals("20.6", SimpleTemplate.render("{d,yy.M}", data));
  }
}
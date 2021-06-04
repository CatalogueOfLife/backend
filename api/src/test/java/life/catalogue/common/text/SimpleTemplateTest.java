package life.catalogue.common.text;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.common.date.FuzzyDate;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SimpleTemplateTest {

  @Test
  public void render() {
    Dataset d = new Dataset();
    d.setTitle("Catalogue of Life");
    d.setAlias("CoL");
    d.setType(DatasetType.TAXONOMIC);
    d.setIssued(FuzzyDate.of(1999, 5, 15));

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

  static class Puppet {
    final String name;
    final LocalDateTime born = LocalDateTime.of(2020, 10, 9,1,1);
    final Puppet mother;
    final Puppet father;

    public Puppet(String name, Puppet mother, Puppet father) {
      this.name = name;
      this.mother = mother;
      this.father = father;
    }

    public String getName() {
      return name;
    }

    public LocalDateTime getBorn() {
      return born;
    }

    public Puppet getMother() {
      return mother;
    }

    public Puppet getFather() {
      return father;
    }

    @Override
    public String toString() {
      return name;
    }
  }
  static class ListContainer {
    List<Puppet> puppets;

    public ListContainer(List<Puppet> puppets) {
      this.puppets = puppets;
    }

    public List<Puppet> getPuppets() {
      return puppets;
    }
  }

  @Test
  public void renderNestedProperties() {
    Puppet clair = new Puppet("Clair", null, null);
    Puppet mark = new Puppet("Mark", null, null);
    Puppet shreg = new Puppet("Shreg", clair, mark);
    Puppet shrug = new Puppet("Shrug", shreg, null);

    assertEquals("Clair 2020-10-09", SimpleTemplate.render("{name} {born}", clair));
    assertEquals("Shrug 2020-10-09. Mum: Shreg, Grandmother: Clair", SimpleTemplate.render("{name} {born}. Mum: {mother.name}, Grandmother: {mother.mother.name}", shrug));

    assertEquals("Hi Clair, Mark, Shreg, Shreg !!!", SimpleTemplate.render("Hi {puppets} !!!", new ListContainer(List.of(clair, mark, shreg, shreg))));
  }

  @Test(expected = IllegalArgumentException.class)
  public void renderBadDateFormat() {
    Puppet clair = new Puppet("Clair", null, null);
    assertEquals("Clair 2020-10-09", SimpleTemplate.render("{name} {born, YMYDhmxswd}", clair));
  }

  @Test(expected = IllegalArgumentException.class)
  public void renderBadProp() {
    Puppet clair = new Puppet("Clair", null, null);
    assertEquals("Clair 2020-10-09", SimpleTemplate.render("{namy} {borny, Y}", clair));
  }
}
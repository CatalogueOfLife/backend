package life.catalogue.common.text;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;

import java.time.LocalDate;
import java.util.*;

import life.catalogue.common.date.FuzzyDate;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;

public class CitationUtilsTest {

  List<Agent> people(int size){
    List<Agent> people = new ArrayList<>();
    while (size>0) {
      people.add(new Agent("Frank", "Miller"));
      size--;
    }
    return people;
  }

  List<Agent> people(String... names){
    List<Agent> people = new ArrayList<>();
    Iterator<String> iter = Arrays.stream(names).iterator();
    while (iter.hasNext()) {
      String first = iter.next();
      String last = iter.hasNext() ? iter.next() : null;
      people.add(new Agent(first, last));
    }
    return people;
  }

  @Test
  public void concat() {
    assertEquals("Miller F., Miller F., Miller F.", CitationUtils.concat(people(3)));
  }

  @Test
  public void concatEds() {
    assertEquals("Miller F., Miller F., Miller F. (eds.)", CitationUtils.concatEditors(people(3)));
  }


  @Test
  public void buildSourceCitation() throws Exception {
    Dataset proj = new Dataset();
    proj.setKey(Datasets.COL);
    proj.setAlias("COL");
    proj.setTitle("Species 2000 & ITIS Catalogue of Life");
    proj.setEditor(people("Yuri", "Roskov", "Geoff", "Ower", "Thomas", "Orrell", "David", "Nicolson"));
    proj.setIssued(FuzzyDate.of("2019-04-21"));

    Dataset d = new Dataset();
    d.setKey(1010);
    d.setAlias("fish");
    d.setTitle("FishBase");
    d.setVersion("v2.0");
    d.setEditor(people("Rainer", "Froese", "David", "Pauly"));
    d.setIssued(FuzzyDate.of("2019-07-13"));

    assertEquals("Mama",
      CitationUtils.fromTemplate(d,proj, "Mama")
    );

    assertEquals("Mama FishBase",
      CitationUtils.fromTemplate(d,proj, "Mama {title}")
    );

    assertEquals("Froese R., Pauly D. (eds.) (2019-04-21). fish: FishBase (version v2.0). In: Species 2000 & ITIS Catalogue of Life, 2019-04-21.",
      CitationUtils.fromTemplate(d,proj, "{editorsOrAuthors} ({project.issued}). {alias}: {title} (version {version}). In: {project.title}, {project.issued}.")
    );
  }
}
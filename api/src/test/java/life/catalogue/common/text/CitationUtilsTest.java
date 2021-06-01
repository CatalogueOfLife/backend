package life.catalogue.common.text;

import com.google.common.collect.Lists;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class CitationUtilsTest {

  List<Person> people(int size){
    List<Person> people = new ArrayList<>();
    while (size>0) {
      people.add(new Person("Frank", "Miller"));
      size--;
    }
    return people;
  }

  List<Person> people(String... names){
    List<Person> people = new ArrayList<>();
    Iterator<String> iter = Arrays.stream(names).iterator();
    while (iter.hasNext()) {
      String first = iter.next();
      String last = iter.hasNext() ? iter.next() : null;
      people.add(new Person(first, last));
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
  public void buildCitation() throws Exception {
    Dataset d = new Dataset();
    d.setKey(Datasets.COL);
    d.setTitle("Species 2000 & ITIS Catalogue of Life");
    d.setEditor(Person.parse(Lists.newArrayList("Roskov Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
    d.setIssued(LocalDate.parse("2019-04-21"));
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21.",
      CitationUtils.buildCitation(d)
    );

    d.setCreator(d.getEditor());
    d.setEditor(Collections.emptyList());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21.",
      CitationUtils.buildCitation(d)
    );

    d.setEditor(null);
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21.",
      CitationUtils.buildCitation(d)
    );
  }

  @Test
  public void buildSourceCitation() throws Exception {
    Dataset proj = new Dataset();
    proj.setKey(Datasets.COL);
    proj.setAlias("COL");
    proj.setTitle("Species 2000 & ITIS Catalogue of Life");
    proj.setEditor(people("Yuri", "Roskov", "Geoff", "Ower", "Thomas", "Orrell", "David", "Nicolson"));
    proj.setIssued(LocalDate.parse("2019-04-21"));
    proj.setCitation(CitationUtils.buildCitation(proj));

    Dataset d = new Dataset();
    d.setKey(1010);
    d.setAlias("fish");
    d.setTitle("FishBase");
    d.setVersion("v2.0");
    d.setEditor(people("Rainer", "Froese", "David", "Pauly"));
    d.setIssued(LocalDate.parse("2019-07-13"));

    assertEquals("Mama",
      CitationUtils.fromTemplate(d,proj, "Mama")
    );

    assertEquals("Mama FishBase",
      CitationUtils.fromTemplate(d,proj, "Mama {title}")
    );

    assertEquals("Froese R., Pauly D. (eds.) (2019-04-21). fish: FishBase (version v2.0). In: Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21.",
      CitationUtils.fromTemplate(d,proj, "{editorsOrAuthors} ({project.released}). {alias}: {title} (version {version}). In: {project.citation}")
    );
  }
}
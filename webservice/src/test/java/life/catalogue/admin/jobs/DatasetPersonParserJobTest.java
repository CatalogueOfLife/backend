package life.catalogue.admin.jobs;

import life.catalogue.api.model.Person;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DatasetPersonParserJobTest {

  @Test
  public void parse() {
    List<Person> people = List.of(
      person("Schierwater B."),
      person("Eitel M. & DeSalle R.")
    );
    List<Person> result = DatasetPersonParserJob.parse(people);
    assertEquals(List.of(
      person("Schierwater", "B."),
      person("Eitel", "M."),
      person("DeSalle", "R.")
    ), result);
  }

  static Person person(String last, String first) {
    return new Person(first, last);
  }

  static Person person(String name) {
    return new Person(null, name);
  }
}
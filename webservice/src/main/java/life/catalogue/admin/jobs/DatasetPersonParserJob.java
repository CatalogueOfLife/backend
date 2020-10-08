package life.catalogue.admin.jobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.User;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Updates the usage counter for all managed datasets.
 */
public class DatasetPersonParserJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPersonParserJob.class);
  private final SqlSessionFactory factory;
  @JsonProperty
  private final Integer datasetKey;

  public DatasetPersonParserJob(User user, JobPriority priority, SqlSessionFactory factory, Integer datasetKey) {
    super(priority, user.getKey());
    this.factory = factory;
    this.datasetKey = datasetKey;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof DatasetPersonParserJob) {
      DatasetPersonParserJob job = (DatasetPersonParserJob) other;
      return Objects.equals(datasetKey, job.datasetKey);
    }
    return false;
  }

  @Override
  public void execute() {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      for (Dataset d : dm.process(null)) {
        d.setAuthors(parse(d.getAuthors()));
        d.setEditors(parse(d.getEditors()));

      }
    }
  }

  static List<Person> parse(List<Person> people) {
    List<Person> result = new ArrayList<>();
    if (people != null) {
      for (Person p : people) {
        result.addAll(parse(p));
      }
    }
    return result;
  }

  static List<Person> parse(Person p) {
    List<Person> result = new ArrayList<>();
    if (p.getFamilyName() != null && p.getGivenName()==null) {
      if (p.getFamilyName().contains("&")) {
        for (String name : p.getFamilyName().split("&")) {
          result.add(Person.parse(name));
        }
      } else {
        Person p2 = Person.parse(p.getFamilyName());
        if (p.getEmail() != null) {
          p2.setEmail(p.getEmail());
        }
        if (p.getOrcid() != null) {
          p2.setOrcid(p.getOrcid());
        }
        result.add(p2);
      }
    } else {
      result.add(p);
    }
    return result;
  }
}

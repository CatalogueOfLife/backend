package life.catalogue.db.mapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TmpIssueMapperTest extends MapperTestBase<TmpIssueMapper> {

  public TmpIssueMapperTest() {
    super(TmpIssueMapper.class);
  }

  @Test
  public void createTmpIssuesTable() {
    mapper().createTmpIssuesTable(testDataRule.testData.key, null);
  }

}
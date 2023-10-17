package life.catalogue.db.mapper;

import life.catalogue.api.model.ApiAnalytics;

import life.catalogue.api.vocab.Country;
import life.catalogue.db.TestDataRule;

import java.time.LocalDateTime;
import java.util.Map;

public class ApiAnalyticsMapperTest extends CRUDTestBase<Long, ApiAnalytics, ApiAnalyticsMapper> {

  public ApiAnalyticsMapperTest() {
    super(ApiAnalyticsMapper.class, TestDataRule.EMPTY);
  }

  @Override
  ApiAnalytics createTestEntity() {
    var a = new ApiAnalytics();
    a.setFrom(LocalDateTime.now().minusHours(5));
    a.setTo(LocalDateTime.now());
    a.setRequestCount(12345678);
    a.setAgentAgg(Map.of("safari", 100, "chrome", 345));
    a.setCountryAgg(Map.of(Country.GERMANY, 10, Country.DENMARK, 12, Country.ISRAEL, 4110));
    a.setResponseCodeAgg(Map.of(200, 345678, 201, 3456, 404, 3456789, 500, 13));
    a.setRequestPatternAgg(Map.of("pat1", 100, "pattern2", 345));
    a.setDatasetAgg(Map.of(3, 56789, 1010, 456789));
    a.setOtherMetrics(Map.of("safari", 100, "chrome", 345));
    return a;
  }

  @Override
  ApiAnalytics removeDbCreatedProps(ApiAnalytics obj) {
    if (obj != null) {
      obj.setKey(null);
    }
    return obj;
  }

  @Override
  void updateTestObj(ApiAnalytics obj) {

  }
}
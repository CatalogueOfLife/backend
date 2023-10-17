package life.catalogue.analytics;

import life.catalogue.api.model.ApiAnalytics;
import life.catalogue.db.mapper.ApiAnalyticsMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiAnalyticsDao {
  private static final Logger LOG = LoggerFactory.getLogger(ApiAnalyticsDao.class);
  private static final String GBIF_PORTAL_REQUESTS_COUNT = "gbifPortalRequestsCount";

  private final LogsClient logsClient;
  private final SqlSessionFactory factory;

  public ApiAnalyticsDao(LogsClient logsClient, SqlSessionFactory factory) {
    this.logsClient = logsClient;
    this.factory = factory;
  }

  /**
   * Create a new analytics records from ES with aggregated counts in the given period.
   * @param startDatetime
   * @param endDatetime
   * @return
   * @throws IOException
   */
  public Optional<ApiAnalytics> createAnalytics(LocalDateTime startDatetime, LocalDateTime endDatetime) throws IOException {
    Objects.requireNonNull(startDatetime);
    Objects.requireNonNull(endDatetime);

    // find the right ES index
    String indexName = getIndexName(startDatetime);
    if (!logsClient.indexExists(indexName)) {
      LOG.error("ES Index {} doesn't exist", indexName);
      return Optional.empty();
    }

    try (SqlSession session = factory.openSession(true)){
      var mapper = session.getMapper(ApiAnalyticsMapper.class);
      if (mapper.exists(startDatetime, endDatetime)) {
        LOG.info("Analytics from {} to {} already exist in the DB", startDatetime, endDatetime);
        return Optional.empty();
      }

      // get the external requests metrics
      ExternalRequestsMetrics externalRequestsMetrics = logsClient.getExternalRequestsMetrics(indexName, startDatetime, endDatetime);

      // get the gbif portal requests count
      long gbifPortalRequestsCount = logsClient.getGbifPortalRequestsCount(indexName, startDatetime, endDatetime);

      // build analytics instance
      ApiAnalytics apiAnalytics = buildApiAnalytics(startDatetime, endDatetime, externalRequestsMetrics, gbifPortalRequestsCount);

      // store the analytics in the DB
      mapper.create(apiAnalytics);

      // return the created analytics
      return Optional.of(apiAnalytics);
    }
  }

  public int createAnalyticsRange(LocalDateTime startDatetime, LocalDateTime endDatetime, Duration range) {
    LocalDateTime from = startDatetime;

    try (SqlSession session = factory.openSession(true)) {
      var mapper = session.getMapper(ApiAnalyticsMapper.class);
      ApiAnalytics latestAnalytics = mapper.getLatest();
      if (latestAnalytics != null && (from.isBefore(latestAnalytics.getFrom()) || from.isEqual(latestAnalytics.getFrom()))) {
        from = latestAnalytics.getTo();
      }
    }

    AtomicInteger counter = new AtomicInteger();
    while (from.isBefore(endDatetime)) {
      LocalDateTime to = from.plus(range);
      LOG.info("Aggregating analytics from {} to {}", from, to);
      try {
        createAnalytics(from, to).ifPresent(x -> counter.incrementAndGet());
      } catch (Exception ex) {
        LOG.error("Couldn't add analytics from {} to {}", from, to, ex);
      }
      from = to;
    }

    return counter.get();
  }

  public void fillTimeGap(Duration range) {
    // check the last analytics inserted.
    // If there is a gap between the last one and the current we fill the gap
    ApiAnalytics latestAnalytics;
    try (SqlSession session = factory.openSession()) {
      latestAnalytics = session.getMapper(ApiAnalyticsMapper.class).getLatest();
      if (latestAnalytics == null) {
        LOG.debug("No analytics found so no gap to fill");
        return;
      }
    }

    // previous hour of current time minus the range is the start for the current range
    LocalDateTime currentStartTime = getCurrentHour().minus(range);

    if (latestAnalytics.getTo().isBefore(currentStartTime)) {
      // fill the gap
      LOG.info("Filling gap from {} to {} with range {}", latestAnalytics.getTo(), currentStartTime, range);
      var cnt = createAnalyticsRange(latestAnalytics.getTo(), currentStartTime, range);
      LOG.info("{} analytics filled", cnt);
    }
  }

  public void retryEmptyAnalytics() {
    try (SqlSession session = factory.openSession(true)) {
      var mapper = session.getMapper(ApiAnalyticsMapper.class);
      mapper.list(true, null).forEach(
        a -> {
          LOG.info("Retrying analytics from {} to {}", a.getFrom(), a.getTo());
          try {
            mapper.delete(a.getKey());
            createAnalytics(a.getFrom(), a.getTo());
          } catch (Exception ex) {
            LOG.error("Couldn't retry analytics  from {} to {}", a.getFrom(), a.getTo(), ex);
          }
        }
      );
    }
  }

  private ApiAnalytics buildApiAnalytics(LocalDateTime startDatetime, LocalDateTime endDatetime, ExternalRequestsMetrics externalRequestsMetrics, long gbifPortalRequestsCount) {
    ApiAnalytics analytics = new ApiAnalytics();
    analytics.setFrom(startDatetime);
    analytics.setTo(endDatetime);
    analytics.setRequestCount((int)externalRequestsMetrics.getRequestsCount());
    analytics.setRequestPatternAgg(toInt(externalRequestsMetrics.getRequestPatternAgg()));
    analytics.setAgentAgg(toInt(externalRequestsMetrics.getAgentAgg()));
    analytics.setCountryAgg(toInt(externalRequestsMetrics.getGeolocationAgg()));
    analytics.setResponseCodeAgg(toInt(externalRequestsMetrics.getResponseCodeAgg()));
    analytics.setOtherMetrics(toInt(Collections.singletonMap(GBIF_PORTAL_REQUESTS_COUNT, gbifPortalRequestsCount)));
    return analytics;
  }

  private static <T> Map<T, Integer> toInt(Map<T, Long> cnt) {
    if (cnt != null) {
      Map<T, Integer> cntInt = new HashMap<>();
      cnt.forEach((k,v) -> cntInt.put(k, v.intValue()));
      return cntInt;
    }
    return null;
  }

  private String getIndexName(LocalDateTime startDate) {
    return "prod-varnish-" + startDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
  }

  public LocalDateTime getCurrentHour() {
    return LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
  }
}


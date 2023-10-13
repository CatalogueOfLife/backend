# api-analytics

Project to gather analytics about the CLB & COL API usage
Heavily based on the GBIF project https://github.com/gbif/api-analytics


## How we count the metrics

The external calls that we count are filtered like this:
- Host: `prodapicache-vh.gbif.org`
- IP: IPs that are not in `130.225.43.*`
- Request:
  - only requests that go to api.gbif.org
  - requests that don't end with `robots.txt` or `favicon.ico`
- Referrer: it has to be different than `https://www.gbif.org/`
- Agent: it has to be different than these [bots](https://github.com/gbif/api-analytics/blob/e5cfea17a03b03f25c96929d59e2c03c68604502/src/main/java/org/gbif/api/analytics/service/es/EsConstants.java#L32)

The GBIF portal calls are filtered like this:
- Host: `prodapicache-vh.gbif.org`
- Agent: `GBIF-portal`

## Database data

We store the following information in the database:

- `from_datetime`: start datetime of the range
- `to_datetime`:  end datetime of the range
- `request_count`: total of requests received for the time range(only external calls)
- `country_agg`: counts per country(only external calls)
- `response_code_agg`: counts per HTTP response code(only external calls)
- `agent_agg`: counts per user agent(only external calls)
- `request_pattern_agg`: counts per URL pattern(only external calls)
- `other_metrics`: at the moment the only metric stored is the number of requests received from the GBIF portal(these are
  internal calls so they are not included in the `request_count` column and the others)

### Query examples
- Get all data:
```
SELECT * FROM api_analytics aa
```

- Number of requests per URL per time range:
```
SELECT entry.key, sum(entry.value::bigint) as total
FROM api_analytics aa, jsonb_each_text(aa.request_pattern_agg) AS entry
WHERE aa.from_datetime between '2022-08-18T13:00:00' and '2022-08-18T20:00:00'
GROUP BY entry.key
ORDER BY total desc
```
```
SELECT entry.key, sum(entry.value::bigint) as total
FROM api_analytics aa, jsonb_each_text(aa.request_pattern_agg) AS entry
WHERE aa.from_datetime >= '2022-08-18T13:00:00'
GROUP BY entry.key
ORDER BY total desc
```

- For other aggregations it's the same but changing the column, e.g.: agent:
```
 SELECT entry.key, sum(entry.value::bigint) as total 
 FROM api_analytics aa, jsonb_each_text(aa.agent_agg) AS entry 
 WHERE aa.from_datetime between '2022-08-18T13:00:00' and '2022-08-18T20:00:00'
 GROUP BY entry.key 
 ORDER BY total desc
```

- Get number of requests from the GBIF portal:
```
SELECT aa.other_metrics->'gbifPortalRequestsCount' as gbif_portal_reqs
FROM api_analytics aa 
WHERE aa.from_datetime between '2022-08-18T13:00:00' and '2022-08-18T20:00:00'
```

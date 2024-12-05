package life.catalogue.analytics;

import java.util.function.Supplier;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.elasticsearch.client.RequestOptions;

public class EsConstants {

  // Fields
  static final String TIMESTAMP_FIELD = "@timestamp";
  static final String AGENT_NAME_FIELD = "name.keyword";
  static final String HOST_FIELD = "host.name.keyword";
  static final String COUNTRY_FIELD = "geoip.country_code2.keyword";
  static final String RESPONSE_FIELD = "response";
  static final String REQUEST_FIELD = "request.keyword";
  static final String CLIENT_IP_FIELD = "clientip.keyword";
  static final String REFERRER_FIELD = "referrer.keyword";
  static final String REQUEST_AGENT_FIELD = "request_agent.keyword";

  // Aggs
  static final int AGG_SIZE = 75;
  static final String AGENT_AGG = "agentAgg";
  static final String REQUEST_PATTERN_AGG = "requestPatternAgg";
  static final String COUNTRY_AGG = "countryAgg";
  static final String RESPONSE_AGG = "responseAgg";

  // Values
  static final String[] BOTS =
    new String[] {
      "AhrefsBot",
      "Applebot",
      "FacebookBot",
      "Googlebot",
      "Googlebot-Image",
      "IABot",
      "MJ12bot",
      "PetalBot",
      "SemrushBot",
      "Slackbot",
      "Superfeedr bot",
      "YandexBot",
      "archive.org_bot",
      "bingbot",
      "coccocbot",
      "robots.txt",
    };

  static final String SCRIPT_LANG = "painless";
  static final String REQUEST_PATTERN_SCRIPT_ID = "col-request-pattern-query";

  static final String REQUEST_PATTERN_SCRIPT_CODE =
    "if(doc['request.keyword'].size()>0){ "
    + " String req = doc['request.keyword'].value; "
    + " if(req.contains('gbrds.gbif.org')) { "
    + "     'gbrds.gbif.org' "
    + " } else if(req.contains('grbio.org')) {"
    + "     'grbio.org' "
    + " } else if(req.contains('usfsc.grscicoll.org')) {"
    + "     'usfsc.grscicoll.org'"
    + " } else if(req.contains('grscicoll.org')) {"
    + "     'grscicoll.org'"
    + " } else if(req.contains('biocol.org')) {"
    + "     'biocol.org'"
    + " } else if(req.contains('urn:lsid:')) {"
    + "     'urn:lsid:'"
    + " } else {"
    + "   if (req.endsWith('/')) {"
    + "     req = req.substring(0, req.length() - 1);"
    + "   }"
    + "   String[] paths = req.splitOnToken('/');" // TODO: we can remove the registry-api
    // part since they are filtered in the
    // query in the ip filter
    + "   if (paths.length >= 4 && paths[2] == 'registry-api.gbif.org') {"
    + "     if(paths.length>=5 && (paths[4]=='search'|| paths[4]=='download')){"
    + "       paths[3]+'/'+paths[4]"
    + "     } else { "
    + "       paths[3].splitOnToken('?')[0]"
    + "     }"
    + "   } else if(paths.length >= 5 && (paths[3] == 'v1' || paths[3] == 'v2' )) {"
    + "     if(paths[3] == 'v2') { "
    + "       paths[4]"
    + "     } else { "
    + "       if(paths.length>=6 && (paths[5]=='search'||paths[5]=='download')){ "
    + "         paths[4]+'/'+paths[5]"
    + "       } else if(paths.length>=5){ "
    + "         paths[4].splitOnToken('?')[0]"
    + "       }"
    + "     }"
    + "   } else if(paths.length >= 4) { "
    + "     paths[3].splitOnToken('?')[0]"
    + "   } else if(paths.length >= 3) { "
    + "     paths[2].splitOnToken('?')[0]"
    + "   } else if (!paths[0].isEmpty()){ "
    + "     paths[0].splitOnToken('?')[0]"
    + "   }"
    + " }"
    + "}";

  static final String AGENT_AGG_SCRIPT_ID = "col-agent-agg-query";

  static final String AGENT_AGG_SCRIPT_CODE =
    "if(doc['name.keyword'].size()>0 && doc['request_agent.keyword'].size()>0){"
    + "  String name = doc['name.keyword'].value;"
    + "  String reqAgent = doc['request_agent.keyword'].value;"
    + "  String reqAgentLowerCase = reqAgent.toLowerCase();"
    + "  if(reqAgent.contains('rgbif')) {"
    + "    'rgbif'"
    + "  } else if (reqAgent.contains('pygbif')) {"
    + "    'pygbif'"
    + "  } else if(reqAgentLowerCase.contains('python')){"
    + "    'python'"
    + "  } else if(reqAgentLowerCase.contains('ruby')){"
    + "    'ruby'"
    + "  } else if(reqAgentLowerCase.contains('qgis')){"
    + "    'qgis'"
    + "  } else if(reqAgentLowerCase.contains('axios')){"
    + "    'axios'"
    + "  } else if(reqAgent.contains('OpenRefine')){"
    + "    'OpenRefine'"
    + "  } else if(reqAgentLowerCase.startsWith('libcurl')){"
    + "     'curl'"
    + "  } else if(reqAgent.startsWith('R (')){"
    + "     'R'"
    + "  } else if(name == '') {"
    + "    reqAgent"
    + "  } else if(name == 'Other' && reqAgent != ''){"
    + "    reqAgent"
    + "  } else {"
    + "    name"
    + "  }"
    + "}";

  static final Supplier<RequestOptions> HEADERS =
    () -> {
      RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
      builder.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
      return builder.build();
    };
}
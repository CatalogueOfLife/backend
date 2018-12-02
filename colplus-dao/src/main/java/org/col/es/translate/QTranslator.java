package org.col.es.translate;

import java.util.EnumSet;
import java.util.Set;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.Query;

import static org.col.common.util.CollectionUtils.isEmpty;

/**
 * Translates the "q" request parameter into an Elasticsearch query.
 */
class QTranslator {

	private final NameSearchRequest request;

	QTranslator(NameSearchRequest request) {
		this.request = request;
	}

	Query translate() {
		Set<SearchContent> content;
		if (isEmpty(request.getContent())) {
			content = EnumSet.allOf(SearchContent.class);
		} else {
			content = request.getContent();
		}
		if (content.size() == 1) {
			return content.stream().map(this::translate).findFirst().orElse(null);
		}
		return content.stream().map(this::translate).collect(BoolQuery::new, BoolQuery::should, BoolQuery::should);
	}

	private Query translate(SearchContent sc) {
		switch (sc) {
		case AUTHORSHIP:
			return new AutoCompleteQuery("authorship", request.getQ());
		case SCIENTIFIC_NAME:
			return new AutoCompleteQuery("scientificName", request.getQ());
		case VERNACULAR_NAME:
		default:
			return new AutoCompleteQuery("vernacularNames", request.getQ());
		}
	}

}

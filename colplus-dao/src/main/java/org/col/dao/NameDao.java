package org.col.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Name;
import org.col.api.NameSearch;
import org.col.api.NameSearchResult;
import org.col.api.Page;
import org.col.api.PagedReference;
import org.col.api.PagingResultSet;
import org.col.api.Synonymy;
import org.col.api.VerbatimRecord;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.col.db.mapper.temp.NameSearchResultTemp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class NameDao {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);

	private final SqlSession session;

	public NameDao(SqlSession sqlSession) {
		this.session = sqlSession;
	}

	public int count(int datasetKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.count(datasetKey);
	}

	public PagingResultSet<Name> list(Integer datasetKey, Page page) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		int total = mapper.count(datasetKey);
		List<Name> result = mapper.list(datasetKey, page);
		return new PagingResultSet<>(page, total, result);
	}

	public Integer lookupKey(String id, int datasetKey) throws NotFoundException {
		NameMapper mapper = session.getMapper(NameMapper.class);
		Integer key = mapper.lookupKey(id, datasetKey);
		if (key == null) {
			throw new NotFoundException(Name.class, datasetKey, id);
		}
		return key;
	}

	public Name get(Integer key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.get(key);
	}

	public void create(Name name) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		mapper.create(name);
	}

	/**
	 * Lists all homotypic basionymGroup based on the same basionym
	 */
	public List<Name> basionymGroup(int key) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		return mapper.basionymGroup(key);
	}

	/**
	 * Adds a new synonym link for an existing taxon and synonym name. This link is
	 * used for both a hetero- or homotypic synonym.
	 *
	 * @param taxonKey
	 *          the key of the accepted Taxon
	 * @param synonymNameKey
	 *          the key of the synonym Name
	 */
	public void addSynonym(int datasetKey, int taxonKey, int synonymNameKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		mapper.addSynonym(datasetKey, taxonKey, synonymNameKey);
	}

	/**
	 * Assemble a synonymy object from the list of synonymy names for a given
	 * accepted taxon.
	 */
	public Synonymy getSynonymy(int taxonKey) {
		NameMapper mapper = session.getMapper(NameMapper.class);
		Synonymy syn = new Synonymy();
		int lastBasKey = -1;
		List<Name> homotypics = null;
		for (Name n : mapper.synonyms(taxonKey)) {
			int basKey = n.getBasionymKey() == null ? n.getKey() : n.getBasionymKey();
			if (lastBasKey == -1 || basKey != lastBasKey) {
				lastBasKey = basKey;
				// new homotypic group
				if (homotypics != null) {
					syn.addHomotypicGroup(homotypics);
				}
				homotypics = Lists.newArrayList();
			}
			homotypics.add(n);
		}
		if (homotypics != null) {
			syn.addHomotypicGroup(homotypics);
		}
		return syn;
	}

	public PagingResultSet<NameSearchResult> search(NameSearch query, Page page) {
		if (query.getQ() != null) {
			query.setQ(query.getQ() + ":*");
		}
		NameMapper mapper = session.getMapper(NameMapper.class);
		int total = mapper.countSearchResults(query);
		List<NameSearchResultTemp> temp = mapper.search(query, page);
		List<NameSearchResult> result = new ArrayList<>(temp.size());
		temp.forEach(c -> result.add(c.toNameSearchResult()));
		return new PagingResultSet<>(page, total, result);
	}

	public PagedReference getPublishedIn(int nameKey) {
		ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
		return mapper.getPublishedIn(nameKey);
	}

	public VerbatimRecord getVerbatim(int nameKey) {
		VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
		return mapper.getByName(nameKey);
	}

}

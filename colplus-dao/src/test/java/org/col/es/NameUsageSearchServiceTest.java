package org.col.es;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.BeanPrinter;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.vocab.*;
import org.col.db.dao.DaoTestBase;
import org.col.db.dao.NameDao;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class NameUsageSearchServiceTest {
}
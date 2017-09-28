/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.jersey.provider;

import org.col.api.Page;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

/**
 * Jersey provider class that extracts the page size/limit and offset from the query parameters,
 * or provides the default implementation if necessary.
 * <p>
 * If the offset or limit values exceed the allowed values an IllegalArgumentException will be thrown.
 * <p/>
 * Example resource use:
 * <pre>
 * {@code
 * public List<Dataset> list(@QueryParam("page") Page page) {
 *   // do stuff
 * }
 * }
 * </pre>
 * <p/>
 */
public class PageFactory implements Factory<Page> {
  private static final Logger LOG = LoggerFactory.getLogger(PageFactory.class);

  static final String PARAM_OFFSET = "offset";
  static final String PARAM_LIMIT = "limit";

  private UriInfo uri;

  @Inject
  public PageFactory(UriInfo uri) {
    this.uri = uri;
  }

  @Override
  public Page provide() {
    try {
      MultivaluedMap<String, String> qp = uri.getQueryParameters();
      LOG.info("getRequestUri: {}", uri.getRequestUri());
      LOG.info("getQueryParameters: {}", qp);
      int offset = qp.containsKey(PARAM_OFFSET) ? Integer.valueOf(qp.getFirst(PARAM_OFFSET)) : Page.DEFAULT_OFFSET;
      int limit = qp.containsKey(PARAM_LIMIT) ? Integer.valueOf(qp.getFirst(PARAM_LIMIT)) : Page.DEFAULT_LIMIT;
      return new Page(offset, limit);

    } catch (NumberFormatException e) {
      LOG.debug("Invalid paging parameters: {}", uri.getQueryParameters());
      return new Page();

    }
  }

  @Override
  public void dispose(Page instance) {

  }

  public static class Binder extends AbstractBinder {
    @Override
    public void configure() {
      bindFactory(PageFactory.class)
          .to(Page.class)
          .in(RequestScoped.class);
    }
  }

}

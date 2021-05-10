/*
 * Copyright 2020 Global Biodiversity Information Facility (GBIF)
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
package life.catalogue.doi.service;


import life.catalogue.api.model.DOI;
import life.catalogue.doi.datacite.model.DataCiteMetadata;
import life.catalogue.doi.datacite.model.DoiData;

import java.net.URI;

import javax.annotation.Nonnull;

public interface DoiService {

  /**
   * Resolves the registered identifier to its status and target URL.
   *
   * @param doi the identifier to resolve
   * @return the status object with the target URL the DOI is backed by or null if DOI does not
   *     exist at all
   * @throws DoiException if the operation failed for any reason
   */
  @Nonnull
  DoiData resolve(DOI doi) throws DoiException;

  /**
   * Check if a DOI is reserved or registered.
   *
   * @param doi the identifier to check
   * @return true if exists or null if not
   * @throws DoiException if the operation failed for any reason
   */
  boolean exists(DOI doi) throws DoiException;

  /**
   * Get the metadata associated with the doi.
   *
   * @param doi the identifier to resolve
   * @return String XML metadata
   * @throws DoiException if the operation failed for any reason
   */
  String getMetadata(DOI doi) throws DoiException;

  /**
   * Reserves a new identifier and defines its initial metadata. Reserved ids are not known to
   * resolvers yet and can still be fully deleted.
   *
   * @param doi the identifier to reserve
   * @param metadata the metadata to be associated with the doi given as a valid DataCite v3.1 XML
   *     document. The identifier inside the metadata will be overwritten by the doi parameter given
   * @throws DoiExistsException if the DOI existed already regardless of its status
   * @throws DoiException if the operation failed for any reason
   */
  void reserve(DOI doi, String metadata) throws DoiException;

  /**
   * Reserves a new identifier and defines its initial metadata. Reserved ids are not known to
   * resolvers yet and can still be fully deleted.
   *
   * @param doi the identifier to reserve
   * @param metadata the metadata to be associated with the doi. The identifier inside the metadata
   *     will be overwritten by the doi parameter given
   * @throws DoiExistsException if the DOI existed already regardless of its status
   * @throws DoiException if the operation failed for any reason
   */
  void reserve(DOI doi, DataCiteMetadata metadata) throws DoiException;

  /**
   * Registers an identifier that is either brand new, has been reserved or is currently marked as
   * deleted. It assigns the latest metadata and a URL for resolution. This causes the DOI to be
   * publicly registered with resolvers and other external services.
   *
   * @param doi the identifier to register
   * @param target the URL the DOI should resolve to
   * @param metadata the metadata to be associated with the doi given as a valid DataCite v3.1 XML
   *     document. The identifier inside the metadata will be overwritten by the explicitly given
   *     DOI
   * @throws DoiException if the operation failed for any reason
   * @throws DoiExistsException if the DOI was already registered
   */
  void register(DOI doi, URI target, String metadata) throws DoiException;

  /**
   * Registers an identifier that is either brand new, has been reserved or is currently marked as
   * deleted. It assigns the latest metadata and a URL for resolution. This causes the DOI to be
   * publicly registered with resolvers and other external services.
   *
   * @param doi the identifier to register
   * @param target the URL the DOI should resolve to
   * @param metadata the metadata to be associated with the doi. The identifier inside the metadata
   *     will be overwritten by the explicitly given DOI
   * @throws DoiException if the operation failed for any reason
   * @throws DoiExistsException if the DOI was already registered
   */
  void register(DOI doi, URI target, DataCiteMetadata metadata) throws DoiException;

  /**
   * Tries to delete an identifier. If the DOI has only been reserved it will be fully deleted, if
   * it was registered before it cannot be deleted as DOIs are permanent identifiers. You can
   * re-register a deleted DOI again if needed.
   *
   * @param doi the identifier to delete
   * @return true if the reserved DOI was fully deleted, false if it was only marked as deleted
   * @throws DoiException if the operation failed for any reason
   */
  boolean delete(DOI doi) throws DoiException;

  /**
   * Updates the identifier metadata. This method must be called every time the object or metadata
   * referenced by the identifier changes (e.g. a dataset gets republished, a dataset is replaced by
   * a new major version, etc).
   *
   * @param doi the identifier of metadata to update
   * @param metadata the DataCite metadata
   * @throws DoiException if the operation failed for any reason
   */
  void update(DOI doi, String metadata) throws DoiException;

  /**
   * Updates the identifier metadata. This method must be called every time the object or metadata
   * referenced by the identifier changes (e.g. a dataset gets republished, a dataset is replaced by
   * a new major version, etc).
   *
   * @param doi the identifier of metadata to update
   * @param metadata the DataCite metadata
   * @throws DoiException if the operation failed for any reason
   */
  void update(DOI doi, DataCiteMetadata metadata) throws DoiException;

  /**
   * Updates the registered identifier's target URL.
   *
   * @param doi the identifier of metadata to update
   * @param target the new URL the DOI should resolve to
   * @throws DoiException if the operation failed for any reason
   */
  void update(DOI doi, URI target) throws DoiException;
}

/**
 * Copyright (c) 2014-2022 Snowplow Analytics Ltd.
 * All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache
 * License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 *
 * See the Apache License Version 2.0 for the specific language
 * governing permissions and limitations there under.
 */
package com.snowplowanalytics.stream.loader
package transformers

/** Like the connector library's ITransformer, but for consuming from stdin */
trait StdinTransformer {
  def consumeLine(line: String): EmitterJsonInput

  /**
   * Convert plain json to an EmitterInput
   *
   * @param record ValidatedRecord containing plain JSON
   * @return An EmitterInput
   */
  def fromClass(record: ValidatedJsonRecord): EmitterJsonInput =
    record
}

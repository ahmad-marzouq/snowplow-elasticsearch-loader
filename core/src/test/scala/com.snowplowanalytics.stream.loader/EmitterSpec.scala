/**
 * Copyright (c) 2014-2022 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.stream.loader

import java.util.Properties
import org.slf4j.Logger

// Scala
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

// AWS libs
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain

// AWS Kinesis Connector libs
import com.amazonaws.services.kinesis.connectors.{KinesisConnectorConfiguration, UnmodifiableBuffer}
import com.amazonaws.services.kinesis.connectors.impl.BasicMemoryBuffer

// cats
import cats.syntax.validated._

import io.circe.Json

// Specs2
import org.specs2.mutable.Specification

// This project
import sinks._
import clients._
import Config.Sink.GoodSink.Elasticsearch.ESChunk

case class MockElasticsearchSender(chunkConf: ESChunk) extends BulkSender[EmitterJsonInput] {
  var sentRecords: List[EmitterJsonInput]       = List.empty
  var callCount: Int                            = 0
  val calls: ListBuffer[List[EmitterJsonInput]] = new ListBuffer
  override val log: Logger                      = null
  override def send(records: List[EmitterJsonInput]): List[EmitterJsonInput] = {
    sentRecords = sentRecords ::: records
    callCount += 1
    calls += records
    List.empty
  }
  override def close() = {}
  override def logHealth(): Unit             = ()
  override def chunkConfig(): ESChunk        = chunkConf
  override val tracker                       = None
  override val maxConnectionWaitTimeMs: Long = 1000L
  override val maxAttempts: Int              = 1
}

class EmitterSpec extends Specification {
  val documentType = "enriched"

  "The emitter" should {
    "return all invalid records" in {

      val fakeSender: BulkSender[EmitterJsonInput] = new BulkSender[EmitterJsonInput] {
        override def send(records: List[EmitterJsonInput]): List[EmitterJsonInput] = List.empty
        override def close(): Unit                                                 = ()
        override def logHealth(): Unit                                             = ()
        override def chunkConfig(): ESChunk                                        = ESChunk(1L, 1L)
        override val tracker                                                       = None
        override val log: Logger                                                   = null
        override val maxConnectionWaitTimeMs: Long                                 = 1000L
        override val maxAttempts: Int                                              = 1
      }

      val kcc =
        new KinesisConnectorConfiguration(new Properties, new DefaultAWSCredentialsProviderChain)
      val eem = new Emitter(Right(fakeSender), new StdouterrSink)

      val validInput: EmitterJsonInput   = "good" -> JsonRecord(Json.obj(), None).valid
      val invalidInput: EmitterJsonInput = "bad"  -> "malformed event".invalidNel

      val input = List(validInput, invalidInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      val actual = eem.emit(ub)

      actual must_== List(invalidInput).asJava
    }

    "send multiple records in seperate requests where single record size > buffer bytes size" in {
      val props = new Properties
      props.setProperty(KinesisConnectorConfiguration.PROP_BUFFER_BYTE_SIZE_LIMIT, "1000")

      val kcc = new KinesisConnectorConfiguration(props, new DefaultAWSCredentialsProviderChain)
      val ess = MockElasticsearchSender(ESChunk(1000L, 1L))
      val eem = new Emitter(Right(ess), new StdouterrSink)

      val validInput: EmitterJsonInput = "good" -> JsonRecord(Json.obj(), None).valid

      val input = List.fill(50)(validInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      eem.emit(ub)

      ess.sentRecords mustEqual input
      ess.callCount mustEqual 50
      forall(ess.calls) { c =>
        c.length mustEqual 1
      }
    }

    "send a single record in 1 request where record size > buffer bytes size " in {
      val props = new Properties
      props.setProperty(KinesisConnectorConfiguration.PROP_BUFFER_BYTE_SIZE_LIMIT, "1000")

      val kcc = new KinesisConnectorConfiguration(props, new DefaultAWSCredentialsProviderChain)
      val ess = MockElasticsearchSender(ESChunk(1000L, 1L))
      val eem = new Emitter(Right(ess), new StdouterrSink)

      val validInput: EmitterJsonInput = "good" -> JsonRecord(Json.obj(), None).valid

      val input = List(validInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      eem.emit(ub)

      ess.sentRecords mustEqual input
      ess.callCount mustEqual 1
      forall(ess.calls) { c =>
        c.length mustEqual 1
      }
    }

    "send multiple records in 1 request where total byte size < buffer bytes size" in {
      val props = new Properties
      props.setProperty(KinesisConnectorConfiguration.PROP_BUFFER_BYTE_SIZE_LIMIT, "1048576")

      val kcc = new KinesisConnectorConfiguration(props, new DefaultAWSCredentialsProviderChain)
      val ess = MockElasticsearchSender(ESChunk(1048576L, 100L))
      val eem = new Emitter(Right(ess), new StdouterrSink)

      val validInput: EmitterJsonInput = "good" -> JsonRecord(Json.obj(), None).valid

      val input = List.fill(50)(validInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      eem.emit(ub)

      ess.sentRecords mustEqual input
      ess.callCount mustEqual 1
      forall(ess.calls) { c =>
        c.length mustEqual 50
      }
    }

    "send a single record in 1 request where single record size < buffer bytes size" in {
      val props = new Properties
      props.setProperty(KinesisConnectorConfiguration.PROP_BUFFER_BYTE_SIZE_LIMIT, "1048576")

      val kcc = new KinesisConnectorConfiguration(props, new DefaultAWSCredentialsProviderChain)
      val ess = MockElasticsearchSender(ESChunk(1048576L, 1L))
      val eem = new Emitter(Right(ess), new StdouterrSink)

      val validInput: EmitterJsonInput = "good" -> JsonRecord(Json.obj(), None).valid

      val input = List(validInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      eem.emit(ub)

      ess.sentRecords mustEqual input
      ess.callCount mustEqual 1
      forall(ess.calls) { c =>
        c.length mustEqual 1
      }
    }

    "send multiple records in batches where single record byte size < buffer size and total byte size > buffer size" in {
      val props = new Properties
      props.setProperty(KinesisConnectorConfiguration.PROP_BUFFER_BYTE_SIZE_LIMIT, "200")

      val kcc = new KinesisConnectorConfiguration(props, new DefaultAWSCredentialsProviderChain)
      val ess = MockElasticsearchSender(ESChunk(200L, 2L))
      val eem = new Emitter(Right(ess), new StdouterrSink)

      // record size is 95 bytes
      val validInput: EmitterJsonInput = "good" -> JsonRecord(Json.obj(), None).valid

      val input = List.fill(20)(validInput)

      val bmb = new BasicMemoryBuffer[EmitterJsonInput](kcc, input.asJava)
      val ub  = new UnmodifiableBuffer[EmitterJsonInput](bmb)

      eem.emit(ub)

      ess.sentRecords mustEqual input
      ess.callCount mustEqual 10 // 10 buffers of 2 records each
      forall(ess.calls) { c =>
        c.length mustEqual 2
      }
    }
  }

}

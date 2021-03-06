/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.codefeedr.core.library.internal

import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.{
  RichParallelSourceFunction,
  RichSourceFunction,
  SourceFunction
}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.codefeedr.configuration.{ConfigurationProviderComponent, KafkaConfigurationComponent}
import org.codefeedr.core.library.SubjectFactoryComponent
import org.codefeedr.core.library.internal.kafka.source.{
  KafkaConsumerFactoryComponent,
  KafkaGenericTrailedSource,
  KafkaRowSource
}
import org.codefeedr.core.library.metastore.{SubjectLibraryComponent, SubjectNode}
import org.codefeedr.model.SubjectType
import org.codefeedr.util.EventTime

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.Future
import async.Async.{async, await}
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}

trait JobComponent {
  this: SubjectLibraryComponent
    with SubjectFactoryComponent
    with ConfigurationProviderComponent
    with KafkaConsumerFactoryComponent
    with KafkaConfigurationComponent =>

  /**
    * TODO: Refactor this class to depend on the traits used from libraryservices, instead of directly calling libraryservices
    *
    * @param name
    * @tparam Input
    * @tparam Output
    */
  abstract class Job[Input: ru.TypeTag: ClassTag: TypeInformation: EventTime,
  Output: ru.TypeTag: ClassTag: EventTime](name: String)
      extends LazyLogging {

    var subjectType: SubjectType = _
    //HACK: Direct call to libraryServices
    lazy val subjectNode = subjectLibrary.getSubject(subjectType.name)
    lazy val jobNode = subjectLibrary.getJob(name)

    var source: SourceFunction[Input] = _

    /**
      * Returns the amount of parallel workers.
      *
      * @return by default 1
      */
    def getParallelism: Int = 1

    /**
      * Setups a stream for the given environment.
      *
      * @param env the environment to setup the stream on.
      * @return the prepared datastream.
      */
    def getStream(env: StreamExecutionEnvironment): DataStream[Output]

    /**
      * Composes the source on the given environment.
      * Registers all meta-information.
      *
      * @param env the environment where the source should be composed on.
      */
    def compose(env: StreamExecutionEnvironment, queryId: String): Future[Unit] = async {
      val sinkName = s"composedsink_${queryId}"
      //HACK: Direct call to libraryServices
      val sink = await(subjectFactory.getSink[Output](sinkName, queryId))
      val stream = getStream(env)
      stream.addSink(sink)
    }

    /**
      * Makes sure the subjectType is created
      *
      * @return
      */
    def setupType(): Future[Unit] = {
      subjectType = SubjectTypeFactory.getSubjectType[Output]
      subjectFactory.create(subjectType).map(_ => ())
    }

    def setSource(job: Job[_, Input]) = {
      //HACK: Direct call to libraryServices
      source = new KafkaGenericTrailedSource[Input](
        job.subjectNode,
        job.jobNode,
        kafkaConfiguration,
        kafkaConsumerFactory,
        subjectFactory.getUnTransformer[Input](subjectType),
        job.subjectType.uuid,
        configurationProvider.get("run", Some("UnConfiguredRun"))
      )
    }

    def startJob() = async {
      val env = StreamExecutionEnvironment.createLocalEnvironment(getParallelism)
      logger.debug(s"Composing env for ${subjectType.name}")
      await(compose(env, s"$name"))
      logger.debug(s"Starting env for ${subjectType.name}")
      env.execute()
      logger.debug(s"Completed env for ${subjectType.name}")
    }

  }
}

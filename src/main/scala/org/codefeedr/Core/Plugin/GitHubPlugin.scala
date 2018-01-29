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

package org.codefeedr.Core.Plugin

import com.google.gson.{Gson, GsonBuilder, JsonObject}
import org.codefeedr.Core.Input.{GHRetrieveCommitFunction, GitHubSource}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.codefeedr.Core.Library.Internal.{AbstractPlugin, SubjectTypeFactory}
import org.codefeedr.Core.Library.SubjectFactory
import org.codefeedr.Model.SubjectType

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}
import org.apache.flink.api.scala._
import org.codefeedr.Core.Clients.GitHub.GitHubProtocol
import org.codefeedr.Core.Clients.GitHub.GitHubProtocol.{Actor, Payload, PushEvent, Repo}
import org.codefeedr.Core.Clients.MongoDB.PUSH_EVENT
import org.codefeedr.Core.Output.MongoSink

import scala.collection.JavaConverters._

class GitHubPlugin[PushEvent: ru.TypeTag: ClassTag](maxRequests: Integer = -1)
    extends AbstractPlugin {

  /**
    * Creates a new SubjectType.
    * @return
    */
  override def CreateSubjectType(): SubjectType = {
    return SubjectTypeFactory.getSubjectType[PushEvent]

  }

  /**
    * Gets the stream.
    * @param env the environment to prepare.
    * @return the data stream.
    */
  def GetStream(env: StreamExecutionEnvironment): DataStream[GitHubProtocol.PushEvent] = {
    val stream =
      env.addSource(new GitHubSource(maxRequests)).filter(_.`type` == "PushEvent").map { x =>
        val gson = new Gson()
        val payload = gson.fromJson(x.payload, classOf[GitHubProtocol.Payload])
        PushEvent(x.id, x.repo, x.actor, payload, x.public, x.created_at)
      }
    stream
  }

  /**
    * Composes the stream.
    * @param env the environment to compose.
    * @return a future of the method.
    */
  override def Compose(env: StreamExecutionEnvironment): Future[Unit] = async {
    val sink = await(SubjectFactory.GetSink[GitHubProtocol.PushEvent])
    val stream = GetStream(env)
    stream.addSink(sink)
    stream.addSink(new MongoSink[GitHubProtocol.PushEvent](PUSH_EVENT, "id"))
  }

}
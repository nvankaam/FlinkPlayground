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

package org.codefeedr.plugins.github.clients

import java.io.IOException

import com.google.gson.reflect.TypeToken
import com.google.gson.{Gson, JsonElement}
import org.codefeedr.plugins.github.clients.GitHubProtocol.{Commit, Event, SimpleCommit}
import org.eclipse.egit.github.core.client.PagedRequest.{PAGE_FIRST, PAGE_SIZE}
import org.eclipse.egit.github.core.client.{GitHubClient, GitHubRequest, PageIterator}
import org.eclipse.egit.github.core.service.GitHubService
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._

/**
  * Requests commit and events into correct case classes.
  * This service is hooked on the GitHubService (dependency).
  * @param client GitHubClient
  */
//TODO ADD EXCEPTIONS!
class GitHubRequestService(client: GitHubClient) extends GitHubService(client) {

  // Brings in default date formats etc
  implicit val formats = DefaultFormats

  //use gson to convert back to string TODO: pretty inefficient to first parse and then 'unparse'?
  lazy val gson: Gson = new Gson()

  /**
    * Gets a commit by SHA.
    * @param repoName the name of the repository.
    * @param sha the SHA of the commit.
    * @return the commit case class.
    */
  @throws(classOf[IOException])
  def getCommit(repoName: String, sha: String): Commit = {
    val uri: StringBuilder = new StringBuilder("/repos")
    uri.append("/").append(repoName)
    uri.append("/commits")
    uri.append("/").append(sha)

    val request: GitHubRequest = createRequest()
    request.setUri(uri.toString())
    request.setType(new TypeToken[JsonElement]() {}.getType)
    val commit = client.get(request).getBody.asInstanceOf[JsonElement]

    //return extracted as Commit
    return parse(gson.toJson(commit)).extract[Commit]
  }

  /**
    * Gets all the commits of a repository.
    * @param repoName the name of the repo.
    * @return a list of all (simple) commit information.
    */
  @throws(classOf[IOException])
  def getAllCommits(repoName: String): PageIterator[SimpleCommit] = {
    val uri: StringBuilder = new StringBuilder("/repos")
    uri.append("/").append(repoName)
    uri.append("/commits")

    val request = createPagedRequest[SimpleCommit](PAGE_FIRST, PAGE_SIZE)
    request.setUri(uri.toString())
    request.setType(new TypeToken[java.util.List[SimpleCommit]]() {}.getType)

    //return page iterator for all commits
    return createPageIterator(request)
  }

  /**
    * Gets all the commits of a repository.
    * @param repoName the name of the repo.
    * @param sha to start from.
    * @return a list of all (simple) commit information.
    */
  @throws(classOf[IOException])
  def getAllCommits(repoName: String, sha: String): PageIterator[SimpleCommit] = {
    val uri: StringBuilder = new StringBuilder("/repos")
    uri.append("/").append(repoName)
    uri.append("/commits")

    val request = createPagedRequest[SimpleCommit](PAGE_FIRST, PAGE_SIZE)
    request.setUri(uri.toString())
    request.setParams(Map("sha" -> sha).asJava)
    request.setType(new TypeToken[java.util.List[SimpleCommit]]() {}.getType)

    //return page iterator for all commits
    return createPageIterator(request)
  }

  /**
    * Gets all events.
    * @return list of all events.
    */
  @throws(classOf[IOException])
  def getEvents(): List[Event] = {
    val request = createPagedRequest[JsonElement](PAGE_FIRST, PAGE_SIZE)
    request.setUri("/events")
    request.setType(new TypeToken[java.util.List[JsonElement]]() {}.getType)

    //get all events
    val events = getAll((createPageIterator(request))).asScala.map(gson.toJson(_))

    //return extracted into Event class
    return events.map(parse(_).extract[Event]).toList
  }

}

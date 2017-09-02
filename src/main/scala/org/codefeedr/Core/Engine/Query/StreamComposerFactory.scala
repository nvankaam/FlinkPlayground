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

package org.codefeedr.Core.Engine.Query

import org.codefeedr.Core.Library.SubjectLibrary

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Niels on 31/07/2017.
  */
object StreamComposerFactory {
  def GetComposer(query: QueryTree): Future[StreamComposer] = {
    query match {
      case SubjectSource(subjectName) =>
        SubjectLibrary.AwaitTypeRegistration(subjectName).map(o => new SourceStreamComposer(o))
      case Join(left, right, keysLeft, keysRight, selectLeft, selectRight, alias) =>
        for {
          leftComposer <- GetComposer(left)
          rightComposer <- GetComposer(right)
          joinedType <- SubjectLibrary.GetOrCreateType(
            alias,
            () =>
              JoinQueryComposer.buildComposedType(leftComposer.GetExposedType(),
                                                  rightComposer.GetExposedType(),
                                                  selectLeft,
                                                  selectRight,
                                                  alias))
        } yield
          new JoinQueryComposer(leftComposer, rightComposer, joinedType, query.asInstanceOf[Join])
      case _ => throw new NotImplementedError("not implemented query subtree")
    }
  }
}

package org.codefeedr.core.library.metastore

import org.codefeedr.core.library.internal.zookeeper.{ZkNode, ZkNodeBase}
import org.codefeedr.model.zookeeper.Partition

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Node representing a single epoch under a source
  * @param epoch
  * @param parent
  */
class EpochNode(epoch: Int, parent: ZkNodeBase) extends ZkNode[String](s"$epoch", parent) {

  /**
    * Retrieves the partitions that belong to this epoch
    * @return the partitions of the epoch
    */
  def getPartitions(): EpochPartitionCollection = new EpochPartitionCollection(this)

  /**
    * Retrieves all partition data of the epoch
    * @return
    */
  def getPartitionData(): Future[Iterable[Partition]] = {
    getPartitions()
      .getChildren()
      .flatMap(
        o =>
          Future.sequence(
            o.map(o => o.getData().map(o => o.get))
        )
      )
  }

  def getEpoch(): Int = epoch
}
package org.codefeedr.core.library.internal.kafka.meta

/**
  * Describes the offset of a partition of a topic in Kafka
  * @param partition
  * @param offset
  */
case class PartitionOffset(partition: Int, offset: Long)

/**
  * Describes a topic, a (subset of) its partitions and their offsets
  * @param topic
  * @param partitions
  */
case class TopicPartitionOffsets(topic: String, partitions: List[PartitionOffset])

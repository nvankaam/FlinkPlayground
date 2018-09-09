package org.codefeedr.socketreceiver

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.CheckpointingMode
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.codefeedr.configuration.ConfigurationProviderComponent
import org.apache.flink.streaming.api.scala._
import org.codefeedr.core.library.SubjectFactoryComponent
import org.codefeedr.core.library.internal.zookeeper.ZkClientComponent
import org.codefeedr.core.library.metastore.SubjectLibraryComponent
import org.codefeedr.evaluation.IntTuple
import org.slf4j.MDC

import scala.concurrent.duration._

import scala.concurrent.Await


trait SocketReceiverComponent {
  this: ConfigurationProviderComponent
    with ZkClientComponent
    with SubjectLibraryComponent
    with SubjectFactoryComponent
    =>



  val socketReceiver:SocketReceiver

  class SocketReceiver {

    /**
      * Creates a new streamexectutionenvironment and deploys the job using the passed configuration
      * @param pt configuration to deploy with
      */
      def deploy(pt:ParameterTool):Unit = {
        configurationProvider.initConfiguration(pt)
        val env = StreamExecutionEnvironment.getExecutionEnvironment
        createTopology(env)
      }

    /**
      * Create the jobTopology for the socketreceiver
      * @param env StreamExecutionEnvironment environment to create the topology on
      * @return
      */
    private def createTopology(env:StreamExecutionEnvironment) = {
      //Put runIdentifier in MDC so we can filter results in ELK.
      MDC.put("RUN_INCREMENT", configurationProvider.get("RUN_INCREMENT"))
      //TODO: Find some better name or way to identify the job
      MDC.put("JOB_IDENTIFIER", "SOCKETRECEIVER")



      // the port to connect to
      val port = try {
        configurationProvider.getInt("socketgenerator.port")
      } catch {
        case e: Exception => {
          System.err.println("No value configured for socketgenerator.port")
          throw e
        }
      }

      val hostname = try {
        configurationProvider.get("socketgenerator.hostname")
      } catch {
        case e: Exception => {
          System.err.println("No value configured for socketgenerator.hostname")
          throw e
        }
      }


      zkClient.deleteRecursive("/")
      subjectLibrary.initialize()

      val subject = Await.result(subjectFactory.create[IntTuple](), 10.seconds)
      val sink = Await.result(subjectFactory.getSink[IntTuple]("testsink", "myjob"),
        10.seconds)

      env.setParallelism(1)
      env.enableCheckpointing(1000,CheckpointingMode.EXACTLY_ONCE)
      env
        .socketTextStream(hostname, port)
        .map(toIntTuple(_))
        .addSink(sink)
      env.execute()
    }


    def toIntTuple(s: String): IntTuple = {
      val r = s.split('|')
      IntTuple(r(0).toInt, r(1).toInt)
    }
  }

}
package io.prediction.core.test

import io.prediction.core.SelfCleaningDataSource
import io.prediction.core.EventWindow
import io.prediction.workflow.SharedSparkContext

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.Event
import io.prediction.data.storage.Storage
import io.prediction.data.store._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

import org.apache.spark.rdd.RDD
import org.scalatest.Inspectors._
import org.scalatest.Matchers._
import org.scalatest.FunSuite
import org.scalatest.Inside

case class DataSourceParams(appName: String, eventWindow: Option[EventWindow], appId: Int) extends Params

class SelfCleaningPDataSource(anAppName: String) extends PDataSource[TrainingData,EmptyEvaluationInfo, Query, EmptyActualResult] with SelfCleaningDataSource {

  val (appId, channelId) = io.prediction.data.store.Common.appNameToId(anAppName, None)


  val dsp = DataSourceParams(anAppName, Some(EventWindow(Some("1825 days"), true, true)), appId = appId)

  override def appName = dsp.appName
  override def eventWindow = dsp.eventWindow

  override def readTraining(sc: SparkContext): TrainingData = new TrainingData()

  def events = Storage.getPEvents().find(appId = dsp.appId)_

  def itemEvents = Storage.getPEvents().find(appId = dsp.appId, entityType = Some("item"), eventNames = Some(Seq("$set")))_  
 
  def eventsAgg = Storage.getPEvents().aggregateProperties(appId = dsp.appId, entityType = "item")_

}

class SelfCleaningDataSourceTest extends FunSuite with Inside with SharedSparkContext {

  //To run manually, requires app "cleanedTest" and test.json data imported to it
  ignore("Test event cleanup") {
    val source = new SelfCleaningPDataSource("cleanedTest")
    val eventsBeforeCount = source.events(sc).count
    val itemEventsBeforeCount = source.itemEvents(sc).count

    source.cleanPersistedPEvents(sc)

    val eventsAfterCount = source.events(sc).count
    val eventsAfter = source.events(sc)
    val itemEventsAfterCount = source.itemEvents(sc).count   
    val distinctEventsAfterCount = eventsAfter.map(x => 
      CleanedDataSourceTest.stripIdAndCreationTimeFromEvents(x)).distinct.count
   
    distinctEventsAfterCount should equal (eventsAfterCount)
    eventsBeforeCount should be > (eventsAfterCount) 
    itemEventsBeforeCount should be > (itemEventsAfterCount)
  }
}

object CleanedDataSourceTest{
  def stripIdAndCreationTimeFromEvents(x: Event): Event = {
   Event(event = x.event, entityType = x.entityType, entityId = x.entityId, targetEntityType = x.targetEntityType, targetEntityId = x.targetEntityId, properties = x.properties, eventTime = x.eventTime, tags = x.tags, prId= x.prId, creationTime = x.eventTime)
  }
}



case class Query() extends Serializable

class TrainingData() extends Serializable

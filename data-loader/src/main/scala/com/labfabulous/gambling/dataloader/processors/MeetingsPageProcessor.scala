package com.labfabulous.gambling.dataloader.processors

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import org.scala_tools.time.Imports._
import org.joda.time.DateTime
import com.labfabulous.gambling.dataloader.html.LinksExtractor
import com.labfabulous.gambling.dataloader.WebClient
import akka.actor.Actor
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.labfabulous.DayWorker._
import com.labfabulous.DayWorker.WorkForDate
import akka.actor.OneForOneStrategy
import com.labfabulous.DayWorker.Start
import com.labfabulous.DayWorker.WorkFailed

class MeetingsPageProcessor(meetingsPageLinksExtractor: LinksExtractor, meetingDetailsProcessor: MeetingDetailsProcessor) extends Actor {
  RegisterJodaTimeConversionHelpers()

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  def processRaces(raceUrls: List[String], date: DateTime, category: String) {
    raceUrls.foreach(url => meetingDetailsProcessor.process(url, date, category))
  }

  def today = {
    DateTime.now().withHour(0).withMinute(0).withMillisOfDay(0)
  }

  def fillForDate(msg: Start, date: DateTime) {
    val targetUrl = msg.baseUrl + date.toString("dd-MM-yyyy")
    if (date <= (today + 1.day)) {
      WebClient.get(targetUrl) match {
        case (200, response) => processRaces(meetingsPageLinksExtractor.extract(response), date, msg.baseUrl)
        case (404, response) => sender ! WorkPartiallyDone(msg.category, date, "${targetUrl} => 404")
        case (code: Int, response) => WorkFailed(msg.category, date, "${targetUrl} => ${code} => ${response}")
      }
    }
  }

  def doWork(start: Start, date: DateTime) {
    fillForDate(start, date)
  }

  def receive = {
    case work: WorkForDate => doWork(work.start, work.date)
  }
}

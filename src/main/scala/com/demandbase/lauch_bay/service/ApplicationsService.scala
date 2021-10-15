package com.demandbase.lauch_bay.service

import cats.implicits.{catsSyntaxOptionId, toShow}
import com.demandbase.lauch_bay.domain.AppConfigDetails
import com.demandbase.lauch_bay.domain.filter.ListApplicationsFilter
import com.demandbase.lauch_bay.domain.types.AppId
import com.demandbase.lauch_bay.trace.{log, Ctx}
import org.slf4j.LoggerFactory
import zio._

trait ApplicationsService {
  def upsert(cmd: AppConfigDetails)(implicit ctx: Ctx): Task[AppConfigDetails]
  def get(id: AppId)(implicit ctx: Ctx): Task[Option[AppConfigDetails]]
  def list(filter: ListApplicationsFilter)(implicit ctx: Ctx): Task[List[AppConfigDetails]]
  def delete(id: AppId)(implicit ctx: Ctx): Task[Option[AppConfigDetails]]
}
object ApplicationsService extends Accessible[ApplicationsService]

case class ApplicationsServiceLive(ref: Ref[Map[AppId, AppConfigDetails]]) extends ApplicationsService {
  implicit private val logger: org.slf4j.Logger = LoggerFactory.getLogger(this.getClass)

  override def upsert(cmd: AppConfigDetails)(implicit ctx: Ctx): Task[AppConfigDetails] = {
    for {
      _ <- ref.update(_ + (cmd.id -> cmd))
      _ <- log.info(s"upsert application ${cmd.show}")
    } yield cmd
  }
  override def get(id: AppId)(implicit ctx: Ctx): Task[Option[AppConfigDetails]] = {
    ref.get.map(_.get(id))
  }
  override def list(filter: ListApplicationsFilter)(implicit ctx: Ctx): Task[List[AppConfigDetails]] = {
    for {
      data <- ref.get
      res <- ZIO.succeed {
               val idsF      = filter.ids.map(_.toList.toSet)
               val projectsF = filter.projectIds.map(_.toList.toSet)
               data.values.toList
                 .flatMap(t => if (idsF.forall(_.contains(t.id))) t.some else None)
                 .flatMap(t => if (projectsF.forall(_.contains(t.projectId))) t.some else None)
             }
    } yield res.sortBy(_.name.value).take(filter.limit.map(_.value).getOrElse(res.size))
  }
  override def delete(id: AppId)(implicit ctx: Ctx): Task[Option[AppConfigDetails]] = {
    for {
      res <- ref.get.map(_.get(id))
      _   <- ZIO.when(res.nonEmpty)(log.info(s"deleted application: ${res.get.show}"))
      _   <- ref.update(_ - id)
    } yield res
  }
}

object ApplicationsServiceLive {
  val layer = (for {
    ref <- Ref.makeManaged(Map.empty[AppId, AppConfigDetails])
  } yield ApplicationsServiceLive(ref)).toLayer[ApplicationsService]
}

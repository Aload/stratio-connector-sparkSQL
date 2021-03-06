/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.stratio.connector.sparksql.core.engine

import akka.actor.ActorRef
import com.stratio.connector.commons.timer
import com.stratio.connector.sparksql.core.`package`.SparkSQLContext
import com.stratio.connector.sparksql.core.connection.ConnectionHandler
import com.stratio.connector.sparksql.core.engine.query.QueryManager
import com.stratio.connector.sparksql.core.engine.query.QueryEngine
import QueryManager.{Registered, Unregistered}
import com.stratio.connector.sparksql.core.providers
import com.stratio.crossdata.common.connector.IMetadataListener
import com.stratio.connector.commons.{Loggable, Metrics}
import com.stratio.crossdata.common.data.{TableName, Name}
import com.stratio.crossdata.common.metadata.{CatalogMetadata, UpdatableMetadata, TableMetadata}
import org.slf4j.Logger
import QueryEngine._
import scala.collection.JavaConversions._
/**
 * Hook for receiving metadata update events.
 */
object SparkSQLMetadataListener extends Loggable with Metrics {

  import timer._

  def apply(
    sqlContext: SparkSQLContext,
    connectionHandler: ConnectionHandler,
    queryManager: ActorRef): IMetadataListener =
    MetadataListener {
      case tableMetadata: TableMetadata =>
        logger.info(s"Received updated table metadata [$tableMetadata]")
        tableCallback(tableMetadata,connectionHandler,sqlContext, queryManager)
      case catalogMetadata: CatalogMetadata =>
        logger.info(s"Received updated catalog metadata [$catalogMetadata]")
        catalogMetadata.getTables.toMap.values.foreach(metadata =>
          tableCallback(metadata,connectionHandler,sqlContext,queryManager))
    } {
      case deletedTable: TableName =>
        logger.info(s"Received deleted table metadata [$deletedTable]")
        timeFor("Received deleted table metadata") {
          unregisterTable(
            deletedTable.getQualifiedName,
            sqlContext)
          queryManager ! Unregistered(deletedTable)
        }
    }

  private def tableCallback(
    tableMetadata: TableMetadata,
    connectionHandler: ConnectionHandler,
    sqlContext: SparkSQLContext,
    queryManager: ActorRef): Unit = {
    val clusterName = tableMetadata.getClusterRef
    val tableName = tableMetadata.getName
    timeFor("Received updated table metadata.") {
      logger.info(s"Registering table $tableName")
      for {
        connection <- connectionHandler.getConnection(clusterName.getName)
        provider <- providers.apply(connection.config.getDataStoreName.getName)
        _ <- registerTable(
          tableName.getName,
          sqlContext,
          provider,
          globalOptions(connection.config,tableMetadata) ++ tableMetadata.getOptions.toMap.map {
            case (k, v) => k.getStringValue -> v.getStringValue
          })
      } queryManager ! Registered(tableName)

    }
  }


}

/**
 * Helper for defining IMetadataListeners.
 * i.e.:{{{
 *   MetadataListener{
 *     metadataUpdated => //do whatever with updated metadata
 *   }{
 *     metadataDeleted => //do whatever with deleted metadata
 *   }
 * }}}
 */

object MetadataListener {

  type Callback[T] = PartialFunction[T, Unit]

  def apply(
    onUpdate: Callback[UpdatableMetadata])(
    onDelete: Callback[Name])(
    implicit logger: Logger): IMetadataListener =
    new IMetadataListener {

      override def updateMetadata(uMetadata: UpdatableMetadata): Unit =
        onUpdate.orElse(DoNothing[UpdatableMetadata]("on delete"))(uMetadata)

      override def deleteMetadata(uName: Name): Unit =
        onDelete.orElse(DoNothing[Name]("on update"))(uName)

    }

  private def DoNothing[T](when: String)(implicit logger: Logger): Callback[T] = {
    case metadata => logger.debug(s"Event $metadata not recognised $when")
  }

}
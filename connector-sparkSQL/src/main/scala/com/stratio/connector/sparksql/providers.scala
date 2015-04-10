/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.connector.sparksql

import com.stratio.connector.sparksql
import org.apache.spark.sql.DataFrame

/**
 * Router for every single supported provider.
 */
object providers {

  val Parquet = "parquet"

  def apply(providerName: String): Option[Provider] = providerName match {
    case Parquet => Some(sparksql.Parquet)
    case _ => None
  }

}

/**
 * Represents a custom SparkSQL Data Source.
 */
trait Provider {

  /**
   * DefaultSource qualifed package name
   */
  val datasource: String

  /**
   * Formats an Spark SQL obtained SchemaRDD,
   * adapting it to current provider format.
   * @param dataFrame DataFrame to be formatted
   * @return Formatted DataFrame
   */
  def formatRDD(
    dataFrame: DataFrame,
    sqlContext: SparkSQLContext): DataFrame = dataFrame

}

case object Parquet extends Provider {

  val datasource = "org.apache.spark.sql.parquet"

}
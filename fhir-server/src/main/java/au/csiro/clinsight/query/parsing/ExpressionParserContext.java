/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.clinsight.query.parsing;

import au.csiro.clinsight.TerminologyClient;
import org.apache.spark.sql.SparkSession;

/**
 * Contains dependencies for the execution of an ExpressionParser.
 *
 * @author John Grimes
 */
public class ExpressionParserContext {

  /**
   * The terminology client that should be used to resolve terminology queries within this
   * expression.
   */
  private TerminologyClient terminologyClient;

  /**
   * The Spark session that should be used to resolve Spark queries required for this expression.
   */
  private SparkSession sparkSession;

  /**
   * The name of the database to be accessed via Spark SQL.
   */
  private String databaseName;

  /**
   * A ParseResult representing the subject resource specified within the query, which is then
   * referred to through `%resource` or `%context`.
   */
  private ParseResult subjectResource;

  /**
   * The name of the table that corresponds to the subject resource.
   */
  private String fromTable;

  /**
   * An alias generator for generating aliases for use within SQL expressions.
   */
  private AliasGenerator aliasGenerator;

  public TerminologyClient getTerminologyClient() {
    return terminologyClient;
  }

  public void setTerminologyClient(TerminologyClient terminologyClient) {
    this.terminologyClient = terminologyClient;
  }

  public SparkSession getSparkSession() {
    return sparkSession;
  }

  public void setSparkSession(SparkSession sparkSession) {
    this.sparkSession = sparkSession;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public void setDatabaseName(String databaseName) {
    this.databaseName = databaseName;
  }

  public ParseResult getSubjectResource() {
    return subjectResource;
  }

  public void setSubjectResource(ParseResult subjectResource) {
    this.subjectResource = subjectResource;
  }

  public String getFromTable() {
    return fromTable;
  }

  public void setFromTable(String fromTable) {
    this.fromTable = fromTable;
  }

  public AliasGenerator getAliasGenerator() {
    return aliasGenerator;
  }

  public void setAliasGenerator(AliasGenerator aliasGenerator) {
    this.aliasGenerator = aliasGenerator;
  }
}
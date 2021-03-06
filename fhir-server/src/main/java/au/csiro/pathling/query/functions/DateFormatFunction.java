/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.query.functions;

import static au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType.DATE;
import static au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType.DATE_TIME;
import static au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType.STRING;
import static au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType.TIME;
import static org.apache.spark.sql.functions.date_format;

import au.csiro.pathling.query.parsing.ParsedExpression;
import au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;

/**
 * Describes a function which allows for the creation of formatted strings based upon dates, using
 * the syntax from the Java SimpleDateFormat class.
 *
 * @author John Grimes
 * @see <a href="https://spark.apache.org/docs/latest/api/java/org/apache/spark/sql/functions.html#date_format-org.apache.spark.sql.Column-java.lang.String-">https://spark.apache.org/docs/latest/api/java/org/apache/spark/sql/functions.html#date_format-org.apache.spark.sql.Column-java.lang.String-</a>
 */
public class DateFormatFunction implements Function {

  private static final Set<FhirPathType> supportedTypes = new HashSet<FhirPathType>() {{
    add(DATE);
    add(DATE_TIME);
    add(TIME);
  }};

  @Nonnull
  @Override
  public ParsedExpression invoke(@Nonnull FunctionInput input) {
    validateInput(input);
    ParsedExpression inputResult = input.getInput();
    ParsedExpression argument = input.getArguments().get(0);
    Dataset<Row> prevDataset = inputResult.getDataset();
    Column prevIdColumn = inputResult.getIdColumn();
    Column prevValueColumn = inputResult.getValueColumn();

    // Invoke the Spark SQL date_format function and store the result in a new Dataset.
    Column valueColumn;
    try {
      valueColumn = date_format(prevValueColumn, argument.getLiteralValue().toString());
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(
          "Invalid format string passed to dateFormat: " + argument.getFhirPath());
    }
    Dataset<Row> dataset = prevDataset.select(prevIdColumn, valueColumn);

    // Construct a new parse result.
    ParsedExpression result = new ParsedExpression();
    result.setFhirPath(input.getExpression());
    result.setFhirPathType(FhirPathType.INTEGER);
    result.setFhirType(FHIRDefinedType.INTEGER);
    result.setPrimitive(true);
    result.setSingular(inputResult.isSingular());
    result.setDataset(dataset);
    result.setIdColumn(prevIdColumn);
    result.setValueColumn(valueColumn);

    return result;
  }

  private void validateInput(FunctionInput input) {
    if (input.getArguments().size() != 1
        || input.getArguments().get(0).getFhirPathType() != STRING) {
      throw new InvalidRequestException(
          "dateFormat function accepts one argument of type String: " + input.getExpression());
    }

    ParsedExpression inputResult = input.getInput();
    if (!supportedTypes.contains(inputResult.getFhirPathType())) {
      throw new InvalidRequestException(
          "Input to dateFormat function is of unsupported type: " + inputResult
              .getFhirPath());
    }
  }

}

/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.clinsight.query.functions;

import static au.csiro.clinsight.fhir.definitions.ResolvedElement.ResolvedElementType.PRIMITIVE;
import static au.csiro.clinsight.query.parsing.ParseResult.ParseResultType.STRING;

import au.csiro.clinsight.TerminologyClient;
import au.csiro.clinsight.query.parsing.ParseResult;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.spark.sql.SparkSession;

/**
 * @author John Grimes
 */
public class DateFormatFunction implements ExpressionFunction {

  private static final Set<String> supportedTypes = new HashSet<String>() {{
    add("instant");
    add("dateTime");
    add("date");
    add("time");
  }};

  @Nonnull
  @Override
  public ParseResult invoke(@Nullable ParseResult input, @Nonnull List<ParseResult> arguments) {
    validateInput(input);
    ParseResult parseResult = validateArgument(arguments);
    String newSqlExpression =
        "date_format(" + input.getSqlExpression() + ", " + parseResult.getExpression() + ")";
    input.setSqlExpression(newSqlExpression);
    input.setResultType(STRING);
    input.setElementType(null);
    input.setElementTypeCode(null);
    return input;
  }

  private void validateInput(ParseResult input) {
    if (input == null || input.getSqlExpression() == null || input.getSqlExpression().isEmpty()) {
      throw new InvalidRequestException("Missing input expression for dateFormat function");
    }
    if (input.getElementType() != PRIMITIVE || !supportedTypes
        .contains(input.getElementTypeCode())) {
      throw new InvalidRequestException(
          "Input to dateFormat function must be instant, dateTime, date or time");
    }
  }

  private ParseResult validateArgument(List<ParseResult> arguments) {
    if (arguments.size() != 1) {
      throw new InvalidRequestException("Must pass format argument to dateFormat function");
    }
    ParseResult argument = arguments.get(0);
    if (argument.getResultType() != STRING) {
      throw new InvalidRequestException(
          "Argument to dateFormat function must be a string: " + argument.getExpression());
    }
    return argument;
  }

  @Override
  public void setTerminologyClient(@Nonnull TerminologyClient terminologyClient) {
  }

  @Override
  public void setSparkSession(@Nonnull SparkSession spark) {
  }
}

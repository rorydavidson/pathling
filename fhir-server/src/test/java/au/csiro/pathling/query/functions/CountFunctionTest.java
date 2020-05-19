/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.query.functions;

import static au.csiro.pathling.TestUtilities.getSparkSession;
import static au.csiro.pathling.test.Assertions.assertThat;
import static au.csiro.pathling.test.fixtures.PrimitiveRowFixture.ROW_ID_1;
import static au.csiro.pathling.test.fixtures.PrimitiveRowFixture.ROW_ID_2;
import static au.csiro.pathling.test.fixtures.PrimitiveRowFixture.ROW_ID_4;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.csiro.pathling.query.parsing.ParsedExpression;
import au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType;
import au.csiro.pathling.query.parsing.parser.ExpressionParserContext;
import au.csiro.pathling.test.DatasetBuilder;
import au.csiro.pathling.test.PrimitiveExpressionBuilder;
import au.csiro.pathling.test.ResourceExpressionBuilder;
import au.csiro.pathling.test.fixtures.PatientResourceRowFixture;
import au.csiro.pathling.test.fixtures.StringPrimitiveRowFixture;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;


/**
 * @author John Grimes
 */

@Category(au.csiro.pathling.UnitTest.class)
public class CountFunctionTest {

  private SparkSession spark;

  @Before
  public void setUp() {
    spark = getSparkSession();
  }

  @Test
  public void testCountsResourcesCorrectly() {
    // Build a Dataset with several rows in it.
    Dataset<Row> dataset = PatientResourceRowFixture.createCompleteDataset(spark);
    // Build up an input for the function.
    ParsedExpression input =
        new ResourceExpressionBuilder(ResourceType.PATIENT, FHIRDefinedType.PATIENT)
            .withDataset(dataset).build();
    ExpressionParserContext parserContext = mock(ExpressionParserContext.class);

    FunctionInput functionInput = new FunctionInput();
    functionInput.setInput(input);
    functionInput.setExpression("count()");
    functionInput.setContext(parserContext);

    // Execute the first function.
    Function function = new CountFunction();
    ParsedExpression result = function.invoke(functionInput);

    assertThat(result)
        .isResultFor(functionInput)
        .isOfType(FHIRDefinedType.UNSIGNEDINT, FhirPathType.INTEGER)
        .isPrimitive()
        .isSingular()
        .isSelection()
        .isAggregation();

    // Check results.
    assertThat(result).aggResult().isValue().isEqualTo(3L);
    assertThat(result).aggByIdResult().hasRows(PatientResourceRowFixture.allPatientsWithValue(1L));
    assertThat(result).selectResult().hasRows(PatientResourceRowFixture.allPatientsWithValue(1L));
  }

  @Test
  public void testCountsElementsCorrectly() {
    Dataset<Row> dataset = StringPrimitiveRowFixture.createCompleteDataset(spark);

    // Build up an input for the function.
    ParsedExpression input =
        new PrimitiveExpressionBuilder(FHIRDefinedType.STRING, FhirPathType.STRING)
            .withDataset(dataset).build();
    ExpressionParserContext parserContext = mock(ExpressionParserContext.class);

    FunctionInput functionInput = new FunctionInput();
    functionInput.setInput(input);
    functionInput.setExpression("name.family.count()");
    functionInput.setContext(parserContext);

    // Execute the count function.
    ParsedExpression result = new CountFunction().invoke(functionInput);

    assertThat(result)
        .isResultFor(functionInput)
        .isOfType(FHIRDefinedType.UNSIGNEDINT, FhirPathType.INTEGER)
        .isPrimitive()
        .isSingular()
        .isSelection()
        .isAggregation();

    DatasetBuilder expectedRows = StringPrimitiveRowFixture.allStringsWithValue(0L)
        .changeValue(ROW_ID_1, 1L)
        .changeValue(ROW_ID_2, 2L)
        .changeValue(ROW_ID_4, 2L);

    // Check the results of the aggregation.
    assertThat(result).aggResult().isValue().isEqualTo(5L);
    assertThat(result).aggByIdResult().hasRows(expectedRows);

    // Check the results of the selection.
    assertThat(result).selectResult().hasRows(expectedRows);
  }

  @Test
  public void preservesInputValueInThisContext() {
    // Build a Dataset with several rows in it.
    Dataset<Row> dataset = PatientResourceRowFixture.createCompleteDataset(spark);
    // Build up an input for the function.
    ParsedExpression input =
        new ResourceExpressionBuilder(ResourceType.PATIENT, FHIRDefinedType.PATIENT)
            .withDataset(dataset).build();
    ExpressionParserContext parserContext = mock(ExpressionParserContext.class);
    when(parserContext.getThisContext()).thenReturn(input);

    FunctionInput functionInput = new FunctionInput();
    functionInput.setInput(input);
    functionInput.setExpression("count()");
    functionInput.setContext(parserContext);

    // Execute the first function.
    Function function = new CountFunction();
    ParsedExpression result = function.invoke(functionInput);

    assertThat(result)
        .isResultFor(functionInput)
        .isOfType(FHIRDefinedType.UNSIGNEDINT, FhirPathType.INTEGER)
        .isPrimitive()
        .isSingular()
        .isSelection()
        .isAggregation();

    // Check results.
    assertThat(result).aggResult().isValue().isEqualTo(3L);
    assertThat(result).aggByIdResult().hasRows(PatientResourceRowFixture.allPatientsWithValue(1L));
    assertThat(result).selectResult().hasRows(PatientResourceRowFixture.allPatientsWithValue(1L));

    // Check that input value has been preserved.
    Dataset<Row> inputValueSelected = result.getDataset()
        .select(result.getIdColumn(), input.getValueColumn());
    assertThat(inputValueSelected).hasRows(PatientResourceRowFixture.createCompleteDataset(spark));
  }

  @Test
  public void inputMustNotContainArguments() {
    // Build up an input for the function.
    ExpressionParserContext expressionParserContext = new ExpressionParserContext();
    expressionParserContext.getGroupings().add(mock(ParsedExpression.class));

    FunctionInput countInput = new FunctionInput();
    countInput.setInput(mock(ParsedExpression.class));
    countInput.setExpression("count('some argument')");
    countInput.setContext(expressionParserContext);
    countInput.getArguments().add(mock(ParsedExpression.class));

    // Execute the function and assert that it throws the right exception.
    CountFunction countFunction = new CountFunction();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> countFunction.invoke(countInput))
        .withMessage("Arguments can not be passed to count function: count('some argument')");
  }

}

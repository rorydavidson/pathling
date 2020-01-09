/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.pathling.query;

import static au.csiro.pathling.TestUtilities.checkExpectedJson;
import static au.csiro.pathling.TestUtilities.getJsonParser;
import static au.csiro.pathling.TestUtilities.getResourceAsStream;
import static au.csiro.pathling.TestUtilities.getSparkSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.csiro.pathling.TestUtilities;
import au.csiro.pathling.fhir.TerminologyClient;
import au.csiro.pathling.fhir.TerminologyClientFactory;
import au.csiro.pathling.query.AggregateRequest.Aggregation;
import au.csiro.pathling.query.AggregateRequest.Grouping;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Parameters;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

/**
 * @author John Grimes
 */
@Category(au.csiro.pathling.UnitTest.class)
public class AggregateExecutorTest {

  private AggregateExecutor executor;
  private SparkSession spark;
  private ResourceReader mockReader;
  private TerminologyClient terminologyClient;

  @Before
  public void setUp() throws IOException {
    spark = getSparkSession();

    terminologyClient = mock(TerminologyClient.class, Mockito.withSettings().serializable());
    TerminologyClientFactory terminologyClientFactory =
        mock(TerminologyClientFactory.class, Mockito.withSettings().serializable());
    when(terminologyClientFactory.build(any())).thenReturn(terminologyClient);

    Path warehouseDirectory = Files.createTempDirectory("pathling-test-");
    mockReader = mock(ResourceReader.class);

    // Create and configure a new AggregateExecutor.
    AggregateExecutorConfiguration config = new AggregateExecutorConfiguration(spark,
        TestUtilities.getFhirContext(), terminologyClientFactory, terminologyClient, mockReader);
    config.setWarehouseUrl(warehouseDirectory.toString());
    config.setDatabaseName("test");

    executor = new AggregateExecutor(config);
  }

  @Test
  public void queryWithMultipleGroupings() throws IOException, JSONException {
    mockResourceReader(ResourceType.ENCOUNTER);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.ENCOUNTER);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of encounters");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Class");
    grouping1.setExpression("class.code");
    request.getGroupings().add(grouping1);

    Grouping grouping2 = new Grouping();
    grouping2.setLabel("Reason");
    grouping2.setExpression("reasonCode.coding.display");
    request.getGroupings().add(grouping2);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithMultipleGroupings.Parameters.json");
  }

  @Test
  public void queryWithFilter() throws IOException, JSONException {
    mockResourceReader(ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Gender");
    grouping1.setExpression("gender");
    request.getGroupings().add(grouping1);

    request.getFilters().add("gender = 'female'");

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithFilter.Parameters.json");
  }

  @Test
  public void queryWithIntegerGroupings() throws IOException, JSONException {
    mockResourceReader(ResourceType.CLAIM);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.CLAIM);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of claims");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping = new Grouping();
    grouping.setLabel("Claim item sequence");
    grouping.setExpression("item.sequence");
    request.getGroupings().add(grouping);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithIntegerGroupings.Parameters.json");
  }

  @Test
  public void queryWithMathExpression() throws IOException, JSONException {
    mockResourceReader(ResourceType.CLAIM);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.CLAIM);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of claims");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping = new Grouping();
    grouping.setLabel("First claim item sequence + 1");
    grouping.setExpression("item.sequence.first() + 1");
    request.getGroupings().add(grouping);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithMathExpression.Parameters.json");
  }

  @Test
  public void queryWithChoiceElement() throws IOException, JSONException {
    mockResourceReader(ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping = new Grouping();
    grouping.setLabel("Multiple birth?");
    grouping.setExpression("multipleBirthBoolean");
    request.getGroupings().add(grouping);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithChoiceElement.Parameters.json");
  }

  @Test
  public void queryWithDateComparison() throws IOException, JSONException {
    mockResourceReader(ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    request.getFilters().add("birthDate > @1980 and birthDate < @1990");

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithDateComparison.Parameters.json");
  }

  @Test
  public void queryWithResolve() throws IOException, JSONException {
    mockResourceReader(ResourceType.ALLERGYINTOLERANCE, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.ALLERGYINTOLERANCE);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of allergies");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Patient gender");
    grouping1.setExpression("patient.resolve().gender");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithResolve.Parameters.json");
  }

  @Test
  public void queryWithPolymorphicResolve() throws IOException, JSONException {
    mockResourceReader(ResourceType.DIAGNOSTICREPORT, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.DIAGNOSTICREPORT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of reports");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Patient active status");
    grouping1.setExpression("subject.resolve().ofType(Patient).gender");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithPolymorphicResolve.Parameters.json");
  }

  @Test
  public void queryWithReverseResolve() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Condition");
    grouping1.setExpression("reverseResolve(Condition.subject).code.coding.display");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithReverseResolve.Parameters.json");
  }


  @Test
  public void queryWithReverseResolveAndCounts() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Condition");
    grouping1.setExpression("reverseResolve(Condition.subject).code.coding.count()");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithReverseResolveAndCounts.Parameters.json");
  }

  @Test
  public void queryMultipleGroupingCounts() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Given name");
    grouping1.setExpression("name.given");
    request.getGroupings().add(grouping1);

    Grouping grouping2 = new Grouping();
    grouping2.setLabel("Name prefix");
    grouping2.setExpression("name.prefix");
    request.getGroupings().add(grouping2);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryMultipleGroupingCounts.Parameters.json");
  }


  @Test
  @Ignore
  /**
   * TODO: Make mulitple aggreations work without producing cartesian product of values to count (per resource) 
   * @throws IOException
   * @throws JSONException
   */
  public void queryMultipleCountAggregations() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patient names");
    aggregation.setExpression("name.given.count()");
    request.getAggregations().add(aggregation);

    aggregation.setLabel("Number of patient prefixes");
    aggregation.setExpression("identifier.count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Gender");
    grouping1.setExpression("gender");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);

    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryMultipleCountAggregations.Parameters.json");
  }

  @Test
  public void queryWithWhere() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping = new Grouping();
    grouping.setLabel("2010 condition verification status");
    grouping.setExpression(
        "reverseResolve(Condition.subject).where($this.onsetDateTime > @2010 and $this.onsetDateTime < @2011).verificationStatus.coding.code");
    request.getGroupings().add(grouping);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithWhere.Parameters.json");
  }

  @Test
  public void queryWithMemberOf() throws IOException, JSONException {
    mockResourceReader(ResourceType.CONDITION, ResourceType.PATIENT);
    Bundle mockResponse = (Bundle) TestUtilities.getJsonParser().parseResource(getResourceAsStream(
        "txResponses/MemberOfFunctionTest-memberOfCoding-validate-code-positive.Bundle.json"));

    // Mock out responses from the terminology server.
    when(terminologyClient.batch(any(Bundle.class))).thenReturn(mockResponse);

    // Build a AggregateRequest to pass to the executor.
    AggregateRequest request = new AggregateRequest();
    request.setSubjectResource(ResourceType.PATIENT);

    Aggregation aggregation = new Aggregation();
    aggregation.setLabel("Number of patients");
    aggregation.setExpression("count()");
    request.getAggregations().add(aggregation);

    Grouping grouping1 = new Grouping();
    grouping1.setLabel("Condition in ED diagnosis reference set?");
    String valueSetUrl = "http://snomed.info/sct?fhir_vs=refset/32570521000036109";
    grouping1.setExpression(
        "reverseResolve(Condition.subject)" + ".code" + ".memberOf('" + valueSetUrl + "'");
    request.getGroupings().add(grouping1);

    // Execute the query.
    AggregateResponse response = executor.execute(request);

    // Check the response against an expected response.
    Parameters responseParameters = response.toParameters();
    String actualJson = getJsonParser().encodeResourceToString(responseParameters);
    checkExpectedJson(actualJson,
        "responses/AggregateExecutorTest-queryWithMemberOf.Parameters.json");
  }

  private void mockResourceReader(ResourceType... resourceTypes) throws MalformedURLException {
    for (ResourceType resourceType : resourceTypes) {
      File parquetFile =
          new File("src/test/resources/test-data/parquet/" + resourceType.toCode() + ".parquet");
      URL parquetUrl = parquetFile.getAbsoluteFile().toURI().toURL();
      assertThat(parquetUrl).isNotNull();
      Dataset<Row> dataset = spark.read().parquet(parquetUrl.toString());
      when(mockReader.read(resourceType)).thenReturn(dataset);
      when(mockReader.getAvailableResourceTypes())
          .thenReturn(new HashSet<>(Arrays.asList(resourceTypes)));
    }
  }

}

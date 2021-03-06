/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.query.parsing;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;
import static org.apache.spark.sql.functions.when;

import java.util.function.BiFunction;
import org.apache.spark.sql.Column;
import org.hl7.fhir.r4.model.Coding;

public class CodingFhirPathTypeSqlHelper implements FhirPathTypeSqlHelper {


  public static final CodingFhirPathTypeSqlHelper INSTANCE = new CodingFhirPathTypeSqlHelper();

  @Override
  public Column getLiteralColumn(ParsedExpression expression) {
    Coding coding = (Coding) expression.getJavaLiteralValue();
    return struct(
        lit(coding.getId()).alias("id"),
        lit(coding.getSystem()).alias("system"),
        lit(coding.getVersion()).alias("version"),
        lit(coding.getCode()).alias("code"),
        lit(coding.getDisplay()).alias("display"),
        lit(coding.getUserSelected()).alias("userSelected")
    );
  }

  @Override
  public BiFunction<Column, Column, Column> getEquality() {
    // When comparing Codings, we base the comparison on the highest common level of precision,
    // i.e. if both Codings have a version, we compare them - otherwise, we ignore the version.
    return (left, right) -> {
      Column incompleteCodingTest = left.getField("system").isNull()
          .or(left.getField("code").isNull())
          .or(right.getField("system").isNull())
          .or(right.getField("code").isNull());
      Column missingVersionTest = left.getField("version").isNull()
          .or(right.getField("version").isNull());
      Column versionAgnosticTest = left.getField("system")
          .equalTo(right.getField("system"))
          .and(left.getField("code").equalTo(right.getField("code")));
      Column fullEqualityTest = versionAgnosticTest
          .and(left.getField("version").equalTo(right.getField("version")));
      return when(incompleteCodingTest, null)
          .when(missingVersionTest, versionAgnosticTest)
          .otherwise(fullEqualityTest);
    };
  }

}

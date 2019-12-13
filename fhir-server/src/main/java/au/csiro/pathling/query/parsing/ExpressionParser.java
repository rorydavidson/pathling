/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.pathling.query.parsing;

import static au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType.CODING;
import static au.csiro.pathling.utilities.Strings.md5Short;
import static au.csiro.pathling.utilities.Strings.unSingleQuote;
import static au.csiro.pathling.utilities.Strings.unescapeFhirPathString;

import au.csiro.pathling.encoding.IdAndBoolean;
import au.csiro.pathling.fhir.FhirPathBaseVisitor;
import au.csiro.pathling.fhir.FhirPathLexer;
import au.csiro.pathling.fhir.FhirPathParser;
import au.csiro.pathling.fhir.FhirPathParser.*;
import au.csiro.pathling.query.functions.Function;
import au.csiro.pathling.query.functions.FunctionInput;
import au.csiro.pathling.query.operators.BinaryOperator;
import au.csiro.pathling.query.operators.BinaryOperatorInput;
import au.csiro.pathling.query.operators.PathTraversalInput;
import au.csiro.pathling.query.operators.PathTraversalOperator;
import au.csiro.pathling.query.parsing.ParsedExpression.FhirPathType;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.spark.sql.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;

/**
 * This is an ANTLR-based parser for processing a FHIRPath expression, and aggregating the results
 * into a ParseResult object.
 *
 * @author John Grimes
 */
public class ExpressionParser {

  private final ExpressionParserContext context;

  public ExpressionParser(ExpressionParserContext context) {
    this.context = context;
  }

  public ParsedExpression parse(String expression) {
    FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expression));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    FhirPathParser parser = new FhirPathParser(tokens);

    ExpressionVisitor expressionVisitor = new ExpressionVisitor(context);
    return expressionVisitor.visit(parser.expression());
  }

  /**
   * This class processes all types of expressions, and delegates the special handling of supported
   * types to the more specific visitor classes.
   */
  private static class ExpressionVisitor extends FhirPathBaseVisitor<ParsedExpression> {

    final ExpressionParserContext context;

    ExpressionVisitor(ExpressionParserContext context) {
      this.context = context;
    }

    /**
     * A term is typically a standalone literal or function invocation.
     */
    @Override
    public ParsedExpression visitTermExpression(TermExpressionContext ctx) {
      return ctx.term().accept(new TermVisitor(context));
    }

    /**
     * An invocation expression is one expression invoking another using the dot notation.
     */
    @Override
    public ParsedExpression visitInvocationExpression(InvocationExpressionContext ctx) {
      ParsedExpression expressionResult = new ExpressionVisitor(context)
          .visit(ctx.expression());
      // The invoking expression is passed through to the invocation visitor's constructor - this
      // will provide it with extra context required to do things like merging in joins from the
      // upstream path.
      ParsedExpression invocationResult = ctx.invocation()
          .accept(new InvocationVisitor(context, expressionResult));
      invocationResult
          .setFhirPath(expressionResult.getFhirPath() + "." + invocationResult.getFhirPath());
      return invocationResult;
    }

    @Nonnull
    private ParsedExpression visitBinaryOperator(ExpressionContext leftExpression,
        ExpressionContext rightExpression,
        String operatorString) {
      // Parse the left and right expressions.
      ParsedExpression leftResult = new ExpressionVisitor(context)
          .visit(leftExpression);
      ParsedExpression rightResult = new ExpressionVisitor(context)
          .visit(rightExpression);
      String fhirPath =
          leftResult.getFhirPath() + " " + operatorString + " " + rightResult.getFhirPath();

      // Retrieve a BinaryOperator object based upon the operator.
      BinaryOperator operator = BinaryOperator.getBinaryOperator(operatorString);

      // Collect the input information.
      BinaryOperatorInput input = new BinaryOperatorInput();
      input.setLeft(leftResult);
      input.setRight(rightResult);
      input.setExpression(fhirPath);
      input.setContext(context);

      // Return the result.
      return operator.invoke(input);
    }

    @Override
    public ParsedExpression visitEqualityExpression(EqualityExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    @Override
    public ParsedExpression visitInequalityExpression(InequalityExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    @Override
    public ParsedExpression visitAndExpression(AndExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    @Override
    public ParsedExpression visitOrExpression(OrExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    @Override
    public ParsedExpression visitImpliesExpression(ImpliesExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    @Override
    public ParsedExpression visitMembershipExpression(MembershipExpressionContext ctx) {
      return visitBinaryOperator(ctx.expression(0), ctx.expression(1),
          ctx.children.get(1).toString());
    }

    // All other FHIRPath constructs are currently unsupported.

    @Override
    public ParsedExpression visitIndexerExpression(IndexerExpressionContext ctx) {
      throw new InvalidRequestException("Indexer operation is not supported");
    }

    @Override
    public ParsedExpression visitPolarityExpression(PolarityExpressionContext ctx) {
      throw new InvalidRequestException("Polarity operator is not supported");
    }

    @Override
    public ParsedExpression visitMultiplicativeExpression(MultiplicativeExpressionContext ctx) {
      throw new InvalidRequestException("Multiplicative expressions are not supported");
    }

    @Override
    public ParsedExpression visitAdditiveExpression(AdditiveExpressionContext ctx) {
      throw new InvalidRequestException("Additive expressions are not supported");
    }

    @Override
    public ParsedExpression visitUnionExpression(UnionExpressionContext ctx) {
      throw new InvalidRequestException("Union expressions are not supported");
    }

    @Override
    public ParsedExpression visitTypeExpression(TypeExpressionContext ctx) {
      throw new InvalidRequestException("Type expressions are not supported");
    }

  }

  /**
   * A term is typically a standalone literal or function invocation.
   */
  private static class TermVisitor extends FhirPathBaseVisitor<ParsedExpression> {

    final ExpressionParserContext context;

    TermVisitor(ExpressionParserContext context) {
      this.context = context;
    }

    /**
     * This passes a standalone function invocation along to the invocation visitor. Note that most
     * functions will require an input, and will fail validation later on in this instance.
     */
    @Override
    public ParsedExpression visitInvocationTerm(InvocationTermContext ctx) {
      return new InvocationVisitor(context).visit(ctx.invocation());
    }

    /**
     * We pass literals as is through to the literal visitor.
     */
    @Override
    public ParsedExpression visitLiteralTerm(LiteralTermContext ctx) {
      return new LiteralTermVisitor(context).visit(ctx.literal());
    }

    @Override
    public ParsedExpression visitExternalConstantTerm(ExternalConstantTermContext ctx) {
      if (ctx.getText().equals("%resource") || ctx.getText().equals("%context")) {
        ParsedExpression result = context.getSubjectContext();
        result.setFhirPath(ctx.getText());
        result.setOrigin(result);
        return result;
      } else {
        throw new InvalidRequestException("Unrecognised environment variable: " + ctx.getText());
      }
    }

    /**
     * Parentheses are ignored in the standalone term case.
     */
    @Override
    public ParsedExpression visitParenthesizedTerm(ParenthesizedTermContext ctx) {
      final ParsedExpression result = new ExpressionVisitor(context)
          .visit(ctx.expression());
      result.setFhirPath(ctx.getText());
      return result;
    }

  }

  /**
   * This class is invoked on the right-hand side of the invocation expression, and can optionally
   * be constructed with an invoker expression to allow it to operate with knowledge of this
   * context.
   */
  private static class InvocationVisitor extends FhirPathBaseVisitor<ParsedExpression> {

    final ExpressionParserContext context;
    ParsedExpression invoker;

    InvocationVisitor(ExpressionParserContext context) {
      this.context = context;
      invoker = context.getSubjectContext();
    }

    InvocationVisitor(ExpressionParserContext context,
        ParsedExpression invoker) {
      this.context = context;
      this.invoker = invoker;
    }

    /**
     * This method gets called when an element is on the right-hand side of the invocation
     * expression, or when an identifier is referred to as a term (e.g. Encounter).
     */
    @Override
    public ParsedExpression visitMemberInvocation(MemberInvocationContext ctx) {
      String fhirPath = ctx.getText();

      // If there is no invoker, we assume that this is a base resource. If we can't resolve it,
      // an error will be thrown.
      String hash = md5Short(fhirPath);
      try {
        //noinspection ResultOfMethodCallIgnored
        ResourceType.fromCode(fhirPath);
      } catch (FHIRException e) {
        // If the expression is not a base resource type, treat it as a path traversal from the
        // subject resource.
        PathTraversalInput pathTraversalInput = new PathTraversalInput();
        pathTraversalInput.setLeft(invoker);
        pathTraversalInput.setRight(fhirPath);
        pathTraversalInput.setExpression(fhirPath);
        pathTraversalInput.setContext(context);
        return new PathTraversalOperator().invoke(pathTraversalInput);
      }

      // Build a new parse result to represent the resource.
      ParsedExpression result = new ParsedExpression();
      result.setFhirPath(fhirPath);
      result.setResource(true);
      result.setResourceType(Enumerations.ResourceType.fromCode(fhirPath));
      result.setOrigin(result);

      // Add a dataset to the parse result representing the nominated resource.
      Dataset<Row> dataset = context.getResourceReader().read(result.getResourceType());
      String firstColumn = dataset.columns()[0];
      String[] remainingColumns = Arrays
          .copyOfRange(dataset.columns(), 1, dataset.columns().length);
      dataset = dataset
          .withColumn(hash, functions.struct(firstColumn, remainingColumns));
      Column idColumn = dataset.col("id");
      Column valueColumn = dataset.col(hash);
      dataset = dataset.select(idColumn, valueColumn);
      result.setDataset(dataset);
      result.setHashedValue(idColumn, valueColumn);

      return result;
    }

    /**
     * This method gets called when a function call is on the right-hand side of an invocation
     * expression. It basically just checks that the function is known, and invokes it to get a new
     * ParseResult.
     */
    @Override
    public ParsedExpression visitFunctionInvocation(FunctionInvocationContext ctx) {
      // First, check if the function is supported.
      String functionIdentifier = ctx.functn().identifier().getText();
      Function function = Function.getFunction(functionIdentifier);
      if (function == null) {
        throw new InvalidRequestException("Unrecognised function: " + functionIdentifier);
      }
      return invokeFunction(ctx, function);
    }

    @Nonnull
    private ParsedExpression invokeFunction(FunctionInvocationContext ctx, Function function) {
      // Get the parse results for each of the expressions that make up the functions arguments.
      List<ParsedExpression> arguments;
      ParamListContext paramList = ctx.functn().paramList();
      if (paramList == null) {
        arguments = new ArrayList<>();
      } else {
        // Create a new ExpressionParserContext, which includes information about how to evaluate
        // the `$this` expression.
        ExpressionParserContext argumentContext = new ExpressionParserContext(context);
        ParsedExpression thisResult = new ParsedExpression(invoker);
        thisResult.setFhirPath("$this");
        argumentContext.setThisContext(thisResult);
        // Parse each of the expressions passed as arguments to the function.
        arguments = paramList.expression().stream()
            .map(expression -> new ExpressionVisitor(argumentContext).visit(expression))
            .collect(Collectors.toList());
      }

      // Build a function input that passes the arguments as ParseResults.
      FunctionInput functionInput = new FunctionInput();
      functionInput.setContext(context);
      functionInput.setExpression(ctx.getText());
      functionInput.setInput(invoker);
      functionInput.getArguments().addAll(arguments);

      // Invoke the function and return the result.
      return function.invoke(functionInput);
    }

    @Override
    public ParsedExpression visitThisInvocation(ThisInvocationContext ctx) {
      if (context.getThisContext() == null) {
        throw new InvalidRequestException(
            "$this can only be used within the context of arguments to a function");
      }
      return context.getThisContext();
    }

  }

  private static class LiteralTermVisitor extends FhirPathBaseVisitor<ParsedExpression> {

    final ExpressionParserContext context;

    LiteralTermVisitor(ExpressionParserContext context) {
      this.context = context;
    }

    @Override
    public ParsedExpression visitCodingLiteral(CodingLiteralContext ctx) {
      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(CODING);
      result.setFhirPath(ctx.getText());
      LinkedList<String> codingTokens = new LinkedList<>(Arrays.asList(ctx.getText().split("\\|")));
      Coding literalValue;
      if (codingTokens.size() == 2) {
        literalValue = new Coding(codingTokens.get(0), codingTokens.get(1), null);
      } else if (codingTokens.size() == 3) {
        literalValue = new Coding(codingTokens.get(0), codingTokens.get(2), null);
        literalValue.setVersion(codingTokens.get(1));
      } else {
        throw new InvalidRequestException(
            "Coding literal must be of form [system]|[code] or [system]|[version]|[code]");
      }
      result.setLiteralValue(literalValue);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitStringLiteral(StringLiteralContext ctx) {
      // Remove the surrounding single quotes and unescape the string according to the rules within
      // the FHIRPath specification.
      String value = unSingleQuote(ctx.getText());
      value = unescapeFhirPathString(value);

      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(FhirPathType.STRING);
      result.setFhirType(FHIRDefinedType.STRING);
      result.setFhirPath(ctx.getText());
      result.setLiteralValue(new StringType(value));
      result.setPrimitive(true);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitDateTimeLiteral(DateTimeLiteralContext ctx) {
      // Parse the string according to ISO8601.
      Date value;
      try {
        value = ParsedExpression.DATE_TIME_FORMAT.parse(ctx.getText().replace("@", ""));
      } catch (ParseException e) {
        throw new InvalidRequestException("Invalid DateTime literal: " + ctx.getText());
      }

      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(FhirPathType.DATE_TIME);
      result.setFhirType(FHIRDefinedType.STRING);
      result.setFhirPath(ctx.getText());
      result.setLiteralValue(new DateTimeType(value));
      result.setPrimitive(true);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitTimeLiteral(TimeLiteralContext ctx) {
      // Parse the string according to ISO8601.
      String timeString = ctx.getText().replace("@", "");
      try {
        ParsedExpression.TIME_FORMAT.parse(timeString);
      } catch (ParseException e) {
        throw new InvalidRequestException("Invalid Time literal: " + ctx.getText());
      }

      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(FhirPathType.TIME);
      result.setFhirType(FHIRDefinedType.TIME);
      result.setFhirPath(ctx.getText());
      result.setLiteralValue(new TimeType(timeString));
      result.setPrimitive(true);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitNumberLiteral(NumberLiteralContext ctx) {
      // Try to parse an integer out of the string. If this fails, try and parse a decimal value out
      // of the string.
      Type value;
      try {
        value = new IntegerType(Integer.parseInt(ctx.getText()));
      } catch (NumberFormatException e) {
        value = new DecimalType(new BigDecimal(ctx.getText()));
      }

      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(FhirPathType.INTEGER);
      result.setFhirType(FHIRDefinedType.INTEGER);
      result.setFhirPath(ctx.getText());
      result.setLiteralValue(value);
      result.setPrimitive(true);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitBooleanLiteral(BooleanLiteralContext ctx) {
      boolean value = ctx.getText().equals("true");

      ParsedExpression result = new ParsedExpression();
      result.setFhirPathType(FhirPathType.BOOLEAN);
      result.setFhirType(FHIRDefinedType.BOOLEAN);
      result.setFhirPath(ctx.getText());
      result.setLiteralValue(new BooleanType(value));
      result.setPrimitive(true);
      result.setSingular(true);
      return result;
    }

    @Override
    public ParsedExpression visitNullLiteral(NullLiteralContext ctx) {
      // Create an empty dataset with and ID and value column.
      Dataset<Row> dataset = context.getSparkSession()
          .emptyDataset(Encoders.bean(IdAndBoolean.class)).toDF();
      Column idColumn = dataset.col(dataset.columns()[0]);
      Column valueColumn = dataset.col(dataset.columns()[1]);
      dataset = dataset.select(idColumn, valueColumn);

      ParsedExpression result = new ParsedExpression();
      result.setFhirPath(ctx.getText());
      result.setDataset(dataset);
      result.setIdColumn(idColumn);
      result.setValueColumn(valueColumn);
      return result;
    }

    @Override
    public ParsedExpression visitQuantityLiteral(QuantityLiteralContext ctx) {
      throw new InvalidRequestException("Quantity literals are not supported");
    }

  }

}
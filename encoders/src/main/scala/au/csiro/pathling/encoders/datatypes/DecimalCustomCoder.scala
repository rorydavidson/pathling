package au.csiro.pathling.encoders.datatypes

import au.csiro.pathling.encoders.datatypes.DecimalCustomCoder.decimalType
import org.apache.spark.sql.catalyst.expressions.objects.{Invoke, NewInstance, StaticInvoke}
import org.apache.spark.sql.catalyst.expressions.{Expression, Literal}
import org.apache.spark.sql.types._
import org.hl7.fhir.r4.model.DecimalType


/**
 * Custom coder for DecimalType.
 * Represents decimal on two dataset columns: the actual DecimalType value and
 * an additional IntegerType column with `_scale` suffix storing the scale of the
 * original FHIR decimal value.
 *
 * @param elementName
 */
case class DecimalCustomCoder(elementName: String) extends CustomCoder {

  def primitiveClass: Class[DecimalType] = classOf[DecimalType]

  val scaleFieldName = elementName + "_scale"

  override val schema: Seq[StructField] = Seq(StructField(elementName, decimalType), StructField(scaleFieldName, IntegerType))

  override def customDeserializer(addToPath: String => Expression): Map[String, Expression] = {
    val expression = NewInstance(primitiveClass,
      Invoke(
        Invoke(addToPath(elementName), "toJavaBigDecimal", ObjectType(classOf[java.math.BigDecimal])),
        "setScale", ObjectType(classOf[java.math.BigDecimal]), addToPath(scaleFieldName) :: Nil
      ) :: Nil,
      ObjectType(primitiveClass)
    )
    Map(elementName -> expression)
  }

  override def customSerializer(inputObject: Expression): List[Expression] = {
    val valueExpression = StaticInvoke(classOf[Decimal],
      decimalType,
      "apply",
      Invoke(inputObject, "getValue", ObjectType(classOf[java.math.BigDecimal])) :: Nil)
    val scaleExpression = StaticInvoke(classOf[Math],
      IntegerType,
      "min", Literal(decimalType.scale) ::
        Invoke(Invoke(inputObject, "getValue", ObjectType(classOf[java.math.BigDecimal])),
          "scale", DataTypes.IntegerType) :: Nil
    )
    List(Literal(elementName), valueExpression, Literal(scaleFieldName), scaleExpression)
  }
}

object DecimalCustomCoder {

  /**
   * SQL DecimalType used to represent FHIR Decimal
   *
   * The specs says it should follow the rules for xs:decimal from:
   * https://www.w3.org/TR/xmlschema-2/, which are the
   * "All ·minimally conforming· processors ·must· support decimal numbers with a minimum of 18 decimal digits
   * (i.e., with a ·totalDigits· of 18). However, ·minimally conforming· processors ·may· set an
   * application-defined limit on the maximum number of decimal digits they are prepared to support,
   * in which case that application-defined maximum number ·must· be clearly documented."
   *
   * On the other hand FHIR spec for decimal reads:
   * "Note that large and/or highly precise values are extremely rare in medicine.
   * One element where highly precise decimals may be encountered is the Location coordinates.
   * Irrespective of this, the limits documented in XML Schema apply"
   *
   * For location coordinates 6 decimal digits allow for location precition od 10cm,
   * so should be sufficient for any medical purpose.
   *
   * So the final type is DECIMAL(24,6) which allows both for 6 decimal places and
   * at least 18 digits (regardless if there any decimal digits or not)
   */

  val scale: Int = 6
  val precision: Int = 26
  val decimalType = DataTypes.createDecimalType(precision, scale)
}

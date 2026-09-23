package nextflow.co2footprint.Parsers

import com.fathzer.soft.javaluator.AbstractEvaluator
import com.fathzer.soft.javaluator.Constant
import com.fathzer.soft.javaluator.Function
import com.fathzer.soft.javaluator.Operator

import java.math.RoundingMode

import static com.fathzer.soft.javaluator.DoubleEvaluator.*

/**
 * Adaption of {@link AbstractEvaluator} with all methods that DoubleEvaluator supports except scientific number notation.
 */
class BigDecimalEvaluator extends AbstractEvaluator<BigDecimal> {

    /**
     * Create an instance of a BigDecimal expression evaluator.
     */
    BigDecimalEvaluator() {
        super(getDefaultParameters())
    }

    /**
     * Convert the literal string to a BigDecimal.
     * 
     * @param literal The literal to evaluate.
     * @param evaluationContext The context of the evaluation
     * @return
     */
    protected BigDecimal toValue(String literal, Object evaluationContext) {
        return literal as BigDecimal
    }

    /**
     * Evaluate a constant in the function.
     * 
     * @param constant The constant
     * @param evaluationContext The context of the evaluation
     * @return
     */
    protected BigDecimal evaluate(Constant constant, Object evaluationContext) {
        return switch (constant) {
            case PI -> Math.PI
            case E -> Math.E
            default -> super.evaluate(constant, evaluationContext)
        }  as BigDecimal
    }

    /**
     * Evaluate an operator in the function.
     * 
     * @param operator The operator
     * @param operands The operands
     * @param evaluationContext The context of the evaluation
     * @return
     */
    protected BigDecimal evaluate(Operator operator, Iterator<BigDecimal> operands, Object evaluationContext) {
        return switch (operator) {
            case NEGATE -> -operands.next()
            case NEGATE_HIGH -> -operands.next()
            case MINUS -> operands.next() - operands.next()
            case PLUS -> operands.next() + operands.next()
            case MULTIPLY -> operands.next() * operands.next()
            case DIVIDE -> operands.next() / operands.next()
            case EXPONENT -> operands.next().pow( operands.next() )
            case MODULO -> operands.next() % operands.next()
            default -> super.evaluate(operator, operands, evaluationContext) as BigDecimal
        }
    }

    /**
     * Evaluate a more complex function.
     * 
     * @param function The function
     * @param arguments The function's arguments
     * @param evaluationContext The context of the evaluation
     * @return
     */
    protected BigDecimal evaluate(Function function, Iterator<BigDecimal> arguments, Object evaluationContext) {
        return switch (function) {
            case ABS -> BigDecimal.abs(arguments.next())
            case CEIL -> arguments.next().setScale(0, RoundingMode.UP)
            case FLOOR -> arguments.next().setScale(0, RoundingMode.DOWN)
            case ROUND -> arguments.next().setScale(0, RoundingMode.HALF_UP)
            case SINEH -> Math.sinh(arguments.next())
            case COSINEH -> Math.cosh(arguments.next())
            case TANGENTH -> Math.tanh(arguments.next())
            case SINE -> Math.sin(arguments.next())
            case COSINE -> Math.cos(arguments.next())
            case TANGENT -> Math.tan(arguments.next())
            case ACOSINE -> Math.acos(arguments.next())
            case ASINE -> Math.asin(arguments.next())
            case ATAN -> Math.atan(arguments.next())
            case MIN -> {
                BigDecimal min = arguments.next()
                while (arguments.hasNext()) { min = min.min(arguments.next()) }
                min
            }
            case MAX -> {
                BigDecimal max = arguments.next()
                while (arguments.hasNext()) { max = max.max(arguments.next()) }
                max
            }
            case SUM -> {
                BigDecimal sum = 0
                while (arguments.hasNext()) { sum += arguments.next() }
                max
            }
            case AVERAGE -> {
                BigDecimal sum = 0
                int nb = 0
                while (arguments.hasNext()) {
                    sum += arguments.next()
                    nb += 1
                }
                sum / nb
            }
            case LN -> Math.log(arguments.next())
            case LOG -> Math.log10(arguments.next())
            case RANDOM -> Math.random()
            default -> super.evaluate(function, arguments, evaluationContext) as BigDecimal
        } as BigDecimal
    }
}

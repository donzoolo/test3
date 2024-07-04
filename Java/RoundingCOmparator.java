import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

/**
 * Comparator that compares two {@link Number} objects after rounding them to a specified precision.
 * This comparator is useful for comparing numeric values such as {@link BigDecimal} and {@link Double}
 * with a specified number of decimal places.
 */
public class RoundingComparator implements Comparator<Number> {
    private final int precision;

    /**
     * Constructs a {@code RoundingComparator} with the specified precision.
     *
     * @param precision the number of decimal places to which the numbers should be rounded
     */
    public RoundingComparator(int precision) {
        this.precision = precision;
    }

    /**
     * Compares two {@link Number} objects after rounding them to the specified precision.
     * The numbers are converted to {@link BigDecimal} and then rounded using {@link RoundingMode#HALF_UP}.
     *
     * @param n1 the first {@code Number} to be compared
     * @param n2 the second {@code Number} to be compared
     * @return a negative integer, zero, or a positive integer as the first argument is less than,
     * equal to, or greater than the second
     */
    @Override
    public int compare(Number n1, Number n2) {
        BigDecimal bd1 = BigDecimal.valueOf(n1.doubleValue()).setScale(precision, RoundingMode.HALF_UP);
        BigDecimal bd2 = BigDecimal.valueOf(n2.doubleValue()).setScale(precision, RoundingMode.HALF_UP);
        return bd1.compareTo(bd2);
    }
}
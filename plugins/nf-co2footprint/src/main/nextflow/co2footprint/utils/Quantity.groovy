package nextflow.co2footprint.utils

/**
 * A number associated with a unit.
 */
class Quantity {
    Number value
    String unit
    String scale

    Quantity(Number value, String unit, String scale='') {
        this.value = value
        this.unit = unit

    }

    /**
     * Set the contents of a Quantity.
     *
     * @param value Number of the quantity
     * @param unit Unit string
     * @param scale Scale of the unit
     */
    void set(Number value, String unit=this.unit, String scale=this.scale) {
        this.value = value
        this.unit = unit
        this.scale = scale
    }

    /**
     * Return the value.
     *
     * @return Number value
     */
    Number get() { return this.value }

    /**
     * Get the readable representation of this quantity.
     * Example: '1 GB' for value = 1, scale = 'G', unit = 'B'
     *
     * @return String '<value> <scale><unit>'
     */
    String getReadable() {
        String readable = this.value as String

        String scaledUnit = this.scale ?: '' + this.unit ?: ''
        if (scaledUnit) { readable += ' ' + scaledUnit }

        return readable
    }
}

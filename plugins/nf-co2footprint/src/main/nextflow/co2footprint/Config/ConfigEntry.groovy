package nextflow.co2footprint.Config

import org.apache.commons.lang.SerializationUtils

/**
 * Represents a single configurable entry (parameter) in the plugin's configuration system.
 */
class ConfigEntry {
    private final String name
    private final String description
    private final def defaultValue
    private final Class returnType
    private final Set<Class> allowedTypes
    private def value

    ConfigEntry(String name, String description=null, def defaultValue=null, Class returnType=null, Set<Class> additionalTypes=[]) {
        this.name = name
        this.description = description
        this.defaultValue = defaultValue

        // Determine returnType based on priority:
        if (returnType != null) {
            // 1. Use explicitly provided returnType
            this.returnType = returnType
        } else if (defaultValue != null && !(defaultValue instanceof Runnable)) {
            // 2. If defaultValue exists and is not a Runnable, infer its type
            this.returnType = defaultValue.getClass()
        } else {
            // 3. Fallback: allow any type
            this.returnType = Object
        }

        // Set allowed types to return type + all additional types
        this.allowedTypes = [this.returnType]
        if (additionalTypes) { this.allowedTypes.addAll(additionalTypes) }
    }

    String getName() { name }
    String getDescription() { description }
    String getAllowedTypes() { allowedTypes }
    String getReturnType() { returnType }

    /**
     * Check whether the value is of an allowed type.
     *
     * @param value Value to be checked
     * @throws InvalidClassException if the value's type is not allowed
     */
    void checkType(def value) {
        if(value != null && !allowedTypes.collect({value in it}).any()) {
            throw new InvalidClassException("Value `${value}` (${value.getClass()}) not in allowed Classes ${allowedTypes} for `${name}`.")
        }
    }

    /**
    * Sets the value of this entry to the default.
    *
    * If the defaultValue is a Closure and arguments are provided,
    * the closure is invoked with those arguments and the result is used.
    * Otherwise, the defaultValue is used directly.
    *
    * @param args Optional arguments passed to the default closure if applicable.
    */
    void setDefault(List<Object> args=null) {
        if (args != null && defaultValue instanceof Runnable) {
            set(defaultValue(*args))
        }
        else{
            set(defaultValue)
        }
    }

    /**
     * Assigns a value to this parameter.
     *
     * @param value The value to assign
     */
    void set(def value) {
        checkType(value)
        this.value = value
    }

    /**
     * Fills the value if it was null before. Replacement for ?=.
     *
     * @param value A value to attempt the fill in
     */
    void fill(def value) {
        if (this.value == null) {
            set(value)
        }
    }

    /**
     * Get the current value.
     *
     * @param name
     * @return value
     */
    def get() {
        return value
    }

    /**
     * Get the current evaluated value with the correct return type.
     *
     * @param type Optional type to cast to (default: returnType)
     * @return The evaluated and cast value
     */
    <T> T evaluate(Class<T> type=returnType) {
        if (value instanceof Runnable) {
            return value.call().asType(type)
        }
        else {
            return value.asType(type)
        }
    }

    /**
     * A String representation of the parameter.
     *
     * @return String representation
     */
    String toString() {
        return "${name}: ${value} (${allowedTypes} -> ${returnType})${description ? '\n' + description: ''}"
    }

    /**
     * A map representation of the parameter.
     * Allows the export of raw values for testing purposes, while not exposing the values for modification.
     *
     * @return Map representation
     */
    Map<String, ?> toMap() {
        return SerializationUtils.clone(
                [name: name, description: description, value: value, returnType: returnType, allowedTypes: allowedTypes]
        ) as Map<String, Object>
    }
}
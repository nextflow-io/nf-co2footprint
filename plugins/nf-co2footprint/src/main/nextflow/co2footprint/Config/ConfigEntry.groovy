package nextflow.co2footprint.Config

/**
 * A holder class for parameters in a config.
 */
class ConfigEntry {
    private final String name
    private final defaultValue
    private final SequencedCollection<Class> allowedTypes
    private final Class returnType
    private final String description
    private def value
    private boolean initialized = false

    ConfigEntry(
        String name,
        def defaultValue=null,
        SequencedCollection<Class> allowedTypes=null,
        Class returnType=null,
        String description=null
    ) {
        this.name = name
        this.defaultValue = defaultValue

        this.allowedTypes = {
            if (allowedTypes) {
                allowedTypes
            } else if (returnType != null) {
                [returnType]
            } else if (defaultValue != null && !(defaultValue instanceof Runnable)) {
                [defaultValue.getClass()]
            } else {
                [Object]
            }
        }()

        this.returnType = returnType ?: this.allowedTypes.getFirst()

        this.description = description
    }

    String getName() { name }
    String getDescription() { description }
    String getAllowedTypes() { allowedTypes }
    String getReturnType() { returnType }

    /**
     * Check whether the value is of an allowed type.
     *
     * @param value Value to be checked
     * @return Whether an allowed type was found
     */
    void checkType(def value) {
        if(value != null && !allowedTypes.collect({value in it}).any()) {
            throw new InvalidClassException("Value `${value}` (${value.getClass()}) not in allowed Classes ${allowedTypes} for `${name}`.")
        }
    }

    /**
     * Initialize the value with the given default (function).
     * Calling the default function will not initialize functions.
     * To initialize defaultValue functions without arguments, args needs to be set to [].
     *
     * @param args Arguments for the default function
     * @param overwrite Whether to overwrite the previously set value
     */
    void setDefault(List<Object> args=null, boolean overwrite=false) {
        boolean doSet = !initialized ||overwrite
        if(doSet) {
            if (args != null && defaultValue instanceof Runnable) {
                set(defaultValue(*args))
            }
            else{
                set(defaultValue)
            }
            this.initialized = true
        }
    }

    /**
     * Configure the value for the first time. Sets initialized = true to avoid double initialization.
     *
     * @param value Sets this value
     */
    void configure(def value) {
        checkType(value)
        this.value = value
        this.initialized = true
    }

    /**
     * Set the value.
     *
     * @param value
     */
    void set(def value) {
        checkType(value)
        this.value = value
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
     * @param name
     * @return value
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
     * @return String representation
     */
    String toString() {
        return "${name}: ${value} (${allowedTypes} -> ${returnType})${description ? '\n' + description: ''}"
    }

    /**
     * A Map representation of the parameter.
     * @return Map representation
     */
    Map<String, ?> toMap() {
        return [name: name, value:value, allowedTypes: allowedTypes, returnType: returnType, description: description]
    }
}

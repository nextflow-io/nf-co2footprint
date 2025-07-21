package nextflow.co2footprint.Config

/**
 * Represents a single configurable entry (parameter) in the plugin's configuration system.
 */
class ConfigEntry {
    private final String name
    private final def defaultValue
    private final List<Class> allowedTypes
    private final Class returnType
    private final String description
    private def value
    private boolean initialized = false

    ConfigEntry(
        String name,
        def defaultValue=null,
        List<Class> allowedTypes=null,
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
        this.returnType = returnType ?: this.allowedTypes[0]

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
     * @throws InvalidClassException if the value's type is not allowed
     */
    void checkType(def value) {
        if(value != null && !allowedTypes.collect({value in it}).any()) {
            throw new InvalidClassException("Value `${value}` (${value.getClass()}) not in allowed Classes ${allowedTypes} for `${name}`.")
        }
    }

    /**
     * Initializes the parameter value using the default.
     *
     * If defaultValue is a closure and `args` are provided, the closure is invoked with those arguments.
     * If overwrite is true, existing values will be replaced.
     *
     * @param args Optional arguments for the default closure
     * @param overwrite Whether to overwrite an existing value
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
     * @param value The value to assign
     */
    void configure(def value) {
        set(value)
        this.initialized = true
    }

    /**
     * Assigns a value to this parameter.
     *
     * If the expected return type is a subclass of BaseConfig, this will instantiate or fill it.
     * Otherwise, it validates and assigns the value directly.
     *
     * @param value The value to assign
     */
    void set(def value) {
        if (returnType in BaseConfig && !(value in BaseConfig)) {
            if (this.value in BaseConfig) {
                this.value.fill(value)
            }
            else {
                value = returnType.newInstance(value)
            }
        }
        else {
            checkType(value)
            this.value = value
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

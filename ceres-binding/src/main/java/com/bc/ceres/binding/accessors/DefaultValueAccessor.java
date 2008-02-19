package com.bc.ceres.binding.accessors;

import com.bc.ceres.binding.ValueAccessor;

/**
 * A value accessor for values stored as plain Java object.
 */
public class DefaultValueAccessor implements ValueAccessor {
    private Object value;

    /**
     * Constructs a new {@code DefaultValueAccessor} with a {@code null} default value.
     * @param valueType the type of the value
     */
    public DefaultValueAccessor(Class<?> valueType) {
    }

    /**
     * {@inheritDoc}
     */
    public Object getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    public void setValue(Object value) {
        this.value = value;
    }
}

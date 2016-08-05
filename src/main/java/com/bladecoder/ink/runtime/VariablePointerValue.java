//
// Translated by CS2J (http://www.cs2j.com): 22/07/2016 12:24:34
//

package com.bladecoder.ink.runtime;

import com.bladecoder.ink.runtime.Value;
import com.bladecoder.ink.runtime.ValueType;
import com.bladecoder.ink.runtime.VariablePointerValue;

// TODO: Think: Erm, I get that this contains a string, but should
// we really derive from Value<string>? That seems a bit misleading to me.
public class VariablePointerValue  extends Value<String> 
{
    public String getvariableName() {
        return this.getValue();
    }

    public void setvariableName(String value) {
        this.setValue(value);
    }

    @Override
    public ValueType getvalueType() throws Exception {
        return ValueType.VariablePointer;
    }

    @Override
    public boolean getisTruthy() throws Exception {
        throw new Exception("Shouldn't be checking the truthiness of a variable pointer");
    }

    // Where the variable is located
    // -1 = default, unknown, yet to be determined
    // 0  = in global scope
    // 1+ = callstack element index + 1 (so that the first doesn't conflict with special global scope)
    private int __contextIndex;
    
    public int getcontextIndex() {
        return __contextIndex;
    }

    public void setcontextIndex(int value) {
        __contextIndex = value;
    }

    public VariablePointerValue(String variableName, int contextIndex) {
        super(variableName);
        this.setcontextIndex(contextIndex);
    }
    
    public VariablePointerValue(String variableName) {
        this(variableName, -1);
    }

    public VariablePointerValue() {
        this(null);
    }

    @Override
	public AbstractValue cast(ValueType newType) throws Exception {
        if (newType == getvalueType())
            return this;
         
        throw new Exception("Unexpected type cast of Value to new ValueType");
    }

    public String toString() {
            return "VariablePointerValue(" + getvariableName() + ")";
    }

    public RTObject copy() {
        return new VariablePointerValue(getvariableName(),getcontextIndex());
    }

}



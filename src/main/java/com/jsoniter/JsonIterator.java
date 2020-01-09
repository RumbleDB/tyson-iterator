package com.jsoniter;

import com.jsoniter.any.Any;
import com.jsoniter.spi.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonIterator implements Closeable {

    public Config configCache;
    private static boolean isStreamingEnabled = false;
    final static ValueType[] valueTypes = new ValueType[256];
	final static TypeDeclaration[] typeDeclarations = new TypeDeclaration[256];

    InputStream in;
    byte[] buf;
    int head;
    int tail;
    int skipStartedAt = -1; // skip should keep bytes starting at this pos

    Map<String, Object> tempObjects = null; // used in reflection object decoder
    final Slice reusableSlice = new Slice(null, 0, 0);
    char[] reusableChars = new char[32];
    Object existingObject = null; // the object should be bind to next

    static {
        for (int i = 0; i < valueTypes.length; i++) {
            valueTypes[i] = ValueType.INVALID;
        }
        valueTypes['"'] = ValueType.STRING;
        valueTypes['-'] = ValueType.NUMBER;
        valueTypes['0'] = ValueType.NUMBER;
        valueTypes['1'] = ValueType.NUMBER;
        valueTypes['2'] = ValueType.NUMBER;
        valueTypes['3'] = ValueType.NUMBER;
        valueTypes['4'] = ValueType.NUMBER;
        valueTypes['5'] = ValueType.NUMBER;
        valueTypes['6'] = ValueType.NUMBER;
        valueTypes['7'] = ValueType.NUMBER;
        valueTypes['8'] = ValueType.NUMBER;
        valueTypes['9'] = ValueType.NUMBER;
        valueTypes['t'] = ValueType.BOOLEAN;
        valueTypes['f'] = ValueType.BOOLEAN;
        valueTypes['n'] = ValueType.NULL;
        valueTypes['['] = ValueType.ARRAY;
        valueTypes['{'] = ValueType.OBJECT;
    	typeDeclarations['('] = TypeDeclaration.TYPEDECLARATION;
    }
    

    private JsonIterator(InputStream in, byte[] buf, int head, int tail) {
        this.in = in;
        this.buf = buf;
        this.head = head;
        this.tail = tail;
    }

    public JsonIterator() {
        this(null, new byte[0], 0, 0);
    }

    public static JsonIterator parse(InputStream in, int bufSize) {
        enableStreamingSupport();
        return new JsonIterator(in, new byte[bufSize], 0, 0);
    }

    public static JsonIterator parse(byte[] buf) {
        return new JsonIterator(null, buf, 0, buf.length);
    }

    public static JsonIterator parse(byte[] buf, int head, int tail) {
        return new JsonIterator(null, buf, head, tail);
    }

    public static JsonIterator parse(String str) {
        return parse(str.getBytes());
    }

    public static JsonIterator parse(Slice slice) {
        return new JsonIterator(null, slice.data(), slice.head(), slice.tail());
    }

    public final void reset(byte[] buf) {
        this.buf = buf;
        this.head = 0;
        this.tail = buf.length;
    }

    public final void reset(byte[] buf, int head, int tail) {
        this.buf = buf;
        this.head = head;
        this.tail = tail;
    }

    public final void reset(Slice value) {
        this.buf = value.data();
        this.head = value.head();
        this.tail = value.tail();
    }

    public final void reset(InputStream in) {
        JsonIterator.enableStreamingSupport();
        this.in = in;
        this.head = 0;
        this.tail = 0;
    }

    public final void close() throws IOException {
        if (in != null) {
            in.close();
        }
    }

    //go back one reading position
    final void unreadByte() {
        if (head == 0) {
            throw reportError("unreadByte", "unread too many bytes");
        }
        head--;
    }

    public final JsonException reportError(String op, String msg) {
        int peekStart = head - 10;
        if (peekStart < 0) {
            peekStart = 0;
        }
        int peekSize = head - peekStart;
        if (head > tail) {
            peekSize = tail - peekStart;
        }
        String peek = new String(buf, peekStart, peekSize);
        throw new JsonException(op + ": " + msg + ", head: " + head + ", peek: " + peek + ", buf: " + new String(buf));
    }

    public final String currentBuffer() {
        int peekStart = head - 10;
        if (peekStart < 0) {
            peekStart = 0;
        }
        String peek = new String(buf, peekStart, head - peekStart);
        return "head: " + head + ", peek: " + peek + ", buf: " + new String(buf);
    }
    
    /*if we expect to read a null value, anything not beginning with a n is unread and false is returned.
     *If the null value is inside quotes, we read the value and check if it is equal to "null".
     *If yes, true is returned.
     *If no, false is returned.
     *else true is returned and we skip the whole null input 
     */
    public final boolean readNull() throws IOException {
        byte c = IterImpl.nextToken(this);
        if (c != 'n') {
        	if(c == '"') {
        		unreadByte();
        		String str = readString();
        		if(str.equals("null")) {
        			return true;
        		}
        	} else unreadByte();
            return false;
        }
        IterImpl.skipFixedBytes(this, 3); // null
        return true;
    }
    
    /*if we expect to read a boolean value, beginning to read 't' leads to returning true (because boolean value is true) 
    * and skipping the bytes belonging to the true value.
    * Beginning to read 'f' leads to returning false (boolean value is false)
    * and skipping the bytes belonging to the false value.
    * On all other occasions an error is reported.
    */
    public final boolean readBoolean() throws IOException {
        byte c = IterImpl.nextToken(this);
        if ('t' == c) {
            IterImpl.skipFixedBytes(this, 3); // true
            return true;
        }
        if ('f' == c) {
            IterImpl.skipFixedBytes(this, 4); // false
            return false;
        }
        if(c == '"') {
    		unreadByte();
    		String str = readString();
    		if(str.equals("true")) {
    			return true;
    		} else if(str.contentEquals("false")) {
    			return false;
    		}
    	}
        throw reportError("readBoolean", "expect t or f, found: " + c);
    }
   
    /* 
     * If we expect to read a user defined atomic value, we just call 
     * IterImplString.readString(this) because all user-defined atomic values
     * are represented by strings
     */
    public final String readLiteral() throws IOException {
    	return IterImplString.readString(this);
    }

    public final short readShort() throws IOException {
        int v = readInt();
        if (Short.MIN_VALUE <= v && v <= Short.MAX_VALUE) {
            return (short) v;
        } else {
            throw reportError("readShort", "short overflow: " + v);
        }
    }

    public final int readInt() throws IOException {
        return IterImplNumber.readInt(this);
    }

    public final long readLong() throws IOException {
        return IterImplNumber.readLong(this);
    }

    public final boolean readArray() throws IOException {
        return IterImplArray.readArray(this);
    }

    public String readNumberAsString() throws IOException {
        IterImplForStreaming.numberChars numberChars = IterImplForStreaming.readNumber(this);
        return new String(numberChars.chars, 0, numberChars.charsLength);
    }

    public static interface ReadArrayCallback {
        boolean handle(JsonIterator iter, Object attachment) throws IOException;
    }

    public final boolean readArrayCB(ReadArrayCallback callback, Object attachment) throws IOException {
        return IterImplArray.readArrayCB(this, callback, attachment);
    }

    public final String readString() throws IOException {
        return IterImplString.readString(this);
    }

    public final Slice readStringAsSlice() throws IOException {
        return IterImpl.readSlice(this);
    }

    public final String readObject() throws IOException {
        return IterImplObject.readObject(this);
    }

    public static interface ReadObjectCallback {
        boolean handle(JsonIterator iter, String field, Object attachment) throws IOException;
    }

    public final void readObjectCB(ReadObjectCallback cb, Object attachment) throws IOException {
        IterImplObject.readObjectCB(this, cb, attachment);
    }

    public final float readFloat() throws IOException {
        return IterImplNumber.readFloat(this);
    }

    public final double readDouble() throws IOException {
        return IterImplNumber.readDouble(this);
    }

    public final BigDecimal readBigDecimal() throws IOException {
        // skip whitespace by read next
        ValueType valueType = whatIsNext();
        if (valueType == ValueType.NULL) {
            skip();
            return null;
        }
        if (valueType != ValueType.NUMBER) {
            throw reportError("readBigDecimal", "not number");
        }
        IterImplForStreaming.numberChars numberChars = IterImplForStreaming.readNumber(this);
        return new BigDecimal(numberChars.chars, 0, numberChars.charsLength);
    }

    public final BigInteger readBigInteger() throws IOException {
        // skip whitespace by read next
        ValueType valueType = whatIsNext();
        if (valueType == ValueType.NULL) {
            skip();
            return null;
        }
        if (valueType != ValueType.NUMBER) {
            throw reportError("readBigDecimal", "not number");
        }
        IterImplForStreaming.numberChars numberChars = IterImplForStreaming.readNumber(this);
        return new BigInteger(new String(numberChars.chars, 0, numberChars.charsLength));
    }

    public final Any readAny() throws IOException {
        try {
            return IterImpl.readAny(this);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("read", "premature end");
        }
    }

    private final static ReadArrayCallback fillArray = new ReadArrayCallback() {
        @Override
        public boolean handle(JsonIterator iter, Object attachment) throws IOException {
            List list = (List) attachment;
            list.add(iter.read());
            return true;
        }
    };

    private final static ReadObjectCallback fillObject = new ReadObjectCallback() {
        @Override
        public boolean handle(JsonIterator iter, String field, Object attachment) throws IOException {
            Map map = (Map) attachment;
            map.put(field, iter.read());
            return true;
        }
    };

    public final Object read() throws IOException {
        try {
            ValueType valueType = whatIsNext();
            switch (valueType) {
                case STRING:
                	String stringContent = readString();
                	if(stringContent.equals("null")) {
                		return null;
                	} else if (stringContent.contentEquals("true")) {
                		return true;
                	} else if (stringContent.contentEquals("false")) {
                		return false;
                	}
                	//TODO: look at number case
                    return stringContent;
                case NUMBER:
                	//TODO: look at number case (integer, double, decimal)
                    IterImplForStreaming.numberChars numberChars = IterImplForStreaming.readNumber(this);
                    String numberStr = new String(numberChars.chars, 0, numberChars.charsLength);
                    Double number = Double.valueOf(numberStr);
                    if (numberChars.dotFound) {
                        return number;
                    }
                    double doubleNumber = number;
                    if (doubleNumber == Math.floor(doubleNumber) && !Double.isInfinite(doubleNumber)) {
                        long longNumber = Long.valueOf(numberStr);
                        if (longNumber <= Integer.MAX_VALUE && longNumber >= Integer.MIN_VALUE) {
                            return (int) longNumber;
                        }
                        return longNumber;
                    }
                    return number;
                case NULL:
                    IterImpl.skipFixedBytes(this, 4);
                    return null;
                case BOOLEAN:
                    return readBoolean();
                case ARRAY:
                case USERDEFINEDARRAY:
                    ArrayList list = new ArrayList(4);
                    readArrayCB(fillArray, list);
                    return list;
                case OBJECT:
                case USERDEFINEDOBJECT:
                    Map map = new HashMap(4);
                    readObjectCB(fillObject, map);
                    return map;
                case USERDEFINEDATOMIC:
                	return readLiteral();
                default:
                    throw reportError("read", "unexpected value type: " + valueType);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("read", "premature end");
        }
    }

    /**
     * try to bind to existing object, returned object might not the same instance
     *
     * @param existingObject the object instance to reuse
     * @param <T>            object type
     * @return data binding result, might not be the same object
     * @throws IOException if I/O went wrong
     */
    public final <T> T read(T existingObject) throws IOException {
        try {
            this.existingObject = existingObject;
            Class<?> clazz = existingObject.getClass();
            String cacheKey = currentConfig().getDecoderCacheKey(clazz);
            return (T) Codegen.getDecoder(cacheKey, clazz).decode(this);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("read", "premature end");
        }
    }

    private Config currentConfig() {
        if (configCache == null) {
            configCache = JsoniterSpi.getCurrentConfig();
        }
        return configCache;
    }

    /**
     * try to bind to existing object, returned object might not the same instance
     *
     * @param typeLiteral    the type object
     * @param existingObject the object instance to reuse
     * @param <T>            object type
     * @return data binding result, might not be the same object
     * @throws IOException if I/O went wrong
     */
    public final <T> T read(TypeLiteral<T> typeLiteral, T existingObject) throws IOException {
        try {
            this.existingObject = existingObject;
            String cacheKey = currentConfig().getDecoderCacheKey(typeLiteral.getType());
            return (T) Codegen.getDecoder(cacheKey, typeLiteral.getType()).decode(this);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("read", "premature end");
        }
    }

    public final <T> T read(Class<T> clazz) throws IOException {
        return (T) read((Type) clazz);
    }

    public final <T> T read(TypeLiteral<T> typeLiteral) throws IOException {
        return (T) read(typeLiteral.getType());
    }

    public final Object read(Type type) throws IOException {
        try {
            String cacheKey = currentConfig().getDecoderCacheKey(type);
            return Codegen.getDecoder(cacheKey, type).decode(this);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("read", "premature end");
        }
    }
    
    public String mapBuiltInValueTypeToString(ValueType v) {
    	switch(v) {
    	case STRING:
    		return "string";
    	case NUMBER:
    		return "number";
    	case NULL:
    		return "null";
    	case ARRAY:
    		return "array";
    	case OBJECT:
    		return "object";
    	case BOOLEAN:
    		return "boolean";
    	default: 
    		return "";
    	}
    }
    
    /**
     * Consume a type declaration in front of a value. 
     * Startpoint is in front of opening parenthesis.
     * 
     * @return String typeName of declared type
     * @throws IOException
     */
    public String readTypeDeclaration() throws IOException {
    	//consume first byte of the type declaration (opening parenthesis)
    	byte openParenthesis = IterImpl.nextToken(this);
    	if(openParenthesis == '(') {
    		//read the string type name
    		String typeName = readString();
    		if(IterImpl.nextToken(this) == ')') {
    			//type declaration is closed with closing parenthesis, return type name
    			return typeName;
    		} else throw reportError("readTypeDeclaration", "no closing parenthesis");
    	} else throw reportError("readTypeDeclaration", "no opening parenthesis");
    }
    
    /**
     * Consume a type declaration in front of a value. 
     * Startpoint is after a closing parenthesis.
     * Step back in the stream of bytes until complete type declaration can be read and
     * return the name of the type. Needed to create userdefined types, 
     * whose constructor waits for a typeName and a TysonInstance.
     * 
     * @return String typeName of declared type
     * @throws IOException
     */
    public String readTypeName() throws IOException{
    	int currentHead = this.head - 1;
    	byte currentToken = this.buf[currentHead];
    	//currentToken should point to closing parenthesis
    	String typeNameReversed = "";
    	//System.out.println(this.buf[3]);
    	//System.out.println("currentToken: " +(char) currentToken);
    	
    	//skip whitespace characters
    	boolean skipWhitespaces = false;
    	skipWhitespaces = (currentToken == ' ' || currentToken == '\n' || currentToken == '\r' || currentToken == '\t' );
    	while(skipWhitespaces) {
    		currentHead -= 1;
    		if(!(this.buf[currentHead]== ' ' || this.buf[currentHead] == '\n' || this.buf[currentHead] == '\r' || this.buf[currentHead] == '\t')) {
    			skipWhitespaces = false;
    		}
    	}
    	
        currentToken = this.buf[currentHead];
    	if(currentToken == ')') {
    		//type declaration is present 
    		//step back until opening parenthesis is found
    		boolean continueBackwards = true;
    		
    		while(continueBackwards) {
    			//go back one step
        		currentHead -= 1;
    			currentToken = this.buf[currentHead];
    			//add currentToken to typeName string
    			typeNameReversed = typeNameReversed + (char)currentToken;
    			//did we find the matching opening parenthesis?
    			if(currentToken == '(') {
    				int peek = currentHead - 1;
    				if(peek >= 0 && this.buf[peek] != '/') {
        				//we found an opening parenthesis and it is not escaped, need to prevent next loop iteration
    					continueBackwards = false;
    				} 
    				if(peek < 0) {
    					//we are at the start of the stream, cannot go back any further
    					continueBackwards = false;
    				}
    				//remove parenthesis from typeNameReversed
    				typeNameReversed = typeNameReversed.substring(0, typeNameReversed.length() - 1);
    			}
    		}
    		//typeNameReversed should now contain our type-name but in reverse
    		//System.out.println(currentHead);
    		String typeName = new StringBuffer(typeNameReversed).reverse().toString();
    		return typeName;    		
    		
    	} else throw reportError("getTypeName", "trying to read type name of a built-in type");

    }
    
    /**
     * Gets the type information of the next value by looking at its first token.
     * If a type is annotated, a typecheck between the annotated value and
     * the provided value is performed and on mismatch, an error is reported.
     * If no type annotation is provided, will simply return the implicit type of the next value.
     * When returning, head is positioned right in front of the value (after a possible type annotation)
     * 
     * @return ValueType of the next value to come in the stream. 
     * @throws IOException
     */
    public ValueType whatIsNext() throws IOException {
        ValueType valueType = valueTypes[IterImpl.nextToken(this)];
        unreadByte();
        //the next tokens are not representing a value but they could be a type declaration
        if(valueType == ValueType.INVALID) {
        	TypeDeclaration typeDecl = typeDeclarations[IterImpl.nextToken(this)];
        	unreadByte();
        	if(typeDecl == TypeDeclaration.TYPEDECLARATION) {
            	String typeName = readTypeDeclaration();
    //System.out.println("typeName is " + typeName);
            	//token should now be after the type declaration
            	ValueType providedValue = valueTypes[IterImpl.nextToken(this)];
                unreadByte();
            	//check if annotated value (typeName) corresponds to a built in type
        		List<String> builtinTypes = Arrays.asList("string", "integer", "decimal", "double", "boolean", "null", "object", "array");
    //System.out.println("builtin check is: " + builtinTypes.contains(typeName));
            	boolean isBuiltInType = (builtinTypes.contains(typeName));
            	//if yes, check if declared value (declaredValue) is of the same type
            	if(isBuiltInType) {
            		boolean isSameType = (mapBuiltInValueTypeToString(providedValue).equals(typeName)); //TODO: need to look at number case
    //System.out.println("mapBuiltInValueTypeToString(providedValue) is: " + mapBuiltInValueTypeToString(providedValue));
    //System.out.println("isSameType is: " + isSameType);
            		if (isSameType) {
            			//unread the byte we read when calling nextToken, iterator now at the end of type declaration
            			unreadByte();
            		} else {
            			//we need to handle typed values that are quoted:
            			unreadByte();
            			if(providedValue == ValueType.STRING) {
            				String val = readString();
            				int backsteps = val.length()+3;
            				//Handle the boolean cases
            				if(val.contentEquals("true") || val.contentEquals("false")) {
            					providedValue = ValueType.BOOLEAN;
            				}
            				
            				//handle the null case
            				else if(val.contentEquals("null")) {
            					providedValue = ValueType.NULL;
            				}
            				//Handle the number cases, these could be "NaN", "+INF", "-INF" or any number
            				//TODO
            				
            				//need to jump back all the bytes we already consumed in readString()
            				this.head = this.head - backsteps;
            				return providedValue;
            			}
            			
            			throw reportError("whatIsNext, typecheck", "type mismatch");
            		}
            	} else {
                //if no, we have a user defined type. Atom/Array/Object depending on the declaredValue
    //System.out.println(providedValue);
            		switch(providedValue) {
            		case STRING:
            			//declaredValue is String -> Atomic Type
            			return providedValue = ValueType.USERDEFINEDATOMIC;
            		case NUMBER:
            			//declaredValue is Number -> Atomic Type
            			return providedValue = ValueType.USERDEFINEDATOMIC;
            		case BOOLEAN:
            			//declaredValue is Boolean -> Atomic Type
            			return providedValue = ValueType.USERDEFINEDATOMIC;
            		case NULL:
            			//declaredValue is Null -> Atomic Type
            			return providedValue = ValueType.USERDEFINEDATOMIC;
            		case ARRAY:
            			//declaredValue is Array -> Array Type
            			return providedValue = ValueType.USERDEFINEDARRAY;
            		case OBJECT:
            			//declaredValue is Object -> Object Type
            			return providedValue = ValueType.USERDEFINEDOBJECT;
                	default:
                		throw reportError("whatIsNext, userdefined value", "provided value " +providedValue+" is not conformant notation");
            		}
            	}
            	return providedValue;
            }
        }
        
        if(valueType == ValueType.NUMBER) {
        	/*
        	 * In this case we have a number because the value starts with a 
        	 * -, 0, 1, 2, 3, 4, 5, 6, 7, 8 or 9, but no type is declared.
        	 * We therefore need to look at the number to get the right type.
        	 * TODO
        	 */
        }
        return valueType;
    }

    public void skip() throws IOException {
        IterImplSkip.skip(this);
    }

    public static final <T> T deserialize(Config config, String input, Class<T> clazz) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input.getBytes(), clazz);
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final <T> T deserialize(String input, Class<T> clazz) {
        return deserialize(input.getBytes(), clazz);
    }

    public static final <T> T deserialize(Config config, String input, TypeLiteral<T> typeLiteral) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input.getBytes(), typeLiteral);
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final <T> T deserialize(String input, TypeLiteral<T> typeLiteral) {
        return deserialize(input.getBytes(), typeLiteral);
    }

    public static final <T> T deserialize(Config config, byte[] input, Class<T> clazz) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input, clazz);
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final <T> T deserialize(byte[] input, Class<T> clazz) {
        int lastNotSpacePos = findLastNotSpacePos(input);
        JsonIterator iter = JsonIteratorPool.borrowJsonIterator();
        iter.reset(input, 0, lastNotSpacePos);
        try {
            T val = iter.read(clazz);
            if (iter.head != lastNotSpacePos) {
                throw iter.reportError("deserialize", "trailing garbage found");
            }
            return val;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw iter.reportError("deserialize", "premature end");
        } catch (IOException e) {
            throw new JsonException(e);
        } finally {
            JsonIteratorPool.returnJsonIterator(iter);
        }
    }

    public static final <T> T deserialize(Config config, byte[] input, TypeLiteral<T> typeLiteral) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input, typeLiteral);
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final <T> T deserialize(byte[] input, TypeLiteral<T> typeLiteral) {
        int lastNotSpacePos = findLastNotSpacePos(input);
        JsonIterator iter = JsonIteratorPool.borrowJsonIterator();
        iter.reset(input, 0, lastNotSpacePos);
        try {
            T val = iter.read(typeLiteral);
            if (iter.head != lastNotSpacePos) {
                throw iter.reportError("deserialize", "trailing garbage found");
            }
            return val;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw iter.reportError("deserialize", "premature end");
        } catch (IOException e) {
            throw new JsonException(e);
        } finally {
            JsonIteratorPool.returnJsonIterator(iter);
        }
    }

    public static final Any deserialize(Config config, String input) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input.getBytes());
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final Any deserialize(String input) {
        return deserialize(input.getBytes());
    }

    public static final Any deserialize(Config config, byte[] input) {
        JsoniterSpi.setCurrentConfig(config);
        try {
            return deserialize(input);
        } finally {
            JsoniterSpi.clearCurrentConfig();
        }
    }

    public static final Any deserialize(byte[] input) {
        int lastNotSpacePos = findLastNotSpacePos(input);
        JsonIterator iter = JsonIteratorPool.borrowJsonIterator();
        iter.reset(input, 0, lastNotSpacePos);
        try {
            Any val = iter.readAny();
            if (iter.head != lastNotSpacePos) {
                throw iter.reportError("deserialize", "trailing garbage found");
            }
            return val;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw iter.reportError("deserialize", "premature end");
        } catch (IOException e) {
            throw new JsonException(e);
        } finally {
            JsonIteratorPool.returnJsonIterator(iter);
        }
    }

    private static int findLastNotSpacePos(byte[] input) {
        for (int i = input.length - 1; i >= 0; i--) {
            byte c = input[i];
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return i + 1;
            }
        }
        return 0;
    }

    public static void setMode(DecodingMode mode) {
        Config newConfig = JsoniterSpi.getDefaultConfig().copyBuilder().decodingMode(mode).build();
        JsoniterSpi.setDefaultConfig(newConfig);
        JsoniterSpi.setCurrentConfig(newConfig);
    }

    public static void enableStreamingSupport() {
        if (isStreamingEnabled) {
            return;
        }
        isStreamingEnabled = true;
        try {
            DynamicCodegen.enableStreamingSupport();
        }  catch (JsonException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonException(e);
        }
    }
}

package com.jsoniter;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class annotated_tests {

	@Test
	public void test_simple_inputs() throws IOException {
		JsonIterator iter = JsonIterator.parse("(\"array\") [\"hello\"]");
		assertTrue(iter.whatIsNext() == ValueType.ARRAY);
		assertTrue(iter.readArray());
		assertTrue(iter.whatIsNext() == ValueType.STRING);
		assertEquals("hello", iter.readString());
		assertFalse(iter.readArray());
		
		JsonIterator iter02 = JsonIterator.parse("34");
		assertEquals(ValueType.NUMBER, iter02.whatIsNext());
		assertEquals(34, iter02.read());
	}
	
	@Test
	public void test_quoted_null_input() throws IOException{
		JsonIterator null01 = JsonIterator.parse("(\"null\") \"null\"");
		assertEquals(ValueType.NULL, null01.whatIsNext());
		assertTrue(null01.readNull());
		
		JsonIterator null02 = JsonIterator.parse("(\"null\") \"null\"");
		assertEquals(ValueType.NULL, null02.whatIsNext());
		assertEquals(null, null02.read());
	}
	
	@Test
	public void test_quoted_boolean_inputs() throws IOException {
		JsonIterator bool01 = JsonIterator.parse("(\"boolean\") true");
		JsonIterator bool02 = JsonIterator.parse("(\"boolean\") false");
		JsonIterator bool03 = JsonIterator.parse("(\"boolean\") \"true\"");
		JsonIterator bool04 = JsonIterator.parse("(\"boolean\") \"false\"");

		
		assertEquals(ValueType.BOOLEAN, bool01.whatIsNext());
		assertTrue(bool01.readBoolean());
		
		assertEquals(ValueType.BOOLEAN, bool02.whatIsNext());
		assertFalse(bool02.readBoolean());

		assertEquals(ValueType.BOOLEAN, bool03.whatIsNext());
		assertTrue(bool03.readBoolean());

		assertEquals(ValueType.BOOLEAN, bool04.whatIsNext());
		assertFalse(bool04.readBoolean());

		JsonIterator bool05 = JsonIterator.parse("(\"boolean\") true");
		JsonIterator bool06 = JsonIterator.parse("(\"boolean\") false");
		JsonIterator bool07 = JsonIterator.parse("(\"boolean\") \"true\"");
		JsonIterator bool08 = JsonIterator.parse("(\"boolean\") \"false\"");
		
		assertEquals(ValueType.BOOLEAN, bool05.whatIsNext());
		assertEquals(true, bool05.read());
		
		assertEquals(ValueType.BOOLEAN, bool06.whatIsNext());
		assertEquals(false, bool06.read());

		assertEquals(ValueType.BOOLEAN, bool07.whatIsNext());
		assertEquals(true, bool07.read());

		assertEquals(ValueType.BOOLEAN, bool08.whatIsNext());
		assertEquals(false, bool08.read());
	}
	
	@Test
	public void test_unannotated_inputs() throws IOException{
		JsonIterator bool01 = JsonIterator.parse("true");
		JsonIterator bool02 = JsonIterator.parse("false");

		
		assertEquals(ValueType.BOOLEAN, bool01.whatIsNext());
		assertTrue(bool01.readBoolean());
		
		assertEquals(ValueType.BOOLEAN, bool02.whatIsNext());
		assertFalse(bool02.readBoolean());

		JsonIterator bool03 = JsonIterator.parse("true");
		JsonIterator bool04 = JsonIterator.parse("false");

		
		assertEquals(ValueType.BOOLEAN, bool03.whatIsNext());
		assertTrue(bool03.readBoolean());
		
		assertEquals(ValueType.BOOLEAN, bool04.whatIsNext());
		assertFalse(bool04.readBoolean());
		
		JsonIterator null01 = JsonIterator.parse("null");
		assertEquals(ValueType.NULL, null01.whatIsNext());
		assertTrue(null01.readNull());
		
		JsonIterator null02 = JsonIterator.parse("null");
		assertEquals(ValueType.NULL, null02.whatIsNext());
		assertEquals(null, null02.read());
		
		JsonIterator string01 = JsonIterator.parse("\"hello I am a string\"");
		assertEquals(ValueType.STRING, string01.whatIsNext());
		assertEquals("hello I am a string", string01.read());

		JsonIterator string02 = JsonIterator.parse("\"hello I am a second string\"");
		assertEquals(ValueType.STRING, string02.whatIsNext());
		assertEquals("hello I am a second string", string02.readString());
		
		JsonIterator iter = JsonIterator.parse("[\"hello\"]");
		assertTrue(iter.whatIsNext() == ValueType.ARRAY);
		assertTrue(iter.readArray());
		assertTrue(iter.whatIsNext() == ValueType.STRING);
		assertEquals("hello", iter.readString());
		assertFalse(iter.readArray());
		
		JsonIterator iter3 = JsonIterator.parse("{\"entry01\": \"Hi, I'm the value\"}");
		assertEquals(ValueType.OBJECT, iter3.whatIsNext());
		assertEquals("entry01", iter3.readObject());
		assertEquals(ValueType.STRING, iter3.whatIsNext());
		assertEquals("Hi, I'm the value", iter3.readString());
		assertEquals(null, iter3.readObject());
	}
	
	@Test
	public void test_userdefined_inputs() throws IOException {
		JsonIterator iter = JsonIterator.parse("(\"my-array\")     [\"hello\"]");
		assertTrue(iter.whatIsNext() == ValueType.USERDEFINEDARRAY);
		assertEquals("\"my-array\"", iter.readTypeName());
		assertEquals(iter.whatIsNext(), ValueType.ARRAY);
		//readArray evaluates to true if the array still has entries
		assertTrue(iter.readArray());
		assertEquals(iter.whatIsNext(), ValueType.STRING);
		String array01 = iter.readString();
		assertEquals(array01, "hello");

				
		JsonIterator iter2 = JsonIterator.parse("(\"my-atomic\")  \"5\"");
		assertTrue(iter2.whatIsNext() == ValueType.USERDEFINEDATOMIC);
		assertEquals(iter2.readLiteral(), "5");
		
		
		JsonIterator iter3 = JsonIterator.parse("(\"my-object\") {\"entry01\": (\"value\") \"Hi, I'm the value\"}");
		assertEquals(ValueType.USERDEFINEDOBJECT, iter3.whatIsNext());
		assertEquals("\"my-object\"", iter3.readTypeName());
		assertEquals("entry01", iter3.readObject());
		assertEquals( ValueType.USERDEFINEDATOMIC, iter3.whatIsNext());
		assertEquals("\"value\"", iter3.readTypeName());
		assertEquals("Hi, I'm the value", iter3.readLiteral());
		assertEquals(null, iter3.readObject());

	}

}

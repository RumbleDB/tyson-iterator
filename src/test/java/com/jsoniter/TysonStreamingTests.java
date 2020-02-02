package com.jsoniter;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class TysonStreamingTests {
	
	@Test
	public void test_numbers() throws IOException {
		JsonIterator tysonIntUnquoted = JsonIterator.parse("(\"integer\") 2");
		assertEquals(InputType.TYPEDECLARATION, tysonIntUnquoted.whatIsNext());
		assertEquals("integer", tysonIntUnquoted.readTypeDeclaration());
		assertEquals(2, tysonIntUnquoted.readInt());
		
		JsonIterator tysonIntQuoted = JsonIterator.parse("(\"integer\") \"2\"");
		assertEquals(InputType.TYPEDECLARATION, tysonIntQuoted.whatIsNext());
		assertEquals("integer", tysonIntQuoted.readTypeDeclaration());
		assertEquals(2, tysonIntQuoted.readInt());
	
	}
	
	@Test
	public void test_simple_inputs() throws IOException {
		JsonIterator iter = JsonIterator.parse("(\"array\") [\"hello\"]");
		assertEquals(InputType.TYPEDECLARATION,iter.whatIsNext());
		assertEquals("array", iter.readTypeDeclaration());
		assertEquals(InputType.ARRAY, iter.whatIsNext());
		assertTrue(iter.readArray());
		assertEquals(InputType.STRING, iter.whatIsNext());
		assertEquals("hello", iter.readString());
		assertFalse(iter.readArray());
		
		JsonIterator iter02 = JsonIterator.parse("34");
		assertEquals(InputType.NUMBER, iter02.whatIsNext());
		assertEquals(34, iter02.read());
	}
	
	@Test
	public void test_quoted_null_input() throws IOException{
		JsonIterator null01 = JsonIterator.parse("(\"null\") \"null\"");
		assertEquals(InputType.TYPEDECLARATION, null01.whatIsNext());
		assertEquals("null", null01.readTypeDeclaration());
		assertTrue(null01.readNull());
		
		JsonIterator null02 = JsonIterator.parse("(\"null\") \"null\"");
		assertEquals(InputType.TYPEDECLARATION, null02.whatIsNext());
		assertEquals("null", null02.readTypeDeclaration());
		assertEquals(null, null02.read());
	}
	
	@Test
	public void test_quoted_boolean_inputs() throws IOException {
		JsonIterator bool01 = JsonIterator.parse("(\"boolean\") true");
		JsonIterator bool02 = JsonIterator.parse("(\"boolean\") false");
		JsonIterator bool03 = JsonIterator.parse("(\"boolean\") \"true\"");
		JsonIterator bool04 = JsonIterator.parse("(\"boolean\") \"false\"");

		
		assertEquals(InputType.TYPEDECLARATION, bool01.whatIsNext());
		assertEquals("boolean", bool01.readTypeDeclaration());
		assertEquals(InputType.BOOLEAN, bool01.whatIsNext());
		assertTrue(bool01.readBoolean());
		
		assertEquals(InputType.TYPEDECLARATION, bool02.whatIsNext());
		assertEquals("boolean", bool02.readTypeDeclaration());
		assertEquals(InputType.BOOLEAN, bool02.whatIsNext());
		assertFalse(bool02.readBoolean());

		assertEquals(InputType.TYPEDECLARATION, bool03.whatIsNext());
		assertEquals("boolean", bool03.readTypeDeclaration());
		assertTrue(bool03.readBoolean());

		assertEquals(InputType.TYPEDECLARATION, bool04.whatIsNext());
		assertEquals("boolean", bool04.readTypeDeclaration());
		assertFalse(bool04.readBoolean());

		JsonIterator bool05 = JsonIterator.parse("(\"boolean\") true");
		JsonIterator bool06 = JsonIterator.parse("(\"boolean\") false");
		JsonIterator bool07 = JsonIterator.parse("(\"boolean\") \"true\"");
		JsonIterator bool08 = JsonIterator.parse("(\"boolean\") \"false\"");
		
		assertEquals(InputType.TYPEDECLARATION, bool05.whatIsNext());
		assertEquals("boolean", bool05.readTypeDeclaration());
		assertEquals(true, bool05.read());
		
		assertEquals(InputType.TYPEDECLARATION, bool06.whatIsNext());
		assertEquals("boolean", bool06.readTypeDeclaration());
		assertEquals(false, bool06.read());

		assertEquals(InputType.TYPEDECLARATION, bool07.whatIsNext());
		assertEquals("boolean", bool07.readTypeDeclaration());
		assertEquals(true, bool07.read());

		assertEquals(InputType.TYPEDECLARATION, bool08.whatIsNext());
		assertEquals("boolean", bool08.readTypeDeclaration());
		assertEquals(false, bool08.read());
	}
	
	@Test
	public void test_unannotated_inputs() throws IOException{
		JsonIterator bool01 = JsonIterator.parse("true");
		JsonIterator bool02 = JsonIterator.parse("false");

		
		assertEquals(InputType.BOOLEAN, bool01.whatIsNext());
		assertTrue(bool01.readBoolean());
		
		assertEquals(InputType.BOOLEAN, bool02.whatIsNext());
		assertFalse(bool02.readBoolean());

		JsonIterator bool03 = JsonIterator.parse("true");
		JsonIterator bool04 = JsonIterator.parse("false");

		
		assertEquals(InputType.BOOLEAN, bool03.whatIsNext());
		assertTrue(bool03.readBoolean());
		
		assertEquals(InputType.BOOLEAN, bool04.whatIsNext());
		assertFalse(bool04.readBoolean());
		
		JsonIterator null01 = JsonIterator.parse("null");
		assertEquals(InputType.NULL, null01.whatIsNext());
		assertTrue(null01.readNull());
		
		JsonIterator null02 = JsonIterator.parse("null");
		assertEquals(InputType.NULL, null02.whatIsNext());
		assertEquals(null, null02.read());
		
		JsonIterator string01 = JsonIterator.parse("\"hello I am a string\"");
		assertEquals(InputType.STRING, string01.whatIsNext());
		assertEquals("hello I am a string", string01.read());

		JsonIterator string02 = JsonIterator.parse("\"hello I am a second string\"");
		assertEquals(InputType.STRING, string02.whatIsNext());
		assertEquals("hello I am a second string", string02.readString());
		
		JsonIterator iter = JsonIterator.parse("[\"hello\"]");
		assertTrue(iter.whatIsNext() == InputType.ARRAY);
		assertTrue(iter.readArray());
		assertTrue(iter.whatIsNext() == InputType.STRING);
		assertEquals("hello", iter.readString());
		assertFalse(iter.readArray());
		
		JsonIterator iter3 = JsonIterator.parse("{\"entry01\": \"Hi, I'm the value\"}");
		assertEquals(InputType.OBJECT, iter3.whatIsNext());
		assertEquals("entry01", iter3.readObject());
		assertEquals(InputType.STRING, iter3.whatIsNext());
		assertEquals("Hi, I'm the value", iter3.readString());
		assertEquals(null, iter3.readObject());
	}
	
	@Test
	public void test_userdefined_inputs() throws IOException {
		JsonIterator iter = JsonIterator.parse("(\"my-array\")     [\"hello\"]");
		assertEquals(InputType.TYPEDECLARATION, iter.whatIsNext());
		assertEquals("my-array", iter.readTypeDeclaration());
		assertEquals(iter.whatIsNext(), InputType.ARRAY);
		//readArray evaluates to true if the array still has entries
		assertTrue(iter.readArray());
		assertEquals(iter.whatIsNext(), InputType.STRING);
		String array01 = iter.readString();
		assertEquals(array01, "hello");

				
		JsonIterator iter2 = JsonIterator.parse("(\"my-atomic\")  \"5\"");
		assertEquals(InputType.TYPEDECLARATION, iter2.whatIsNext());
		assertEquals("my-atomic", iter2.readTypeDeclaration());
		assertEquals("5", iter2.readLiteral());
		
		
		JsonIterator iter3 = JsonIterator.parse("(\"my-object\") {\"entry01\": (\"value\") \"Hi, I'm the value\"}");
		assertEquals(InputType.TYPEDECLARATION, iter3.whatIsNext());
		assertEquals("my-object", iter3.readTypeDeclaration());
		assertEquals("entry01", iter3.readObject());
		assertEquals( InputType.TYPEDECLARATION, iter3.whatIsNext());
		assertEquals("value", iter3.readTypeDeclaration());
		assertEquals("Hi, I'm the value", iter3.readLiteral());
		assertEquals(null, iter3.readObject());

	}
	
	@Test
	public void test_different() throws IOException {
		JsonIterator annotatedQuoted = JsonIterator.parse("(\"string\") \"hello\"");
		JsonIterator annotatedNotQuoted = JsonIterator.parse("(\"string\") hello");
		JsonIterator annotatedNotQuotedBoolean = JsonIterator.parse("(\"boolean\") true");
		JsonIterator notQuotedUserdefinedString = JsonIterator.parse("(\"string-my-01\") hello");
		JsonIterator notQuotedUserdefinedNumber = JsonIterator.parse("(\"string-my-01\") 4");
		JsonIterator quotedUserdefined = JsonIterator.parse("(\"string-my-02\") \"hello\"");
		JsonIterator notAnnotetedNotQuotedBoolean = JsonIterator.parse("true");
		JsonIterator notAnnotatedQuoted = JsonIterator.parse("\"hello\"");

		//annotated and quoted
		assertEquals(InputType.TYPEDECLARATION, annotatedQuoted.whatIsNext());
		assertEquals("string", annotatedQuoted.readTypeDeclaration());
		assertEquals("hello", annotatedQuoted.readString());
		
		//annotated and not quoted boolean
		assertEquals(InputType.TYPEDECLARATION, annotatedNotQuotedBoolean.whatIsNext());
		assertEquals("boolean", annotatedNotQuotedBoolean.readTypeDeclaration());
		assertEquals(annotatedNotQuotedBoolean.readBoolean(), true);
		
		//annotated and not quoted
		assertEquals(InputType.TYPEDECLARATION, annotatedNotQuoted.whatIsNext());
		assertEquals("string", annotatedNotQuoted.readTypeDeclaration());
		assertEquals("hello", annotatedNotQuoted.readString());
		
		//not annotated not quoted		
		assertEquals(InputType.BOOLEAN, notAnnotetedNotQuotedBoolean.whatIsNext());
		assertEquals(true, notAnnotetedNotQuotedBoolean.readBoolean());
		
		//not annotated but quoted
		assertTrue(notAnnotatedQuoted.whatIsNext()== InputType.STRING);
		assertEquals("hello", notAnnotatedQuoted.readString());
		
		//unquoted userdefined
		assertEquals(InputType.TYPEDECLARATION, notQuotedUserdefinedNumber.whatIsNext());
		assertEquals("string-my-01", notQuotedUserdefinedNumber.readTypeDeclaration());
		assertEquals("4", notQuotedUserdefinedNumber.readLiteral());
		
		assertEquals(InputType.TYPEDECLARATION, notQuotedUserdefinedString.whatIsNext());
		assertEquals("string-my-01", notQuotedUserdefinedString.readTypeDeclaration());
		assertEquals("hello", notQuotedUserdefinedString.readLiteral());
	

		//quoted userdefined
		assertEquals(InputType.TYPEDECLARATION, quotedUserdefined.whatIsNext());
		assertEquals("string-my-02", quotedUserdefined.readTypeDeclaration());
		assertEquals("hello", quotedUserdefined.readLiteral());
		
	}

}

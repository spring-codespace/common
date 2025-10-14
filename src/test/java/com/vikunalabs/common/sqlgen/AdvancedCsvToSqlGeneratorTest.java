package com.vikunalabs.common.sqlgen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class AdvancedCsvToSqlGeneratorTest {

    private AdvancedCsvToSqlGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new AdvancedCsvToSqlGenerator();
    }

    @Test
    void testBasicCsvToSqlConversion() throws IOException {
        // Create test CSV file
        Path csvFile = tempDir.resolve("test.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,email,age",
                "1,John Doe,john@example.com,30",
                "2,Jane Smith,jane@example.com,25"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(2, sqlStatements.size());
        assertEquals("INSERT INTO users (id, name, email, age) VALUES (1, 'John Doe', 'john@example.com', 30);",
                sqlStatements.get(0));
        assertEquals("INSERT INTO users (id, name, email, age) VALUES (2, 'Jane Smith', 'jane@example.com', 25);",
                sqlStatements.get(1));
    }

    @Test
    void testCsvWithQuotedFields() throws IOException {
        Path csvFile = tempDir.resolve("quoted.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,description",
                "1,John Doe,\"Software Engineer, Senior\"",
                "2,Jane Smith,\"Manager, IT Department\""
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "employees");

        assertEquals(2, sqlStatements.size());
        assertTrue(sqlStatements.get(0).contains("'Software Engineer, Senior'"));
        assertTrue(sqlStatements.get(1).contains("'Manager, IT Department'"));
    }

    @Test
    void testCsvWithEmptyValues() throws IOException {
        Path csvFile = tempDir.resolve("empty.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,email,phone",
                "1,John Doe,john@example.com,",
                "2,Jane Smith,,+123456789"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "contacts");

        assertEquals(2, sqlStatements.size());
        assertTrue(sqlStatements.get(0).contains("NULL")); // Empty phone should be NULL
        assertTrue(sqlStatements.get(1).contains("NULL")); // Empty email should be NULL
    }

    @Test
    void testCsvWithBooleanValues() throws IOException {
        Path csvFile = tempDir.resolve("boolean.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,active,verified",
                "1,John Doe,true,false",
                "2,Jane Smith,false,true"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(2, sqlStatements.size());
        assertTrue(sqlStatements.get(0).contains("TRUE"));
        assertTrue(sqlStatements.get(0).contains("FALSE"));
        assertTrue(sqlStatements.get(1).contains("FALSE"));
        assertTrue(sqlStatements.get(1).contains("TRUE"));
    }

    @Test
    void testCsvWithNumericValues() throws IOException {
        Path csvFile = tempDir.resolve("numeric.csv");
        Files.write(csvFile, Arrays.asList(
                "id,price,quantity,discount",
                "1,19.99,100,0.1",
                "2,0.50,25,0.0"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "products");

        assertEquals(2, sqlStatements.size());
        // Numeric values should not be quoted
        assertTrue(sqlStatements.get(0).contains("19.99, 100, 0.1"));
        assertFalse(sqlStatements.get(0).contains("'19.99'"));
    }

    @Test
    void testCsvWithEscapedQuotes() throws IOException {
        Path csvFile = tempDir.resolve("escaped.csv");
        Files.write(csvFile, Arrays.asList(
                "id,message",
                "1,\"He said, \"\"Hello World!\"\"\"",
                "2,\"Another \"\"quoted\"\" text\""
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "messages");

        assertEquals(2, sqlStatements.size());
        // Single quotes should be escaped in SQL
        assertTrue(sqlStatements.get(0).contains("'He said, \"Hello World!\"'"));
    }

    @Test
    void testCsvWithDifferentDelimiter() throws IOException {
        Path csvFile = tempDir.resolve("semicolon.csv");
        Files.write(csvFile, Arrays.asList(
                "id;name;email",
                "1;John Doe;john@example.com",
                "2;Jane Smith;jane@example.com"
        ));

        generator.setDelimiter(";");
        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(2, sqlStatements.size());
        assertEquals("INSERT INTO users (id, name, email) VALUES (1, 'John Doe', 'john@example.com');",
                sqlStatements.get(0));
    }

    @Test
    void testCsvWithTransaction() throws IOException {
        Path csvFile = tempDir.resolve("transaction.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name",
                "1,John",
                "2,Jane"
        ));

        generator.setIncludeTransaction(true);
        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(4, sqlStatements.size());
        assertEquals("START TRANSACTION;", sqlStatements.get(0));
        assertEquals("COMMIT;", sqlStatements.get(3));
    }

    @Test
    void testCsvWithColumnMappings() throws IOException {
        Path csvFile = tempDir.resolve("mapping.csv");
        Files.write(csvFile, Arrays.asList(
                "user_id,full_name,email_address",
                "1,John Doe,john@example.com",
                "2,Jane Smith,jane@example.com"
        ));

        Map<String, String> columnMappings = new HashMap<>();
        columnMappings.put("user_id", "id");
        columnMappings.put("full_name", "name");
        columnMappings.put("email_address", "email");

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users", columnMappings);

        assertEquals(2, sqlStatements.size());
        // Should use mapped column names
        assertTrue(sqlStatements.get(0).contains("(id, name, email)"));
        assertFalse(sqlStatements.get(0).contains("user_id"));
        assertFalse(sqlStatements.get(0).contains("full_name"));
    }

    @Test
    void testCsvWithNullString() throws IOException {
        Path csvFile = tempDir.resolve("nullstring.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,description",
                "1,John Doe,NULL",
                "2,Jane Smith,null"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(2, sqlStatements.size());
        // "NULL" and "null" strings should be converted to SQL NULL
        assertTrue(sqlStatements.get(0).contains("NULL"));
        assertTrue(sqlStatements.get(1).contains("NULL"));
    }

    @Test
    void testEmptyCsvFile() throws IOException {
        Path csvFile = tempDir.resolve("empty.csv");
        Files.write(csvFile, Collections.singletonList("id,name,email"));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(0, sqlStatements.size()); // Only headers, no data rows
    }

    @Test
    void testCsvWithWhitespace() throws IOException {
        Path csvFile = tempDir.resolve("whitespace.csv");
        Files.write(csvFile, Arrays.asList(
                "  id  ,  name  ,  email  ",
                "  1  ,  John Doe  ,  john@example.com  ",
                "  2  ,  Jane Smith  ,  jane@example.com  "
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(2, sqlStatements.size());
        // Whitespace should be trimmed
        assertTrue(sqlStatements.get(0).contains("(id, name, email)"));
        assertTrue(sqlStatements.get(0).contains("'John Doe'"));
    }

    @Test
    void testCsvWithSpecialCharacters() throws IOException {
        Path csvFile = tempDir.resolve("special.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,description",
                "1,John O'Reilly,Contains single quote",
                "2,Jane Doe,Contains \\ backslash",
                "3,Bob Smith,Contains \" double quote"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

        assertEquals(3, sqlStatements.size());
        // Single quotes should be escaped in SQL
        assertTrue(sqlStatements.get(0).contains("'John O''Reilly'"));
    }

    @Test
    void testCsvWithDifferentEncoding() throws IOException {
        Path csvFile = tempDir.resolve("utf8.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,description",
                "1,Juan Pérez,Español",
                "2,李华,中文",
                "3,François,Français"
        ));

        generator.setEncoding("UTF-8");
        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "international");

        assertEquals(3, sqlStatements.size());
        // Should handle UTF-8 characters correctly
        assertTrue(sqlStatements.get(0).contains("'Juan Pérez'"));
        assertTrue(sqlStatements.get(1).contains("'李华'"));
    }

    @Test
    void testHeaderValueCountMismatch() throws IOException {
        Path csvFile = tempDir.resolve("mismatch.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,email",
                "1,John Doe" // Missing email value
        ));

        // The generator now catches exceptions internally, so we need to check the error output
        // Capture stderr to verify the error message
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        try {
            List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "users");

            // Should have no SQL statements due to the error
            assertEquals(0, sqlStatements.size());

            // Verify error was logged
            String errorOutput = errContent.toString();
            assertTrue(errorOutput.contains("Error processing line 2"));
            assertTrue(errorOutput.contains("doesn't match value count"));
            assertTrue(errorOutput.contains("Header count (3) doesn't match value count (2)"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testNonExistentFile() {
        Exception exception = assertThrows(IOException.class, () -> {
            generator.generateSqlFromCsv("nonexistent.csv", "users");
        });

        assertTrue(exception.getMessage().contains("nonexistent.csv"));
    }

    @Test
    void testMalformedCsvWithUnclosedQuotes() throws IOException {
        Path csvFile = tempDir.resolve("malformed.csv");
        Files.write(csvFile, Arrays.asList(
                "id,description",
                "1,\"Unclosed quote",
                "2,Normal value"
        ));

        // Should handle malformed CSV gracefully
        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "test");

        // Should still process what it can
        assertEquals(2, sqlStatements.size());
    }

    @Test
    void testSingleQuoteEscaping() throws IOException {
        Path csvFile = tempDir.resolve("singlequotes.csv");
        Files.write(csvFile, Arrays.asList(
                "id,message",
                "1,It's a beautiful day",
                "2,Don't stop believing",
                "3,O'Reilly & Associates"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "messages");

        assertEquals(3, sqlStatements.size());
        // Single quotes should be doubled for SQL escaping
        assertTrue(sqlStatements.get(0).contains("'It''s a beautiful day'"));
        assertTrue(sqlStatements.get(1).contains("'Don''t stop believing'"));
        assertTrue(sqlStatements.get(2).contains("'O''Reilly & Associates'"));
    }

    @Test
    void testMixedDataTypes() throws IOException {
        Path csvFile = tempDir.resolve("mixed.csv");
        Files.write(csvFile, Arrays.asList(
                "id,name,age,salary,active,notes",
                "1,John Doe,30,50000.50,true,\"Employee of the month\"",
                "2,Jane Smith,25,45000.75,false,",
                "3,Bob Johnson,,0,true,NULL"
        ));

        List<String> sqlStatements = generator.generateSqlFromCsv(csvFile.toString(), "employees");

        assertEquals(3, sqlStatements.size());

        // Test first row
        String firstRow = sqlStatements.get(0);
        assertTrue(firstRow.contains("1, 'John Doe', 30, 50000.50, TRUE, 'Employee of the month'"));

        // Test second row - empty notes should be NULL
        String secondRow = sqlStatements.get(1);
        assertTrue(secondRow.contains("2, 'Jane Smith', 25, 45000.75, FALSE, NULL"));

        // Test third row - empty age and NULL string
        String thirdRow = sqlStatements.get(2);
        assertTrue(thirdRow.contains("3, 'Bob Johnson', NULL, 0, TRUE, NULL"));
    }
}
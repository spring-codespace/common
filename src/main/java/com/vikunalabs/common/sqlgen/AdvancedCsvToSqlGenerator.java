package com.vikunalabs.common.sqlgen;

import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.util.*;

public class AdvancedCsvToSqlGenerator {
    private boolean includeTransaction;
    private String encoding;
    private String delimiter;
    private boolean useBatchInsert;
    private int batchSize;
    private boolean formatSql;
    private String indentString;

    public AdvancedCsvToSqlGenerator() {
        this.includeTransaction = true;
        this.encoding = "UTF-8";
        this.delimiter = ",";
        this.useBatchInsert = true;
        this.batchSize = 1000;
        this.formatSql = true;
        this.indentString = "  "; // 2 spaces
    }

    public AdvancedCsvToSqlGenerator(boolean includeTransaction, String encoding, String delimiter) {
        this.includeTransaction = includeTransaction;
        this.encoding = encoding;
        this.delimiter = delimiter;
        this.useBatchInsert = false;
        this.batchSize = 1000;
        this.formatSql = true;
        this.indentString = "  ";
    }

    public List<String> generateSqlFromCsv(String csvFile, String tableName) throws IOException {
        return generateSqlFromCsv(csvFile, tableName, null);
    }

    public List<String> generateSqlFromCsv(String csvFile, String tableName,
                                           Map<String, String> columnMappings) throws IOException {
        List<String> sqlStatements = new ArrayList<>();

        // Determine charset from encoding
        Charset charset = Charset.forName(encoding);

        // Use BOMInputStream to handle BOM in CSV files
        try (InputStream inputStream = Files.newInputStream(Paths.get(csvFile));
             BOMInputStream bomInputStream = new BOMInputStream(inputStream);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bomInputStream, charset))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV file is empty");
            }

            // Remove outer quotes if present and split
            List<String> headers = parseCsvLine(headerLine);

            // Apply column mappings if provided
            List<String> mappedHeaders = headers;
            if (columnMappings != null) {
                mappedHeaders = new ArrayList<>();
                for (String header : headers) {
                    mappedHeaders.add(columnMappings.getOrDefault(header, header));
                }
            }

            if (includeTransaction) {
                sqlStatements.add("START TRANSACTION;");
            }

            // Collect all rows
            List<List<String>> allRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> values = parseCsvLine(line);

                if (values.size() != headers.size()) {
                    System.err.println("Warning: Row has " + values.size() +
                            " values but header has " + headers.size() + " columns. Skipping row.");
                    continue;
                }

                allRows.add(values);
            }

            // Generate INSERT statements based on batch mode
            if (useBatchInsert) {
                // Batch INSERT statements
                for (int i = 0; i < allRows.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, allRows.size());
                    List<List<String>> batch = allRows.subList(i, end);
                    String sql = generateBatchInsertStatement(tableName, mappedHeaders, batch);
                    sqlStatements.add(sql);
                }
            } else {
                // Individual INSERT statements (original behavior)
                for (List<String> values : allRows) {
                    String sql = generateInsertStatement(tableName, mappedHeaders, values);
                    sqlStatements.add(sql);
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading CSV: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to process CSV file", e);
        }

        if (includeTransaction) {
            sqlStatements.add("COMMIT;");
        }

        return sqlStatements;
    }

    /**
     * Parse a CSV line that might be wrapped in quotes
     * Handles format: "field1,field2,field3" or field1,field2,field3
     */
    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();

        // Remove leading/trailing whitespace
        line = line.trim();

        // Check if entire line is wrapped in quotes
        if (line.startsWith("\"") && line.endsWith("\"")) {
            // Remove outer quotes
            line = line.substring(1, line.length() - 1);
        }

        // Now split by delimiter, handling escaped quotes
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Check if it's an escaped quote (two consecutive quotes)
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++; // Skip the next quote
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                // Found a delimiter outside quotes - end of field
                values.add(currentValue.toString());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }

        // Add the last value
        values.add(currentValue.toString());

        return values;
    }

    private String generateInsertStatement(String tableName, List<String> headers, List<String> values) {
        if (headers.size() != values.size()) {
            throw new IllegalArgumentException(
                    String.format("Header count (%d) doesn't match value count (%d)",
                            headers.size(), values.size()));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" ");

        if (formatSql) {
            sql.append("\n").append(indentString).append("(");

            // Add column names (one per line if many columns)
            if (headers.size() > 5) {
                for (int i = 0; i < headers.size(); i++) {
                    sql.append("\n").append(indentString).append(indentString);
                    sql.append(headers.get(i));
                    if (i < headers.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append("\n").append(indentString);
            } else {
                // Short column list on one line
                for (int i = 0; i < headers.size(); i++) {
                    sql.append(headers.get(i));
                    if (i < headers.size() - 1) {
                        sql.append(", ");
                    }
                }
            }

            sql.append(")\n");
            sql.append("VALUES\n").append(indentString).append("(");

            // Add values
            List<String> formattedValues = new ArrayList<>();
            for (String value : values) {
                formattedValues.add(formatValue(value));
            }

            if (headers.size() > 5) {
                for (int i = 0; i < formattedValues.size(); i++) {
                    sql.append("\n").append(indentString).append(indentString);
                    sql.append(formattedValues.get(i));
                    if (i < formattedValues.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append("\n").append(indentString);
            } else {
                sql.append(String.join(", ", formattedValues));
            }

            sql.append(");");
        } else {
            // Compact format (original)
            sql.append("(");
            for (int i = 0; i < headers.size(); i++) {
                sql.append(headers.get(i));
                if (i < headers.size() - 1) {
                    sql.append(", ");
                }
            }
            sql.append(") VALUES (");

            List<String> formattedValues = new ArrayList<>();
            for (String value : values) {
                formattedValues.add(formatValue(value));
            }
            sql.append(String.join(", ", formattedValues));
            sql.append(");");
        }

        return sql.toString();
    }

    /**
     * Generate a batch INSERT statement for multiple rows
     * Format: INSERT INTO table (col1, col2) VALUES (val1, val2), (val3, val4), ...;
     */
    private String generateBatchInsertStatement(String tableName, List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate batch INSERT with no rows");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName);

        if (formatSql) {
            sql.append("\n").append(indentString).append("(");

            // Add column names (one per line if many columns)
            if (headers.size() > 5) {
                for (int i = 0; i < headers.size(); i++) {
                    sql.append("\n").append(indentString).append(indentString);
                    sql.append(headers.get(i));
                    if (i < headers.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append("\n").append(indentString);
            } else {
                // Short column list on one line
                for (int i = 0; i < headers.size(); i++) {
                    sql.append(headers.get(i));
                    if (i < headers.size() - 1) {
                        sql.append(", ");
                    }
                }
            }

            sql.append(")\n");
            sql.append("VALUES\n");

            // Add each row's values
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> values = rows.get(rowIndex);

                if (values.size() != headers.size()) {
                    throw new IllegalArgumentException(
                            String.format("Row %d: Header count (%d) doesn't match value count (%d)",
                                    rowIndex, headers.size(), values.size()));
                }

                sql.append(indentString).append("(");

                // Add formatted values
                List<String> formattedValues = new ArrayList<>();
                for (String value : values) {
                    formattedValues.add(formatValue(value));
                }

                if (headers.size() > 5) {
                    // Many columns: one value per line
                    for (int i = 0; i < formattedValues.size(); i++) {
                        sql.append("\n").append(indentString).append(indentString);
                        sql.append(formattedValues.get(i));
                        if (i < formattedValues.size() - 1) {
                            sql.append(",");
                        }
                    }
                    sql.append("\n").append(indentString);
                } else {
                    // Few columns: all on one line
                    sql.append(String.join(", ", formattedValues));
                }

                sql.append(")");

                // Add comma between rows, semicolon after last row
                if (rowIndex < rows.size() - 1) {
                    sql.append(",\n");
                } else {
                    sql.append(";");
                }
            }
        } else {
            // Compact format (original)
            sql.append(" (");
            for (int i = 0; i < headers.size(); i++) {
                sql.append(headers.get(i));
                if (i < headers.size() - 1) {
                    sql.append(", ");
                }
            }
            sql.append(") VALUES ");

            // Add each row's values
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> values = rows.get(rowIndex);

                if (values.size() != headers.size()) {
                    throw new IllegalArgumentException(
                            String.format("Row %d: Header count (%d) doesn't match value count (%d)",
                                    rowIndex, headers.size(), values.size()));
                }

                sql.append("(");

                List<String> formattedValues = new ArrayList<>();
                for (String value : values) {
                    formattedValues.add(formatValue(value));
                }

                sql.append(String.join(", ", formattedValues));
                sql.append(")");

                if (rowIndex < rows.size() - 1) {
                    sql.append(",\n");
                } else {
                    sql.append(";");
                }
            }
        }

        return sql.toString();
    }

    private String formatValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NULL";
        }

        // Check for NULL string
        if ("NULL".equalsIgnoreCase(value.trim())) {
            return "NULL";
        }

        // Check if value is a date
        if (isDate(value)) {
            return "'" + value + "'";
        }

        // Check for numeric values
        if (isNumeric(value)) {
            return value;
        }

        // Check for boolean values
        if (isBoolean(value)) {
            return value.toUpperCase();
        }

        // Escape single quotes and wrap in quotes for strings
        String escapedValue = value.replace("'", "''");
        return "'" + escapedValue + "'";
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBoolean(String str) {
        return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
    }

    private boolean isDate(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            sdf.parse(value);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    // Getters and setters
    public void setIncludeTransaction(boolean includeTransaction) {
        this.includeTransaction = includeTransaction;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public void setUseBatchInsert(boolean useBatchInsert) {
        this.useBatchInsert = useBatchInsert;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
    }

    public void setFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
    }

    public void setIndentString(String indentString) {
        this.indentString = indentString;
    }

    public static void main(String[] args) {
        // Accept command line arguments or use defaults
        String csvFile = args.length > 0 ? args[0] : "/home/vikunalabs/workspace/code/common/src/main/java/com/vikunalabs/common/sqlgen/db_output.csv";
        String tableName = args.length > 1 ? args[1] : "test_table";

        AdvancedCsvToSqlGenerator generator = new AdvancedCsvToSqlGenerator();

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-t":
                    generator.setIncludeTransaction(true);
                    break;
                case "-e":
                    if (i + 1 < args.length) {
                        generator.setEncoding(args[++i]);
                    }
                    break;
                case "-d":
                    if (i + 1 < args.length) {
                        generator.setDelimiter(args[++i]);
                    }
                    break;
                case "-b":
                case "--batch":
                    generator.setUseBatchInsert(true);
                    break;
                case "-bs":
                case "--batch-size":
                    if (i + 1 < args.length) {
                        try {
                            int size = Integer.parseInt(args[++i]);
                            generator.setBatchSize(size);
                            generator.setUseBatchInsert(true); // Auto-enable batch mode
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid batch size: " + args[i]);
                        }
                    }
                    break;
                case "-f":
                case "--format":
                    generator.setFormatSql(true);
                    break;
                case "--no-format":
                    generator.setFormatSql(false);
                    break;
                case "--indent":
                    if (i + 1 < args.length) {
                        String indent = args[++i];
                        // Support special values
                        if ("tab".equalsIgnoreCase(indent)) {
                            generator.setIndentString("\t");
                        } else if (indent.matches("\\d+")) {
                            // Number means that many spaces
                            int spaces = Integer.parseInt(indent);
                            generator.setIndentString(" ".repeat(spaces));
                        } else {
                            generator.setIndentString(indent);
                        }
                    }
                    break;
            }
        }

        try {
            List<String> sqlStatements = generator.generateSqlFromCsv(csvFile, tableName);

            // Print to console
            for (String sql : sqlStatements) {
                System.out.println(sql);
            }

            // Save to file with BufferedWriter
            String outputFile = tableName + "_inserts.sql";
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardCharsets.UTF_8)) {
                for (String sql : sqlStatements) {
                    writer.write(sql);
                    writer.newLine();
                }
            }

            int statementCount = sqlStatements.size();
            if (generator.includeTransaction) {
                statementCount -= 2; // Subtract START TRANSACTION and COMMIT
            }

            System.out.println("\nGenerated " + statementCount + " INSERT statement(s)");
            if (generator.useBatchInsert) {
                System.out.println("Batch mode: " + generator.batchSize + " rows per statement");
            }
            System.out.println("Saved to: " + outputFile);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
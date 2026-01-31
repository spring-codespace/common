package com.vikunalabs.common.sqlgen;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.util.*;

public class AdvancedCsvToSqlGenerator {
    private boolean includeTransaction;
    private String encoding;
    private String delimiter;

    public AdvancedCsvToSqlGenerator() {
        this.includeTransaction = false;
        this.encoding = "UTF-8";
        this.delimiter = ",";
    }

    public AdvancedCsvToSqlGenerator(boolean includeTransaction, String encoding, String delimiter) {
        this.includeTransaction = includeTransaction;
        this.encoding = encoding;
        this.delimiter = delimiter;
    }

    public List<String> generateSqlFromCsv(String csvFile, String tableName) throws IOException {
        return generateSqlFromCsv(csvFile, tableName, null);
    }

    public List<String> generateSqlFromCsv(String csvFile, String tableName,
                                           Map<String, String> columnMappings) throws IOException {
        List<String> sqlStatements = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        if (includeTransaction) {
            sqlStatements.add("START TRANSACTION;");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(csvFile)), encoding))) {
            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    if (isFirstLine) {
                        headers = parseCsvLine(line);
                        isFirstLine = false;
                    } else {
                        List<String> values = parseCsvLine(line);

                        // Apply column mappings if provided
                        List<String> mappedHeaders = headers;
                        if (columnMappings != null) {
                            mappedHeaders = new ArrayList<>();
                            for (String header : headers) {
                                mappedHeaders.add(columnMappings.getOrDefault(header, header));
                            }
                        }

                        String sql = generateInsertStatement(tableName, mappedHeaders, values);
                        sqlStatements.add(sql);
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid data format at line " + lineNumber + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error at line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        if (includeTransaction) {
            sqlStatements.add("COMMIT;");
        }

        return sqlStatements;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Handle escaped quotes ("")
                    field.append('"');
                    i++; // Skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                result.add(cleanField(field.toString()));
                field.setLength(0);
            } else {
                field.append(c);
            }
        }

        result.add(cleanField(field.toString()));
        return result;
    }

    private String cleanField(String field) {
        String cleaned = field.trim();
        // Remove surrounding quotes if present
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String generateInsertStatement(String tableName, List<String> headers, List<String> values) {
        if (headers.size() != values.size()) {
            throw new IllegalArgumentException(
                    String.format("Header count (%d) doesn't match value count (%d)",
                            headers.size(), values.size()));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(quoteColumnNames(headers)).append(" (");

        // Add column names
        sql.append(String.join(", ", headers));

        sql.append(") VALUES (");

        // Add values
        List<String> formattedValues = new ArrayList<>();
        for (String value : values) {
            formattedValues.add(formatValue(value));
        }

        sql.append(String.join(", ", formattedValues));
        sql.append(");");

        return sql.toString();
    }

    private String quoteColumnNames(List<String> columns) {
        List<String> quotedColumns = new ArrayList<>();
        for (String column : columns) {
            quotedColumns.add("`" + column + "`"); // MySQL-style quoting
        }
        return String.join(", ", quotedColumns);
    }

    private String formatValue(String value) {
        if (value == null || value.isEmpty()) {
            return "NULL";
        }

        // Check if value is a date (could be customizable based on the format in your CSV)
        if (isDate(value)) {
            return "'" + formatDate(value) + "'";
        }

        // Check for numeric values
        if (isNumeric(value)) {
            return value;
        }

        // Check for boolean values
        if (isBoolean(value)) {
            return value.toUpperCase();
        }

        // Check for NULL string
        if ("NULL".equalsIgnoreCase(value)) {
            return "NULL";
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
            new SimpleDateFormat("yyyy-MM-dd").parse(value);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private String formatDate(String value) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = inputFormat.parse(value);
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
            return outputFormat.format(date);
        } catch (ParseException e) {
            return value; // Return the original value if not a valid date
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

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java AdvancedCsvToSqlGenerator <csvFile> <tableName> [options]");
            System.out.println("Options:");
            System.out.println("  -t    Include transaction statements");
            System.out.println("  -e    Encoding (default: UTF-8)");
            System.out.println("  -d    Delimiter (default: ,)");
            return;
        }

        String csvFile = args[0];
        String tableName = args[1];

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
            System.out.println("\nGenerated " + sqlStatements.size() + " INSERT statements");
            System.out.println("Saved to: " + outputFile);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

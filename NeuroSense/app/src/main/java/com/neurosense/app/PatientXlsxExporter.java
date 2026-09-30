package com.neurosense.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Produces a standards-compliant XLSX workbook without external storage libraries. */
final class PatientXlsxExporter {
    private PatientXlsxExporter() {
    }

    static byte[] create(AdminDataStore.PatientRecord patient) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "[Content_Types].xml", contentTypes());
            add(zip, "_rels/.rels", packageRelationships());
            add(zip, "xl/workbook.xml", workbook(false));
            add(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            add(zip, "xl/styles.xml", styles());
            add(zip, "xl/worksheets/sheet1.xml", sessionSheet(patient));
            add(zip, "xl/worksheets/sheet2.xml", frequencySheet(patient));
        }
        return output.toByteArray();
    }

    static byte[] createPtpActivity(List<PtpActivityStore.ActivityRecord> records)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "[Content_Types].xml", contentTypes());
            add(zip, "_rels/.rels", packageRelationships());
            add(zip, "xl/workbook.xml", workbook(true));
            add(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            add(zip, "xl/styles.xml", styles());
            add(zip, "xl/worksheets/sheet1.xml", ptpActivitySheet(records));
            add(zip, "xl/worksheets/sheet2.xml", ptpFrequencySheet(records));
        }
        return output.toByteArray();
    }

    private static String ptpActivitySheet(
            List<PtpActivityStore.ActivityRecord> records) {
        StringBuilder rows = new StringBuilder();
        int row = 1;
        rows.append(row(row++, cell("A", 1, "NeuroSense PTP Activity", 1)));
        rows.append(row(row++, cell("A", 2, "Tracked connections", 2),
                numberCell("B", 2, records.size(), 0)));
        row++;
        rows.append(row(row,
                cell("A", row, "Patient name", 2),
                cell("B", row, "Phone number", 2),
                cell("C", row, "Connected", 2),
                cell("D", row, "Disconnected", 2),
                cell("E", row, "Connection duration", 2),
                cell("F", row, "First device use", 2),
                cell("G", row, "Last device use", 2),
                cell("H", row, "Active-use duration", 2),
                cell("I", row, "Last active frequency (Hz)", 2),
                cell("J", row, "Active level changes", 2)));
        row++;
        for (PtpActivityStore.ActivityRecord record : records) {
            rows.append(row(row,
                    cell("A", row, record.patientName, 0),
                    cell("B", row, record.phoneNumber, 0),
                    dateCell("C", row, record.connectedAt),
                    record.disconnectedAt > 0
                            ? dateCell("D", row, record.disconnectedAt)
                            : cell("D", row, "Connected", 0),
                    durationCell("E", row, record.connectionDurationMillis()),
                    record.firstUsedAt() > 0
                            ? dateCell("F", row, record.firstUsedAt())
                            : cell("F", row, "Not used", 0),
                    record.lastUsedAt() > 0
                            ? dateCell("G", row, record.lastUsedAt())
                            : cell("G", row, "Not used", 0),
                    durationCell("H", row, record.activeDurationMillis()),
                    numberCell("I", row, record.lastActiveFrequency(), 0),
                    numberCell("J", row, record.frequencyChangeCount(), 0)));
            row++;
        }
        return worksheet(rows.toString(),
                "<cols><col min=\"1\" max=\"2\" width=\"24\" customWidth=\"1\"/>" +
                        "<col min=\"3\" max=\"7\" width=\"22\" customWidth=\"1\"/>" +
                        "<col min=\"8\" max=\"10\" width=\"24\" customWidth=\"1\"/></cols>");
    }

    private static String ptpFrequencySheet(
            List<PtpActivityStore.ActivityRecord> records) {
        StringBuilder rows = new StringBuilder();
        int row = 1;
        rows.append(row(row,
                cell("A", row, "Patient name", 2),
                cell("B", row, "Connection started", 2),
                cell("C", row, "Change number", 2),
                cell("D", row, "Change time", 2),
                cell("E", row, "Elapsed from connection", 2),
                cell("F", row, "Frequency (Hz)", 2),
                cell("G", row, "Device state", 2)));
        row++;
        for (PtpActivityStore.ActivityRecord record : records) {
            int command = 1;
            for (PtpActivityStore.FrequencyMark mark : record.frequencyMarks) {
                rows.append(row(row,
                        cell("A", row, record.patientName, 0),
                        dateCell("B", row, record.connectedAt),
                        numberCell("C", row, command++, 0),
                        dateCell("D", row, mark.timestamp),
                        durationCell("E", row, Math.max(0L,
                                mark.timestamp - record.connectedAt)),
                        numberCell("F", row, mark.frequency, 0),
                        cell("G", row, mark.frequency == 0
                                ? "Stopped" : "Active", 0)));
                row++;
            }
        }
        return worksheet(rows.toString(),
                "<cols><col min=\"1\" max=\"2\" width=\"24\" customWidth=\"1\"/>" +
                        "<col min=\"3\" max=\"3\" width=\"18\" customWidth=\"1\"/>" +
                        "<col min=\"4\" max=\"5\" width=\"22\" customWidth=\"1\"/>" +
                        "<col min=\"6\" max=\"7\" width=\"20\" customWidth=\"1\"/></cols>");
    }

    private static String sessionSheet(AdminDataStore.PatientRecord patient) {
        StringBuilder rows = new StringBuilder();
        int row = 1;
        rows.append(row(row++, cell("A", 1, "NeuroSense Patient History", 1)));
        rows.append(row(row, cell("A", row, "Patient name", 2),
                cell("B", row++, patient.name, 0)));
        rows.append(row(row, cell("A", row, "Age", 2),
                numberCell("B", row++, patient.age, 0)));
        rows.append(row(row, cell("A", row, "Sex", 2),
                cell("B", row++, patient.sex, 0)));
        rows.append(row(row, cell("A", row, "Phone number", 2),
                cell("B", row++, patient.phoneNumber, 0)));
        rows.append(row(row, cell("A", row, "Aadhaar ID", 2),
                cell("B", row++, patient.aadhaarId, 0)));
        rows.append(row(row, cell("A", row, "ABHA ID", 2),
                cell("B", row++, patient.abhaId, 0)));
        StringBuilder reportNames = new StringBuilder();
        for (AdminDataStore.ReportAttachment report : patient.previousReports) {
            if (reportNames.length() > 0) {
                reportNames.append("; ");
            }
            reportNames.append(report.name);
        }
        rows.append(row(row, cell("A", row, "Previous reports", 2),
                cell("B", row++, reportNames.length() == 0
                        ? "None" : reportNames.toString(), 0)));
        rows.append(row(row, cell("A", row, "Total sessions", 2),
                numberCell("B", row++, patient.sessions.size(), 0)));
        row++;
        rows.append(row(row,
                cell("A", row, "Session number", 2),
                cell("B", row, "Started", 2),
                cell("C", row, "Ended", 2),
                cell("D", row, "Duration", 2),
                cell("E", row, "Initial frequency (Hz)", 2),
                cell("F", row, "Last active frequency (Hz)", 2),
                cell("G", row, "Frequency changes", 2),
                cell("H", row, "Session objective", 2),
                cell("I", row, "Patient's complaint", 2)));
        row++;
        for (AdminDataStore.SessionRecord session : patient.sessions) {
            int initial = session.frequencyMarks.isEmpty()
                    ? 0 : session.frequencyMarks.get(0).frequency;
            rows.append(row(row,
                    cell("A", row, session.sessionNumber, 0),
                    dateCell("B", row, session.startedAt),
                    session.endedAt > 0
                            ? dateCell("C", row, session.endedAt)
                            : cell("C", row, "In progress", 0),
                    durationCell("D", row, session.durationMillis()),
                    numberCell("E", row, initial, 0),
                    numberCell("F", row, session.lastActiveFrequency(), 0),
                    numberCell("G", row, session.frequencyMarks.size(), 0),
                    cell("H", row, session.objective, 0),
                    cell("I", row, session.review, 0)));
            row++;
        }
        return worksheet(rows.toString(),
                "<cols><col min=\"1\" max=\"1\" width=\"18\" customWidth=\"1\"/>" +
                        "<col min=\"2\" max=\"3\" width=\"21\" customWidth=\"1\"/>" +
                        "<col min=\"4\" max=\"7\" width=\"20\" customWidth=\"1\"/>" +
                        "<col min=\"8\" max=\"9\" width=\"36\" customWidth=\"1\"/></cols>");
    }

    private static String frequencySheet(AdminDataStore.PatientRecord patient) {
        StringBuilder rows = new StringBuilder();
        int row = 1;
        rows.append(row(row,
                cell("A", row, "Session number", 2),
                cell("B", row, "Change number", 2),
                cell("C", row, "Timestamp", 2),
                cell("D", row, "Elapsed from start", 2),
                cell("E", row, "Frequency (Hz)", 2)));
        row++;
        for (AdminDataStore.SessionRecord session : patient.sessions) {
            int change = 1;
            for (AdminDataStore.FrequencyMark mark : session.frequencyMarks) {
                rows.append(row(row,
                        cell("A", row, session.sessionNumber, 0),
                        numberCell("B", row, change++, 0),
                        dateCell("C", row, mark.timestamp),
                        durationCell("D", row, Math.max(0L,
                                mark.timestamp - session.startedAt)),
                        numberCell("E", row, mark.frequency, 0)));
                row++;
            }
        }
        return worksheet(rows.toString(),
                "<cols><col min=\"1\" max=\"2\" width=\"18\" customWidth=\"1\"/>" +
                        "<col min=\"3\" max=\"4\" width=\"22\" customWidth=\"1\"/>" +
                        "<col min=\"5\" max=\"5\" width=\"20\" customWidth=\"1\"/></cols>");
    }

    private static String worksheet(String rows, String columns) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>" +
                columns + "<sheetData>" + rows + "</sheetData></worksheet>";
    }

    private static String row(int number, String... cells) {
        StringBuilder value = new StringBuilder("<row r=\"").append(number).append("\">");
        for (String cell : cells) {
            value.append(cell);
        }
        return value.append("</row>").toString();
    }

    private static String cell(String column, int row, String value, int style) {
        return "<c r=\"" + column + row + "\" t=\"inlineStr\" s=\"" + style +
                "\"><is><t xml:space=\"preserve\">" + escape(value) +
                "</t></is></c>";
    }

    private static String numberCell(String column, int row, long value, int style) {
        return "<c r=\"" + column + row + "\" s=\"" + style +
                "\"><v>" + value + "</v></c>";
    }

    private static String dateCell(String column, int row, long timestamp) {
        long localTimestamp = timestamp +
                TimeZone.getDefault().getOffset(timestamp);
        double serial = localTimestamp / 86_400_000d + 25_569d;
        return decimalCell(column, row, serial, 3);
    }

    private static String durationCell(String column, int row, long millis) {
        return decimalCell(column, row, Math.max(0L, millis) / 86_400_000d, 4);
    }

    private static String decimalCell(String column, int row,
                                      double value, int style) {
        return "<c r=\"" + column + row + "\" s=\"" + style +
                "\"><v>" + Double.toString(value) + "</v></c>";
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void add(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>";
    }

    private static String packageRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbook(boolean ptpActivity) {
        String firstSheet = ptpActivity ? "PTP Activity" : "Patient History";
        String secondSheet = ptpActivity ? "Frequency Timeline" : "Frequency Changes";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + firstSheet + "\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"" + secondSheet + "\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>";
    }

    private static String workbookRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd hh:mm:ss\"/>" +
                "<numFmt numFmtId=\"165\" formatCode=\"[h]:mm:ss\"/></numFmts>" +
                "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Aptos\"/></font>" +
                "<font><b/><sz val=\"18\"/><color rgb=\"FF1C1B2B\"/><name val=\"Aptos Display\"/></font>" +
                "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Aptos\"/></font></fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>" +
                "<fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF6755D9\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"5\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
                "</styleSheet>";
    }
}

import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "C:/Users/user/Documents/Github/NeuroVibe/outputs/neurovibe_bom";
const outputFile = `${outputDir}/NeuroVibe_BOM_and_Connections.xlsx`;

await fs.mkdir(outputDir, { recursive: true });

const wb = Workbook.create();
const bom = wb.worksheets.add("BOM");
const connections = wb.worksheets.add("Connections");

const navy = "#12304A";
const teal = "#0F766E";
const cyan = "#DDF5F2";
const paleBlue = "#EAF3F8";
const paleYellow = "#FFF7D6";
const paleRed = "#FDECEC";
const grid = "#C9D6DF";
const text = "#17324D";
const white = "#FFFFFF";

// ---------------- BOM ----------------
bom.showGridLines = false;
bom.getRange("A1:I1").merge();
bom.getRange("A1").values = [["NeuroVibe Device — Bill of Materials"]];
bom.getRange("A1:I1").format = {
  fill: navy,
  font: { bold: true, color: white, size: 18 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
bom.getRange("A1:I1").format.rowHeight = 34;

bom.getRange("A2:I2").merge();
bom.getRange("A2").values = [["Prototype hardware: ESP32-C3 Super Mini + DRV8833 dual motor driver + two 3 V ERM coin motors"]];
bom.getRange("A2:I2").format = {
  fill: paleBlue,
  font: { color: text, italic: true, size: 10 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
bom.getRange("A2:I2").format.rowHeight = 24;

bom.getRange("A4:B4").values = [["Unique BOM lines", "Total component quantity"]];
bom.getRange("A5").formulas = [["=COUNTA(A8:A11)"]];
bom.getRange("B5").formulas = [["=SUM(D8:D11)"]];
bom.getRange("A4:B4").format = {
  fill: teal,
  font: { bold: true, color: white },
  horizontalAlignment: "center",
};
bom.getRange("A5:B5").format = {
  fill: cyan,
  font: { bold: true, color: navy, size: 14 },
  horizontalAlignment: "center",
  borders: { preset: "all", style: "thin", color: grid },
};
bom.getRange("A5:B5").format.numberFormat = "0";

const bomHeaders = [
  "S.no",
  "Manufacturing Part No",
  "Designator",
  "Quantity",
  "Description (VALUE)",
  "Package (Case)",
  "Top/Bottom",
  "Points (No. of Pins)",
  "Comments",
];
const bomRows = [
  [1, "ESP32-C3-SUPERMINI", "U1", 1, "ESP32-C3 Super Mini Wi-Fi + BLE controller board", "Super Mini module, approx. 22.5 × 18 mm", "Top", 16, "Generic development-board identifier. Confirm the exact supplier pinout and purchasing MPN before production."],
  [2, "MODULE-DRV8833", "U2", 1, "DRV8833 dual H-bridge DC motor driver module", "Breakout module", "Top", 12, "One dual-channel driver controls both motors. Module pin count and nSLEEP pull-up may vary; confirm the selected board."],
  [3, "GRM31CR71E106KA12L", "C1", 1, "10 µF, 25 V, X7R ceramic capacitor", "1206 (3216 metric)", "Top", 2, "Place directly across DRV8833 VM and GND. This suggested Murata MPN may be replaced by an equivalent rated part."],
  [4, "ERM-COIN-3V", "M1, M2", 2, "3 V ERM coin vibration motor", "Coin motor with 2 wire leads", "External", 2, "Supplier-specific placeholder MPN. Select two identical motors and verify rated current, starting current and mechanical mounting."],
];
bom.getRange("A7:I7").values = [bomHeaders];
bom.getRange("A8:I11").values = bomRows;
bom.getRange("A7:I11").format.borders = { preset: "all", style: "thin", color: grid };
bom.getRange("A7:I7").format = {
  fill: navy,
  font: { bold: true, color: white },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: grid },
};
bom.getRange("A7:I7").format.rowHeight = 40;
bom.getRange("A8:I11").format = {
  font: { color: text, size: 10 },
  verticalAlignment: "top",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: grid },
};
bom.getRange("A8:D11").format.horizontalAlignment = "center";
bom.getRange("F8:H11").format.horizontalAlignment = "center";
bom.getRange("A8:I8").format.fill = "#F7FBFD";
bom.getRange("A10:I10").format.fill = "#F7FBFD";
bom.tables.add("A7:I11", true, "NeuroVibeBOMTable").style = "TableStyleMedium2";

bom.getRange("A13:I13").merge();
bom.getRange("A13").values = [["PURCHASING AND SAFETY NOTES"]];
bom.getRange("A13:I13").format = { fill: teal, font: { bold: true, color: white }, horizontalAlignment: "left" };
bom.getRange("A14:I14").merge();
bom.getRange("A14").values = [["1. ESP32-C3 and DRV8833 module identifiers are prototype placeholders; replace them with supplier-orderable MPNs after the exact boards are selected."]];
bom.getRange("A15:I15").merge();
bom.getRange("A15").values = [["2. The 3 V motor supply must support the combined start/stall current of both motors. Never power a motor from an ESP32 GPIO pin."]];
bom.getRange("A16:I16").merge();
bom.getRange("A16").values = [["3. This is a prototype BOM, not a medical-production BOM. Electrical, thermal, EMC, biocompatibility and safety review are required before clinical use."]];
bom.getRange("A14:I16").format = {
  fill: paleYellow,
  font: { color: text, size: 10 },
  wrapText: true,
  verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: "#E5C96A" },
};
bom.getRange("A14:I16").format.rowHeight = 29;

bom.getRange("A1:A16").format.columnWidth = 18;
bom.getRange("B1:B16").format.columnWidth = 25;
bom.getRange("C1:C16").format.columnWidth = 13;
bom.getRange("D1:D16").format.columnWidth = 10;
bom.getRange("E1:E16").format.columnWidth = 35;
bom.getRange("F1:F16").format.columnWidth = 25;
bom.getRange("G1:G16").format.columnWidth = 12;
bom.getRange("H1:H16").format.columnWidth = 17;
bom.getRange("I1:I16").format.columnWidth = 48;
bom.freezePanes.freezeRows(7);

// ---------------- Connections ----------------
connections.showGridLines = false;
connections.getRange("A1:G1").merge();
connections.getRange("A1").values = [["NeuroVibe Electrical Connection Schedule"]];
connections.getRange("A1:G1").format = {
  fill: navy,
  font: { bold: true, color: white, size: 18 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
connections.getRange("A1:G1").format.rowHeight = 34;

connections.getRange("A2:G2").merge();
connections.getRange("A2").values = [["Firmware mapping verified from NeuroVibe.ino: motor 1 uses GPIO 2/3; motor 2 uses GPIO 4/5"]];
connections.getRange("A2:G2").format = {
  fill: paleBlue,
  font: { color: text, italic: true },
  horizontalAlignment: "center",
};

const connectionHeaders = ["From Designator", "From Pin", "To Designator", "To Pin", "Net / Signal", "Purpose", "Connection Notes"];
const connectionRows = [
  ["U1 (ESP32-C3)", "GPIO2", "U2 (DRV8833)", "AIN1", "M1_IN1", "Motor 1 PWM/control input 1", "Firmware pin mapping."],
  ["U1 (ESP32-C3)", "GPIO3", "U2 (DRV8833)", "AIN2", "M1_IN2", "Motor 1 control input 2", "Firmware currently drives this low for one-direction operation."],
  ["U1 (ESP32-C3)", "GPIO4", "U2 (DRV8833)", "BIN1", "M2_IN1", "Motor 2 PWM/control input 1", "Firmware pin mapping."],
  ["U1 (ESP32-C3)", "GPIO5", "U2 (DRV8833)", "BIN2", "M2_IN2", "Motor 2 control input 2", "Firmware currently drives this low for one-direction operation."],
  ["U1 (ESP32-C3)", "3V3", "U2 (DRV8833)", "nSLEEP", "DRIVER_ENABLE", "Keep motor driver enabled", "Connect only if the selected module does not already pull nSLEEP high. Verify module schematic."],
  ["Regulated motor supply", "+3 V", "U2 (DRV8833)", "VM", "MOTOR_3V", "Motor power input", "Supply must handle both motors' combined start/stall current."],
  ["Regulated motor supply", "GND", "U2 (DRV8833)", "GND", "GND", "Motor power return", "All grounds must be common."],
  ["U1 (ESP32-C3)", "GND", "U2 (DRV8833)", "GND", "GND", "Logic reference", "Required common ground between controller and driver."],
  ["C1 (10 µF)", "Terminal 1", "U2 (DRV8833)", "VM", "MOTOR_3V", "Supply decoupling", "Place C1 as close as possible to VM and GND. Ceramic capacitor is non-polarized."],
  ["C1 (10 µF)", "Terminal 2", "U2 (DRV8833)", "GND", "GND", "Supply decoupling return", "Use short, low-impedance traces/wires."],
  ["U2 (DRV8833)", "AOUT1", "M1", "Terminal 1", "MOTOR1_A", "Motor 1 output", "Swap the two motor wires only if direction matters mechanically."],
  ["U2 (DRV8833)", "AOUT2", "M1", "Terminal 2", "MOTOR1_B", "Motor 1 output", "Do not connect motor directly to ESP32 pins."],
  ["U2 (DRV8833)", "BOUT1", "M2", "Terminal 1", "MOTOR2_A", "Motor 2 output", "Use the same motor model as M1."],
  ["U2 (DRV8833)", "BOUT2", "M2", "Terminal 2", "MOTOR2_B", "Motor 2 output", "Do not connect motor directly to ESP32 pins."],
  ["USB / regulated source", "5 V / VBUS", "U1 (ESP32-C3)", "5V / VBUS", "BOARD_POWER", "Power the ESP32-C3 board", "Use the exact input named by the selected ESP32-C3 Super Mini board. Do not apply 5 V to 3V3."],
];
connections.getRange("A4:G4").values = [connectionHeaders];
connections.getRange("A5:G19").values = connectionRows;
connections.getRange("A4:G4").format = {
  fill: navy,
  font: { bold: true, color: white },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: grid },
};
connections.getRange("A4:G4").format.rowHeight = 34;
connections.getRange("A5:G19").format = {
  font: { color: text, size: 10 },
  verticalAlignment: "top",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: grid },
};
connections.getRange("A5:E19").format.horizontalAlignment = "center";
connections.getRange("A9:G9").format.fill = cyan;
connections.getRange("A10:G14").format.fill = paleYellow;
connections.getRange("A19:G19").format.fill = paleBlue;
connections.tables.add("A4:G19", true, "NeuroVibeConnectionsTable").style = "TableStyleMedium2";

connections.getRange("A21:G21").merge();
connections.getRange("A21").values = [["IMPORTANT: The PWM duty cycle controls average motor voltage/speed; it does not guarantee an exact vibration frequency without calibration or sensor feedback."]];
connections.getRange("A21:G21").format = {
  fill: paleRed,
  font: { bold: true, color: "#8A1C1C" },
  wrapText: true,
  horizontalAlignment: "center",
  verticalAlignment: "center",
  borders: { preset: "all", style: "medium", color: "#C84B4B" },
};
connections.getRange("A21:G21").format.rowHeight = 38;

connections.getRange("A1:A21").format.columnWidth = 23;
connections.getRange("B1:B21").format.columnWidth = 15;
connections.getRange("C1:C21").format.columnWidth = 22;
connections.getRange("D1:D21").format.columnWidth = 15;
connections.getRange("E1:E21").format.columnWidth = 18;
connections.getRange("F1:F21").format.columnWidth = 28;
connections.getRange("G1:G21").format.columnWidth = 46;
connections.freezePanes.freezeRows(4);

// Export and visual QA images.
const bomPreview = await wb.render({ sheetName: "BOM", autoCrop: "all", scale: 1, format: "png" });
await fs.writeFile(`${outputDir}/BOM_preview.png`, new Uint8Array(await bomPreview.arrayBuffer()));
const connectionsPreview = await wb.render({ sheetName: "Connections", autoCrop: "all", scale: 1, format: "png" });
await fs.writeFile(`${outputDir}/Connections_preview.png`, new Uint8Array(await connectionsPreview.arrayBuffer()));

const inspection = await wb.inspect({ kind: "workbook,sheet,table", maxChars: 7000, tableMaxRows: 20, tableMaxCols: 10 });
await fs.writeFile(`${outputDir}/inspection.txt`, inspection.ndjson ?? String(inspection));
const formulaErrors = await wb.inspect({ kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A", options: { useRegex: true, maxResults: 100 }, maxChars: 5000 });
await fs.writeFile(`${outputDir}/formula_errors.txt`, formulaErrors.ndjson ?? String(formulaErrors));

const xlsx = await SpreadsheetFile.exportXlsx(wb);
await xlsx.save(outputFile);

console.log(outputFile);

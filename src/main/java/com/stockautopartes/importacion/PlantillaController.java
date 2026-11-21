package com.stockautopartes.importacion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Plantilla de ejemplo para descargar. No es obligatoria: se aceptan los encabezados con otros nombres. */
@RestController
public class PlantillaController {

    private static final String[] TITULOS = {"Código", "Descripción", "Marca", "Vehículo", "Cantidad", "Precio USD", "Reemplaza a", "Equivalentes"};
    private static final Object[][] EJEMPLOS = {
            {"04465-02220", "Pastillas de freno delanteras", "Toyota", "Corolla 2009-2013", 4, 35.50, "", ""},
            {"FB3Z-9A543-J", "Bomba de inyección", "Ford", "", 1, 1850.00,
                    "BK3Z-9A543-A / FB3Z-9A543-A / FB3Z-9A543-F", "ALT-EJEMPLO-1"},
    };

    @GetMapping("/plantilla.xlsx")
    public ResponseEntity<byte[]> plantilla() throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("stock");
            CellStyle negrita = libro.createCellStyle();
            Font font = libro.createFont();
            font.setBold(true);
            negrita.setFont(font);

            Row titulos = hoja.createRow(0);
            for (int i = 0; i < TITULOS.length; i++) {
                Cell c = titulos.createCell(i);
                c.setCellValue(TITULOS[i]);
                c.setCellStyle(negrita);
            }
            for (int r = 0; r < EJEMPLOS.length; r++) {
                Row fila = hoja.createRow(r + 1);
                for (int i = 0; i < EJEMPLOS[r].length; i++) {
                    Object v = EJEMPLOS[r][i];
                    if (v instanceof Number n) fila.createCell(i).setCellValue(n.doubleValue());
                    else fila.createCell(i).setCellValue(v.toString());
                }
            }
            int[] anchos = {16, 36, 12, 22, 10, 12, 44, 16};
            for (int i = 0; i < anchos.length; i++) {
                hoja.setColumnWidth(i, anchos[i] * 256);
            }
            libro.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-stock.xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}

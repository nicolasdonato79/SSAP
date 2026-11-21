package com.stockautopartes.importacion;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Lee la primera hoja de un Excel y le pasa las filas a {@link ProcesadorFilas}.
 * - .xlsx: en streaming (SAX), sin cargar el archivo entero en memoria.
 * - .xls (Excel viejo): con el modelo de POI; ese formato tiene como máximo 65.536 filas.
 */
@Component
public class ExcelStockReader {

    public ResultadoLectura leer(Path archivo) throws Exception {
        ProcesadorFilas procesador = new ProcesadorFilas();
        if (archivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xls")) {
            leerXls(archivo, procesador);
        } else {
            leerXlsx(archivo, procesador);
        }
        return procesador.resultado();
    }

    private void leerXlsx(Path archivo, ProcesadorFilas procesador) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(archivo.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable estilos = reader.getStylesTable();
            Iterator<InputStream> hojas = reader.getSheetsData();
            if (!hojas.hasNext()) {
                return;
            }
            try (InputStream hoja = hojas.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                // Locale.US: los números de celdas numéricas salen con punto decimal
                parser.setContentHandler(new XSSFSheetXMLHandler(
                        estilos, strings, new Recolector(procesador), new DataFormatter(Locale.US), false));
                parser.parse(new InputSource(hoja));
            }
        }
    }

    private void leerXls(Path archivo, ProcesadorFilas procesador) throws Exception {
        try (Workbook libro = WorkbookFactory.create(archivo.toFile(), null, true)) {
            if (libro.getNumberOfSheets() == 0) {
                return;
            }
            DataFormatter formato = new DataFormatter(Locale.US);
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            for (Row fila : libro.getSheetAt(0)) {
                Map<Integer, String> celdas = new HashMap<>();
                for (Cell celda : fila) {
                    celdas.put(celda.getColumnIndex(), formato.formatCellValue(celda, evaluador));
                }
                procesador.fila(fila.getRowNum(), celdas);
            }
        }
    }

    /** Arma un mapa columna → valor por cada fila del .xlsx. */
    private static class Recolector implements SheetContentsHandler {

        private final ProcesadorFilas procesador;
        private Map<Integer, String> celdas;

        Recolector(ProcesadorFilas procesador) {
            this.procesador = procesador;
        }

        @Override
        public void startRow(int rowNum) {
            celdas = new HashMap<>();
        }

        @Override
        public void cell(String ref, String valor, XSSFComment comentario) {
            celdas.put((int) new CellReference(ref).getCol(), valor);
        }

        @Override
        public void endRow(int rowNum) {
            procesador.fila(rowNum, celdas);
        }

        // En POI 5.x tiene implementación por defecto; se deja vacío por compatibilidad.
        public void headerFooter(String text, boolean isHeader, String tagName) {
        }
    }
}

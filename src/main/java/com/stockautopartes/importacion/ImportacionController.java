package com.stockautopartes.importacion;

import com.stockautopartes.usuario.UsuarioActual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class ImportacionController {

    private final ImportacionRepository importaciones;
    private final ImportacionProcessor processor;
    private final UsuarioActual usuarioActual;
    private final Path carpetaTemporal;

    public ImportacionController(ImportacionRepository importaciones,
                                 ImportacionProcessor processor,
                                 UsuarioActual usuarioActual,
                                 @Value("${app.importacion.carpeta-temporal}") String carpetaTemporal) {
        this.importaciones = importaciones;
        this.processor = processor;
        this.usuarioActual = usuarioActual;
        this.carpetaTemporal = Path.of(carpetaTemporal);
    }

    @GetMapping("/importar")
    public String formulario() {
        return "redirect:/mi-stock";
    }

    @PostMapping("/importar")
    public String subir(@RequestParam("archivo") MultipartFile archivo, Model model) throws IOException {
        String nombre = archivo.getOriginalFilename() == null ? "stock.xlsx" : archivo.getOriginalFilename();
        String minuscula = nombre.toLowerCase();
        String extension = minuscula.endsWith(".xlsx") ? ".xlsx" : minuscula.endsWith(".xls") ? ".xls" : null;
        if (archivo.isEmpty() || extension == null) {
            model.addAttribute("error", "Subí un archivo de Excel (.xlsx o .xls).");
            return "fragmentos/importacion :: error";
        }
        if (nombre.length() > 255) {
            nombre = nombre.substring(nombre.length() - 255);
        }
        Files.createDirectories(carpetaTemporal);
        Path destino = Files.createTempFile(carpetaTemporal, "stock-", extension);
        archivo.transferTo(destino);

        Importacion imp = importaciones.save(new Importacion(usuarioActual.obtener().getId(), nombre));
        processor.procesar(imp.getId(), destino);

        model.addAttribute("imp", imp);
        return "fragmentos/importacion :: estado";
    }

    /** HTMX consulta este endpoint cada 2 segundos hasta que la importación termina. */
    @GetMapping("/importaciones/{id}")
    public String estado(@PathVariable long id, Model model) {
        Importacion imp = importaciones.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        model.addAttribute("imp", imp);
        return "fragmentos/importacion :: estado";
    }
}

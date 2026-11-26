package com.stockautopartes.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** La app sin conexión (PWA, issue #9) vive en /app/ como archivos estáticos (static/app). */
@Controller
public class AppController {

    /** Con barra al final, para que las rutas relativas de la app funcionen. */
    @GetMapping("/app")
    public String app() {
        return "redirect:/app/";
    }

    @GetMapping("/app/")
    public String inicio() {
        return "forward:/app/index.html";
    }
}

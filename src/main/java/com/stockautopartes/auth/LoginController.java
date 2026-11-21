package com.stockautopartes.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantalla de login con usuario y contraseña. El POST a /login lo procesa Spring Security
 * (ver SecurityConfig). El login por código de WhatsApp (OtpService) queda listo para más adelante.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}

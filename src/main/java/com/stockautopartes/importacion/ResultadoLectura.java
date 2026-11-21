package com.stockautopartes.importacion;

import java.util.List;

public record ResultadoLectura(List<FilaStock> filasOk, List<String> errores) {
}

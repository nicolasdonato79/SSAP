package com.stockautopartes.matching;

import com.stockautopartes.demanda.Demanda;
import com.stockautopartes.demanda.DemandaRepository;
import com.stockautopartes.demanda.EstadoDemanda;
import com.stockautopartes.inventario.EquivalenciaService;
import com.stockautopartes.notificacion.Notificador;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Cruza el stock recién cargado de un vendedor con las demandas abiertas.
 * Tiene en cuenta los reemplazos: si alguien pidió un código viejo y el vendedor cargó el nuevo, se avisa.
 * TODO: sumar coincidencia por descripción (pg_trgm) y no avisar dos veces la misma demanda al mismo vendedor.
 */
@Service
public class MatchingService {

    private final DemandaRepository demandas;
    private final UsuarioRepository usuarios;
    private final EquivalenciaService equivalencias;
    private final Notificador notificador;

    public MatchingService(DemandaRepository demandas, UsuarioRepository usuarios,
                           EquivalenciaService equivalencias, Notificador notificador) {
        this.demandas = demandas;
        this.usuarios = usuarios;
        this.equivalencias = equivalencias;
        this.notificador = notificador;
    }

    @Transactional(readOnly = true)
    public void buscarDemandasPara(long vendedorId, Set<String> codigosCargados) {
        if (codigosCargados.isEmpty()) {
            return;
        }
        Set<String> cubiertos = equivalencias.codigosServidosPor(codigosCargados);
        Usuario vendedor = usuarios.findById(vendedorId).orElseThrow();
        for (Demanda d : demandas.findByEstadoAndCodigoNormalizadoIn(EstadoDemanda.ABIERTA, cubiertos)) {
            if (!d.getComprador().getId().equals(vendedorId)) {
                notificador.avisarCoincidencia(d, vendedor);
            }
        }
    }
}

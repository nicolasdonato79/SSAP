package com.stockautopartes.inventario;

import java.math.BigDecimal;

/** Formulario de alta/modificación manual de un repuesto. Cantidad y precio como texto ("consultar", "1.250,50"). */
public class ItemStockForm {

    private String codigo = "";
    private String descripcion = "";
    private String vehiculo = "";
    private String cantidad = "";
    private String precio = "";
    /** "" = automática (según el modelo); si no, el id de la marca elegida. */
    private String marca = "";

    public static ItemStockForm de(ItemStock i) {
        ItemStockForm f = new ItemStockForm();
        f.codigo = i.getCodigoOriginal();
        f.descripcion = i.getDescripcion() == null ? "" : i.getDescripcion();
        f.vehiculo = i.getVehiculo() == null ? "" : i.getVehiculo();
        f.cantidad = i.getCantidad() == null ? "consultar" : i.getCantidad().toString();
        f.precio = i.getPrecioUsd() == null ? "" : precioEnTexto(i.getPrecioUsd());
        f.marca = i.getMarca() == null ? "" : i.getMarca().getId().toString();
        return f;
    }

    /** 1250.5 → "1250,50" (con coma decimal, que NumeroParser lee sin ambigüedad). */
    private static String precioEnTexto(BigDecimal p) {
        return p.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo == null ? "" : codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion == null ? "" : descripcion; }
    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo == null ? "" : vehiculo; }
    public String getCantidad() { return cantidad; }
    public void setCantidad(String cantidad) { this.cantidad = cantidad == null ? "" : cantidad; }
    public String getPrecio() { return precio; }
    public void setPrecio(String precio) { this.precio = precio == null ? "" : precio; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca == null ? "" : marca; }
}

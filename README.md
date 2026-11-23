# SistemaStockAutoPartes

Marketplace de stock de autopartes entre vendedores de Venezuela.
Cada vendedor sube un Excel con su stock, los compradores buscan, y si no encuentran dejan un pedido
que se les avisa cuando alguien carga el repuesto.

## Stack

- Java 21 · Spring Boot 4 (monolito modular)
- PostgreSQL 17 con `pg_trgm` (búsqueda tolerante a errores) · Flyway
- Thymeleaf + HTMX (PWA mobile-first, liviana para conexiones malas)
- Apache POI en modo streaming para leer los Excel

## Levantarlo

Hay un solo ambiente: base + app + túnel de Cloudflare, en `docker-compose.yml`.
Las claves van en `.env` (no se sube a git): `DB_PASSWORD`, `TUNNEL_TOKEN` y, cuando se configuren, las de Twilio y SMTP.

```bash
docker compose up -d --build   # base (localhost:5434), app (localhost:8081) y https://stock.gochosoftware.com
docker compose logs -f app     # logs
```

Para programar con recarga rápida: `mvn spring-boot:run` (http://localhost:8080) usa **la misma base**
(toma `DB_PASSWORD` del `.env`). Para ver la base: DBeaver a `localhost:5434`, usuario `stock`.

Para probar: creá una cuenta en `/registro` (en modo prueba el código y el link salen en pantalla),
o entrá con `admin`, `jean` o `carlos`, completá **⚙ Configuración**,
subí `plantilla-stock.xlsx` en **Mi stock** y buscá en `/`.

## Login y alta de usuarios

- **Usuario (o email) y contraseña**, con BCrypt y sesión de 30 días.
- **Alta automática** en `/registro` (issue #1), en 3 pasos:
  1. Datos: nombre o negocio, usuario, teléfono, email y contraseña (mínimo 8 caracteres).
  2. Teléfono: código de 6 números por **SMS** (vence en 5 minutos, 5 intentos, máximo 3 códigos cada 10 minutos).
  3. Email: **link de activación** (vence en 24 horas). Recién ahí la cuenta queda activa.
  Mientras no termina, la cuenta queda "pendiente" y no puede entrar. Si se equivocó o no le llegó nada,
  vuelve a registrarse con los mismos datos y el registro pendiente se pisa. Los pendientes se borran a las 48 horas.
  En el primer ingreso aparece el aviso para completar estado, ciudad, marcas y horarios.
- **Recuperar contraseña** en `/recuperar`: con el usuario o el email llega un link (vence en 1 hora, un solo uso).
  La pantalla dice siempre lo mismo, exista o no la cuenta.
- Teléfono y email verificados no se pueden cambiar desde ⚙ (el admin sí; si los cambia, dejan de estar verificados).
- El administrador sigue pudiendo crear usuarios en `/admin/usuarios`. Usuarios iniciales: `admin`, `jean`, `carlos`.
- Los links del email son de un solo uso; en la base se guarda solo su hash (tabla `token_usuario`).

### Modo prueba y proveedores

Sin configurar nada, el SMS y el email **no salen**: el código y el link se escriben en la consola y, con
`OTP_EN_PANTALLA=true` (por defecto en desarrollo), también aparecen en pantalla para poder probar.

| Variable | Qué es |
|---|---|
| `REGISTRO_HABILITADO` | `true` abre `/registro`. En `docker-compose.yml` está en `false` hasta configurar SMS y email |
| `APP_URL` | Dirección pública para los links (en el servidor: `https://stock.gochosoftware.com`) |
| `VERIFICAR_TELEFONO` | `false` (por defecto): el alta es datos + link por email. `true`: además pide el código por SMS |
| `OTP_CANAL` | `prueba` (por defecto), `sms` (Twilio) o `whatsapp` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | Credenciales de Twilio |
| `TWILIO_FROM` | Número Twilio (`+1...`) o SID de un Messaging Service (`MG...`) |
| `EMAIL_HABILITADO` | `true` para mandar los emails por SMTP |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Servidor SMTP (ej. Gmail: `smtp.gmail.com`, 587, la cuenta y una contraseña de aplicación) |
| `MAIL_FROM` | Remitente, ej. `Stock Multimarca <noresponder@gochosoftware.com>` |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_PLANTILLA`, `WHATSAPP_IDIOMA` | Solo si `OTP_CANAL=whatsapp` |

## Ciudades

- Catálogo `ciudad` (migración `V7`): 532 ciudades y pueblos de los 24 estados. Base: capitales de los 335
  municipios y ciudades principales de [zokeber/venezuela-json](https://github.com/zokeber/venezuela-json),
  depuradas (duplicados, tildes, "Vargas" pasa a La Guaira).
- En ⚙ Configuración la ciudad se elige de un desplegable que se carga según el estado (`GET /ciudades`).
  Si un pueblo no está, se elige "Otra (escribirla)" y se guarda el texto.
- La búsqueda se puede filtrar por estado y por ciudad del vendedor.

## Marcas

- Catálogo en la tabla `marca` (migración `V6`): ~145 marcas en tres grupos — autos y camionetas, camiones y buses, motos.
  El administrador agrega las que falten en `/admin/usuarios#marcas`.
- Cada usuario elige una o varias (`usuario_marca`). El admin las asigna al crear el usuario y cada uno las cambia en ⚙.
- En la búsqueda se puede filtrar "Vendedores de: Ford", y cada resultado muestra qué marcas trabaja el vendedor
  ("Ford · Toyota", o "Multimarca (N marcas)" si son más de 3).
- Horarios: un rango por día (`usuario_horario`); se muestran resumidos ("Lun–Vie 8:00–17:00 · Sáb 8:00–12:00")
  al comprador cuando le aceptan un pedido.
- Público: `/`, `/buscar`, `/plantilla.xlsx`. Todo lo demás requiere login; `/admin/**` solo administradores.

## Paquetes

| Paquete         | Qué hace |
|-----------------|----------|
| `auth`          | Pantalla de login, normalización de teléfonos y el login por código (OTP) para más adelante. |
| `registro`      | Alta automática (SMS + link por email) y recuperar contraseña. |
| `email`         | Envío de emails: SMTP o modo prueba. |
| `admin`         | Administración de usuarios y del catálogo de marcas. |
| `marca`         | Catálogo de marcas de vehículos. |
| `usuario`       | Usuarios (vendedor y/o comprador) y registro. `UsuarioActual` es el único lugar que sabe quién está logueado. |
| `importacion`   | Subida del Excel, lectura SAX, validación, reemplazo del stock en segundo plano. |
| `inventario`    | Ítems de stock, normalización de códigos y búsqueda. |
| `solicitud`     | Pedidos de compradores a vendedores: pedir, aceptar, rechazar, cancelar. |
| `demanda`       | Pedidos abiertos de compradores. |
| `matching`      | Cruza stock recién cargado con demandas abiertas. |
| `notificacion`  | Avisos. Hoy solo log; después WhatsApp Cloud API. |

## Carga de stock por Excel

- Acepta `.xlsx` y `.xls`. Lee la primera hoja.
- **Obligatorias:** código y cantidad. En la cantidad también vale la palabra **"consultar"** (y variantes:
  "Consultar", "a consultar", "consulte"): se publica como "Disponibilidad a consultar".
- **Opcionales:** precio en USD (vacío, 0 o "consultar" = "Precio a consultar"), descripción, marca, vehículo.
- Una cantidad vacía sigue siendo error (probablemente un olvido); la cantidad 0 se carga pero no aparece en las búsquedas.
- Las columnas se reconocen por el título, en cualquier orden y con los nombres que ya usan los vendedores
  (ver `Campo.java`): "Ref", "Nº Parte", "Cant.", "Existencia", "Precio $", "Valor", "Aplicación"…
- La fila de títulos puede no ser la primera (se busca en las primeras 10 filas con datos).
- Números en formato local: `1.234,56`, `35,50`, `$ 40`. Un solo separador seguido de 3 dígitos (`1.500`) se toma como miles.
- Las filas con errores se informan una por una; las buenas se cargan igual.
- Cada carga **reemplaza todo** el stock anterior del vendedor.
- `plantilla-stock.xlsx` es un ejemplo con títulos "no estándar" y una fila de título arriba.

## Reemplazos y equivalencias de códigos

Los números de pieza cambian con el tiempo (ej. Ford: `BK3Z-9A543-A` → `FB3Z-9A543-A` → `FB3Z-9A543-F` → `FB3Z-9A543-J`).
Tabla `relacion_codigo`, dos tipos:

- **REEMPLAZO** (con dirección): el nuevo reemplaza al anterior. Quien busca el anterior ve el stock del nuevo;
  quien busca el nuevo **no** ve el del anterior.
- **EQUIVALENTE** (los dos sentidos): códigos intercambiables, ej. el de la marca original y el de otro fabricante.

Se cargan desde el mismo Excel de stock, con columnas opcionales:
- **"Reemplaza a"** (también "Sustituye", "Código anterior"): códigos viejos que este reemplaza.
- **"Alterno"** (también "Equivalente", "Código alterno"): códigos intercambiables.
- Varios códigos en la misma celda separados por `/`, `,`, `;` o salto de línea.

La búsqueda y los avisos de pedidos recorren la cadena completa (`EquivalenciaService`, CTE recursivo, máx. 20 niveles).
Las relaciones no se borran al recargar el stock. Por ahora cualquier vendedor puede cargarlas:
más adelante conviene moderarlas o darles más peso según quién las cargó.

**Componentes** (ej. el piñón `FB3Z-9F588-A` que va con la bomba) no son equivalentes y no se mezclan en la búsqueda.
Quedan para más adelante como "piezas relacionadas".

## Pedidos y negociación

1. El comprador busca, elige la cantidad y toca **Pedir** (el precio publicado puede ser "a consultar").
2. El vendedor responde: **Tengo stock** con el precio unitario (viene cargado el publicado, si había) o **No lo tengo**.
3. El comprador **acepta** ese precio o hace una **contraoferta** (o cancela).
4. Si hubo contraoferta, el vendedor la **acepta** o la **rechaza**.
5. Con el trato cerrado, a los dos les aparece el botón de WhatsApp del otro.

Estados (`EstadoSolicitud`): PENDIENTE → OFERTADA | RECHAZADA → ACEPTADA | CONTRAOFERTA | CANCELADA;
CONTRAOFERTA → ACEPTADA | RECHAZADA. El contador de "Pedidos" en el menú suma los pedidos donde le toca
responder al usuario. El pedido guarda el precio publicado, la oferta y la contraoferta.

## Próximos pasos

1. Configurar el SMTP (`EMAIL_HABILITADO=true` + `MAIL_*`) y abrir el registro en el servidor (`REGISTRO_HABILITADO=true`).
   Segunda etapa: Twilio para el SMS (`OTP_CANAL=sms`, `VERIFICAR_TELEFONO=true`).
2. `WhatsAppNotificador` con WhatsApp Cloud API (avisos de pedidos y coincidencias).
3. Moneda: hoy todo es USD; definir si hace falta bolívares.
4. Matching también por descripción, y no repetir avisos.
5. Descarga del Excel de errores de la importación.
6. Docker de la app + Nginx + backups para el VPS.
# SSAP

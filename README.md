# Simulador Ladder LOGO!

Simulador local para Windows que carga archivos `.lld` de **LOGO! 8 y 9** y permite probar sus señales o una planta virtual de mezcla RT800. Interfaz en español con animación de agua, válvulas, bomba y agitador.

## Descargar y ejecutar

1. Descarga `Simulador-Ladder-LOGO-Windows.zip` desde [Releases](https://github.com/VIXCA22/simulador-ladder-logo/releases/latest) y extrae todo su contenido.
2. Esta edición de GitHub utiliza una instalación local autorizada de **Siemens LOGO! Soft Comfort V9**, incluidos su Java y sus bibliotecas. El motor de Siemens no se distribuye en este repositorio.
3. Abre `Abrir_simulador.cmd`. Se detecta la instalación habitual `C:\Program Files\Siemens\LOGOComfort_V9.0`. Para otra ubicación, configura `LOGO_INSTALL` o inicia PowerShell con:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Iniciar.ps1 -MotorPath 'D:\Siemens\LOGOComfort_V9.0'
```

4. En el navegador, carga tu `.lld`. El programa original se conserva; se trabaja con una copia local.
5. Abre `Cerrar_simulador.cmd` para apagar el motor. Cerrar solo la pestaña no lo detiene.

**Importante:** abrir archivos LOGO! 8 y 9 no implica ejecutar esta aplicación con las bibliotecas antiguas de V8. Esta edición usa el motor V9 para ambos formatos. No necesita Python, Node.js ni conexión a internet para funcionar.

## Funciones

- Carga por selector o arrastre; máximo 20 MB por archivo.
- Detección del formato histórico o cifrado según el controlador de destino, incluso V9 guardado para LOGO! 8.
- Banco manual de entradas digitales y analógicas; visualización de salidas y bloques nativos.
- Planta virtual RT800 con mapa editable de entradas/salidas, niveles, caudales y fallos.
- Ejecutar, pausar, avanzar un ciclo y ajustar velocidad.
- Reiniciar desde cero conservando archivo y mapa, o quitar el archivo para cargar otro.
- Agitador animado según su salida asignada y el estado del reloj.
- Exportación del ensayo a CSV y del mapa de la planta a JSON.

## Prueba rápida

Carga `Ejemplos/Banco_digital_analogico.lld` y usa **modo manual**:

| Acción | Resultado esperado |
|---|---|
| Activar I1 | Q1 se activa |
| Mantener I1 durante 1 s de reloj virtual | Q2 se activa por TON |
| Dar tres flancos de subida en I2 | Q3 se activa |
| Activar I3 | Reinicia el contador |
| Introducir 620 en AI1 | AQ1 muestra 620 |

`Banco_LOGO9.lld` permite repetir el ensayo con destino LOGO! 9. Para temporizadores y pulsos, deja correr el reloj o usa el avance por ciclos.

En modo GUNT, configura primero qué Q controla cada actuador y qué I recibe cada sensor. El mapa debe corresponder a tu programa; el simulador no convierte automáticamente cualquier Ladder en una receta de mezcla.

## Límites

El paso de simulación es de 0,1 s. Se rechazan archivos protegidos y bloques detectados de red/AS-i, mensajes o calendarios. Pulsos más rápidos, funciones especiales y retención entre reinicios requieren la simulación oficial o el equipo correspondiente. La planta es un modelo virtual y no valida físicamente una instalación.

La lógica usa las clases nativas de Siemens; las entradas analógicas se inyectan en sus conectores y el reloj es virtual. No se garantiza compatibilidad universal con todos los archivos `.lld` ni equivalencia con cada detalle de un PLC físico.

## Código y compilación

El código Java está en `Fuentes`; la interfaz está en `interfaz.html`. `app/simulador.jar` contiene únicamente la aplicación propia, sin bibliotecas Siemens. Para recompilar, usa `Compilar.ps1` con la instalación V9:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Compilar.ps1
```

Los informes y el código de verificación de formatos están en `Verificacion`. Cubren ocho combinaciones de cabecera/controlador con directo, TON y AI/AQ. Los circuitos se escribieron con las clases Siemens V9; esto no prueba todas las variantes de editores antiguos.

## Contenido

Este proyecto incluye solo el simulador, sus fuentes, ejemplos propios y verificación. Excluye el proyecto académico, los documentos, los programas de otros integrantes, los datos de sesiones y los componentes propietarios de Siemens. Los datos locales se crean en `datos/`, que queda fuera del control de versiones.

# Simulador Ladder LOGO!

**Versión 1.1.0 · Windows de 64 bits · Interfaz en español**

Aplicación local para prácticas de automatización: carga programas `.lld` de LOGO! 8 y 9, comprueba señales y simula una planta de mezcla con agua. La lógica y los temporizadores pertenecen al programa cargado; los nombres, sensores y parámetros de la práctica son configurables.

## Descargar y abrir

1. Descarga **Simulador-Ladder-LOGO-Windows.zip** desde [la última versión](https://github.com/VIXCA22/simulador-ladder-logo/releases/latest).
2. Extrae la carpeta completa y abre **Abrir_simulador.cmd**.
3. Carga tu `.lld`, elige banco manual o planta RT800 y revisa el mapa de señales.

**La edición de GitHub requiere una instalación local autorizada de Siemens LOGO! Soft Comfort V9.** El motor de Siemens no se incluye en el repositorio ni en el ZIP. Se utiliza el Java de esa instalación; no hacen falta Python, Node.js ni internet para ejecutar la aplicación.

Se detecta la ubicación habitual de LOGO! V9. Para otra ruta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Iniciar.ps1 -MotorPath 'D:\Siemens\LOGOComfort_V9.0'
```

También puede utilizarse la variable `LOGO_INSTALL`. **Cerrar_simulador.cmd** detiene el motor; cerrar la pestaña solo cierra la vista.

## Configurar la práctica

- **Cambiar nombres:** identifica los tanques, el mezclador y la reserva.
- **Ajustar sensores:** modifica las nueve alturas con el dibujo, deslizadores o porcentajes.
- **Modelo de tiempo:** configura caudales, geometría, temperatura y pérdidas. La duración medida es opcional.
- **Aplicar cambios y reiniciar:** guarda los ajustes por programa e inicia otro ensayo.

El modelo de agua incluye gravedad, viscosidad, pérdidas de Darcy–Weisbach y una curva de bomba configurable. Los parámetros iniciales son valores de partida para adaptar la práctica. Consulta [el modelo hidráulico](Modelo_tiempo_RT800.html).

## Ejecutar y revisar resultados

- Banco manual con entradas digitales y analógicas, salidas y bloques nativos.
- Niveles instantáneos en litros y porcentaje, caudales y tiempos por depósito.
- Ejecución, pausa, avance de 0,1 s y velocidad de reproducción.
- STOP automático mediante la señal FIN configurada.
- Historial permanente con parámetros, niveles, tiempos y movimientos de cada ensayo.
- Descargas de resumen, movimientos y traza en CSV; importación y exportación de mapas JSON.

Los datos se crean en `datos/`, excluido del repositorio. [Guía completa](GUIA.md) · [Cambios de la versión](CAMBIOS.md).

## Ejemplos incluidos

`Ejemplos/Banco_digital_analogico.lld` y `Ejemplos/Banco_LOGO9.lld` son circuitos propios para practicar en modo manual:

| Acción | Resultado |
|---|---|
| Activar I1 | Se activa Q1 |
| Mantener I1 un segundo virtual | Se activa Q2 mediante TON |
| Aplicar tres flancos en I2 | Se activa Q3 |
| Activar I3 | Se reinicia el contador |
| Introducir un valor en AI1 | AQ1 refleja el valor |

## Compilar

Las fuentes propias están en `Fuentes` y el JAR en `app/simulador.jar`. Con LOGO! V9 disponible:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Compilar.ps1 -MotorPath 'D:\Siemens\LOGOComfort_V9.0'
```

El JAR contiene únicamente las clases de esta aplicación. Las bibliotecas Siemens se cargan desde la instalación indicada. Las comprobaciones y resultados están en `Verificacion`.

## Alcance

La planta representa tres silos, un mezclador y una reserva. El banco manual permite comprobar otros programas compatibles. El paso del Ladder es de 0,1 s; el modelo de agua actualiza volúmenes en subpasos de hasta 20 ms. La precisión física depende de los parámetros de cada práctica.

Los archivos protegidos y las funciones detectadas de comunicación, calendarios y pantallas se rechazan o informan al cargar. La compatibilidad comprobada no equivale a soporte universal de todos los archivos `.lld` ni a una certificación física del proceso.

El repositorio incluye aplicación, fuentes, ejemplos propios y verificación. No contiene datos personales de sesiones ni componentes propietarios del motor.

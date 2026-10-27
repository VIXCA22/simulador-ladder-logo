# Simulador de prácticas de automatización

Aplicación portable para Windows que permite cargar programas Ladder de LOGO!, comprobar sus señales y simular una planta de mezcla con agua. Cada práctica utiliza su propio programa, mapa de entradas y salidas, nombres, sensores y parámetros hidráulicos.

## Inicio

1. Conserva juntas las carpetas `app`, `Ejemplos` y los archivos de la aplicación.
2. Abre `Abrir_simulador.cmd`. Esta edición requiere una instalación local autorizada de LOGO! Soft Comfort V9; su motor no se incluye en la descarga de GitHub. La aplicación utiliza el Java de esa instalación y no necesita Python ni Node.js. Para otra ubicación usa LOGO_INSTALL o el parámetro -MotorPath de Iniciar.ps1.
3. Selecciona un archivo `.lld` sin contraseña y pulsa **Cargar .lld**. El archivo original se conserva.
4. Elige **Banco manual de señales** para probar entradas, salidas y temporizadores, o **Planta GUNT RT800** para simular el proceso de mezcla.
5. Revisa el mapa de señales y los parámetros antes de ejecutar la práctica.

La dirección local se guarda en `datos/puerto.txt`. Cierra el motor con `Cerrar_simulador.cmd`. La aplicación funciona sin conexión a internet y utiliza el navegador como interfaz.

## Configurar una práctica

- **Cambiar nombres:** permite identificar los tres tanques, el mezclador y la reserva.
- **Ajustar sensores:** permite modificar las nueve alturas con los puntos del dibujo, deslizadores o porcentajes. Cada silo tiene sensores alto/bajo y el mezclador alto/medio/bajo.
- **Mapa GUNT y parámetros del agua:** asigna sensores a entradas I, actuadores a salidas Q, START y FIN. Las señales ausentes quedan sin asignar.
- **Modelo de tiempo:** configura los caudales por ruta y las propiedades del modelo de agua.
- **Aplicar cambios y reiniciar:** guarda los ajustes e inicia un ensayo nuevo, en pausa.

El porcentaje del sensor se refiere a la altura útil de un depósito de sección uniforme; el editor también muestra su volumen equivalente. Debe respetarse el orden de las alturas. Las asignaciones del Ladder se revisan para cada práctica: el nombre de una señal no determina su conexión física.

Los ajustes se guardan por contenido del archivo `.lld` en `datos/mapas`. **Exportar mapa** e **Importar mapa** permiten reutilizarlos con el mismo programa. Los cambios pendientes pueden descartarse antes de aplicarlos.

## Parámetros iniciales

Los valores iniciales son un punto de partida configurable para la planta representada. Deben ajustarse a las condiciones de cada práctica.

| Depósito o ruta | Valor inicial |
|---|---:|
| Tanques T1, T2 y T3 | 1,5 L cada uno |
| Mezclador | 7 L |
| Reserva | 70 L de capacidad; 12 L al iniciar |
| Llenado T1 / T2 / T3 | 0,154 / 0,154296 / 0,164683 L/s |
| Dosificación T1 / T2 / T3 | 0,054406 / 0,067880 / 0,053462 L/s |
| Retorno T1 / T2 / T3 | 0,060839 / 0,060 / 0,060 L/s |
| Descarga del mezclador | 0,246053 L/s |
| Caudal compartido, modo constante | 0,17 L/s |
| Temperatura del agua | 20 °C |

**Restablecer caudales iniciales** recupera esos valores conservando nombres, sensores y volúmenes iniciales. Si está activo el modelo de agua, recalcula los coeficientes de pérdidas con la geometría configurada. Las configuraciones anteriores que utilizan caudales comunes siguen siendo compatibles.

## Calcular y ejecutar

**Calcular duración** ejecuta el programa desde el estado inicial configurado en un ensayo aislado. Muestra duración hasta FIN, uso de bomba y agitador, tiempos de llenado y vaciado por depósito y el inicio y fin de cada movimiento. Respeta los temporizadores del archivo y las operaciones simultáneas. El cálculo no modifica el ensayo abierto.

**Duración medida (opcional)** permite comparar el resultado con una práctica real. Puede dejarse vacía. No ajusta automáticamente los caudales ni modifica los temporizadores.

En modo constante se utilizan los caudales configurados. En **Agua: gravedad, viscosidad y pérdidas**, el caudal depende de los niveles, la temperatura, los diámetros, las longitudes, las cotas, la rugosidad, las válvulas y la curva de bomba. **Preparar desde caudales por ruta** calcula coeficientes K a media capacidad del depósito. Las dimensiones iniciales de este modelo son editables y requieren validación para la instalación simulada.

Consulta [Modelo hidráulico y cálculo de tiempos](Modelo_tiempo_RT800.html) para ver fórmulas, unidades, parámetros y fuentes técnicas.

Utiliza **START** si el programa requiere un pulsador asignado, o **Ejecutar** si inicia por energización. **Pausar** congela el reloj; **Avanzar 0,1 s** calcula un ciclo. La velocidad de reproducción no cambia los tiempos virtuales del proceso.

## Fin del ensayo e historial

La señal FIN puede detectarse automáticamente cuando es inequívoca, o seleccionarse en el mapa. Al activarse, **STOP automático** conserva los niveles y tiempos finales y guarda el ensayo. **STOP** también permite detenerlo manualmente. Para repetir se utiliza **Reiniciar desde cero**.

El historial conserva los nombres, parámetros, volúmenes, movimientos y duración de cada ensayo. Permite descargar **Resumen CSV**, **Movimientos CSV** y **Traza completa CSV**. Reiniciar, cambiar de modo o sustituir el archivo conserva el ensayo anterior como parcial cuando corresponde.

Los datos están en `datos/historial`. La traza conserva las últimas 20.000 muestras; los totales y movimientos permanecen en el registro. Copia `datos/historial` y `datos/mapas` para respaldar resultados y configuraciones.

## Alcance y mantenimiento

La planta representada tiene tres silos, un mezclador y una reserva. El banco manual permite comprobar programas de otros procesos sin asociarlos a esta animación. Se admiten archivos compatibles de LOGO! 8 y 9; las funciones no soportadas se informan al cargar. Comunicación de red, calendarios y pantallas requieren las herramientas correspondientes.

El Ladder se ejecuta con paso virtual de 0,1 s. El modelo hidráulico usa subpasos para actualizar los volúmenes. La precisión física depende de los parámetros de la práctica; las dimensiones y curvas iniciales no sustituyen las mediciones del equipo.

Si el agua no cambia, revisa modo, reloj, mapa de salidas y rutas. Si la práctica no inicia, revisa START y las condiciones del programa. Si no termina, revisa FIN y los sensores. Los detalles del motor están en `datos/motor.log` y `datos/motor_errores.log`. Las fuentes y comprobaciones técnicas están en `Fuentes` y `Verificacion`.

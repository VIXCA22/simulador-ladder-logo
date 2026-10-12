# Guía de uso

Los requisitos y el inicio están en `README.md`.

## Repetir la simulación o cambiar de archivo

Debajo del selector de archivos están los dos controles:

- **Reiniciar desde cero**: conserva el archivo y el mapa, reinicia memorias, reloj y agua al estado inicial configurado, retira fallos y queda en pausa. Para repetir, pulsa START asignado o Ejecutar según el programa.
- **Quitar archivo y cargar otro**: detiene el reloj, libera el programa, borra la traza y las señales del ensayo, y elimina únicamente su copia importada de `datos/cargas`. Conserva tu archivo original. Selecciona el nuevo `.lld` y pulsa Cargar .lld.

Descarga el CSV antes de reiniciar o quitar si quieres conservar la traza.

## Banco manual: probar un programa distinto

Después de cargar, el modo inicial es **Banco manual de señales**. Las entradas I, AI y C se descubren en el archivo; las salidas Q, AQ y marcas se muestran con sus nombres y valores. El inventario contiene los tipos y parámetros de sus bloques.

1. Comprueba el nombre y hardware identificado. Las entradas manuales comienzan en cero; se descartan los estados de prueba que estuvieran guardados en el archivo.
2. Activa entradas digitales con **OFF / ON**. Estando en pausa, cada cambio calcula un ciclo virtual de 0,1 segundos para actualizar la lógica.
3. Usa **Pulso** para mantener la entrada durante tres ciclos. En pausa, avanza el reloj para que el pulso se complete; al ejecutar, se completa automáticamente. C1 representa el cursor superior cuando ese bloque existe en el programa.
4. Para una entrada AI, escribe un valor y pulsa **Aplicar AI…**. El valor es la magnitud interna numérica, no una tensión en voltios. La fuente AI se inyecta en su conector de simulación; los cálculos posteriores utilizan los bloques del archivo.
5. Pulsa **Ejecutar** para avanzar continuamente. Cambia la velocidad de 1× a 50×. **Pausar** congela el reloj virtual y **Avanzar 0,1 s** calcula un único ciclo.
6. Observa las salidas, marcas y el inventario. **Reiniciar desde cero** vuelve a cargar las memorias, pone las entradas manuales a cero y aplica M8 una vez. No es el RESET diseñado dentro del Ladder; ese RESET se prueba accionando su entrada real.
7. Descarga el CSV antes de reiniciar o cargar otro programa. Contiene tiempos, entradas, salidas y, en modo GUNT, volúmenes. Conserva las últimas 20.000 muestras; el registro informa si se descartaron muestras antiguas.

La ejecución no depende del nombre del archivo ni de etiquetas particulares. No contiene una receta que sustituya el programa cargado. El modo manual permite probar lógicas de otros procesos, aunque la animación GUNT representa únicamente esta planta de mezcla.

## Planta GUNT RT800: asignar correctamente el cableado

El Ladder contiene la lógica, pero no describe qué tubería mueve cada salida. La simulación hidráulica necesita un mapa explícito. Las asignaciones iniciales siguen el proyecto RT800; las direcciones que no existen en el LAD quedan **sin asignar**.

1. Selecciona **Planta GUNT RT800**. El cambio de modo inicia un ensayo nuevo, en pausa.
2. Despliega **Mapa GUNT y parámetros del agua**.
3. Asigna sensores a entradas I: alto/bajo de T1, T2 y T3; alto/medio/bajo del reactor; fuente disponible; STOP permitido y retorno permitido. Una entrada sin asignar no envía esa condición al programa.
4. Asigna V1…V8, bomba y agitador a sus salidas Q. Las marcas M y salidas analógicas AQ no controlan directamente las válvulas de este modelo.
5. Selecciona la entrada digital o cursor que utiliza el programa como START. No puede compartir la dirección de un sensor. Si el programa arranca por M8, deja START sin asignar y utiliza **Ejecutar**.
6. Ajusta caudales, umbrales y volúmenes iniciales. Pulsa **Aplicar y reiniciar ensayo**. Se comprueban rangos, señales existentes y direcciones repetidas.
7. Pulsa **START asignado** si el programa tiene ese pulsador, o **Ejecutar** para continuar un programa que arranca por energización. El modelo actualiza los sensores asignados a partir del agua; esos controles manuales aparecen deshabilitados.
8. Observa el llenado y vaciado, las órdenes de bomba y agitador, la cantidad de leche dosificada y las observaciones del modelo. Verde indica orden activa del LAD; azul indica una ruta de flujo disponible. El agitador dibujado dentro del reactor gira cuando su salida asignada está activa y el reloj está en marcha; al pausar o apagar esa salida se detiene.
9. Prueba los fallos virtuales: STOP no permitido, fuente no disponible, retorno no permitido o una válvula bloqueada. El modelo comunica las condiciones por las entradas asignadas. Si el programa no lee esas condiciones, sus órdenes pueden continuar activas; el simulador no añade un enclavamiento de protección al Ladder.
10. **Exportar mapa** guarda JSON. **Importar mapa** permite reutilizarlo únicamente con el mismo contenido de LAD, comprobado mediante SHA-256. Renombrar el archivo no invalida el mapa; cambiar la lógica sí.

### Parámetros iniciales del modelo

| Elemento | Valor inicial |
|---|---:|
| Capacidad T1, T2, T3 | 1,5 L cada uno |
| Capacidad reactor | 7 L |
| Capacidad reserva | 70 L |
| Agua en reserva al iniciar | 12 L |
| Agua en tanques/reactor al iniciar | 0 L |
| Caudal de bomba | 0,10 L/s, repartido entre rutas abiertas |
| Caudal de dosificación | 0,08 L/s por válvula abierta |
| Caudal de descarga de reactor | 0,12 L/s |
| Caudal de retorno | 0,06 L/s por tanque conectado |
| Alto / bajo de T1…T3 | 1,45 / 0,08 L |
| Alto / medio / bajo del reactor | 6,8 / 3,5 / 0,12 L |

Los caudales y umbrales son parámetros didácticos, no mediciones de la planta real. El retorno por V1/V3/V5 con V7 abierta y bomba apagada es una hipótesis hidráulica. El modelo conserva el agua y limita los volúmenes a la capacidad; registra intentos de sobrellenado, por lo que no reproduce una fuga exterior ni un rebose real. No añade una etapa FIN, ni apaga salidas por alcanzar un límite de observación.

## Ejemplos incluidos

Consulta los ejemplos propios y la prueba rápida en `README.md`. Esta edición no incluye los programas del proyecto académico ni de otros integrantes.

## Compatibilidad y límites

Admite archivos `.lld` de LOGO! 8 y 9 con bloques compatibles. Detecta automáticamente el contenedor histórico y los formatos cifrados 8.3, 8.4 y 9 según el controlador de destino, incluso cuando el editor V9 guarda para LOGO! 8. La memoria virtual se dimensiona según ese controlador. No necesitas elegir una versión ni tener instalado LOGO! 8 o 9 para usar el ZIP completo.

La prueba `Verificacion/Compatibilidad_LOGO_8_9.json` cubre ocho combinaciones: contenedores históricos con cabecera 8.0/8.1/8.2; cifrados 8.3 y 8.4; editor V9 con destino 8.3/8.4; y V9 con destino LOGO! 9. Verifica directo, TON y AI/AQ en cada caso, y el rechazo de una copia protegida. Son circuitos propios escritos con las clases Siemens instaladas de V9; no constituyen una prueba de todas las variantes producidas por editores antiguos. Las verificaciones con programas del proyecto académico quedan en el workspace original y no se distribuyen aquí.

Un archivo válido para LOGO! puede utilizar funciones no implementadas por este banco. Se rechazan bloques detectados de comunicación de red/AS-i, pantallas de mensajes y calendarios; un formato o bloque no admitido produce un mensaje de carga y conserva el último programa válido en pausa. Los archivos protegidos no se abren: desde LOGO!, guarda una copia sin contraseña con autorización del propietario. El programa no elimina ni omite la protección.

La simulación usa paso fijo de 0,1 s. Pulsos y temporizadores inferiores a ese paso, entradas rápidas, dispositivos remotos, calendario y retención entre reinicios requieren la simulación oficial y el equipo correspondiente. No es una certificación universal de archivos `.lld` ni una validación física. La GUI oficial de LOGO! no se verificó interactivamente en esta entrega.

Las comprobaciones se encuentran en `Verificacion/Compatibilidad_LOGO_8_9.json`; validan el importador, el motor local y el modelo, no convierten las diferencias de los programas originales en requisitos aprobados.

## Diagnóstico y mantenimiento

- Si falta el motor, instala una copia autorizada de LOGO! Soft Comfort V9 e indica su carpeta mediante `LOGO_INSTALL` o `-MotorPath`. Esta edición no incluye `MotorSiemens`.
- Si el archivo no carga, revisa el mensaje: versión no admitida, protección, bloque especial o archivo incompleto. Guarda una copia desde LOGO! V9 cuando corresponda.
- Si no cambia el agua, comprueba modo GUNT, reloj en marcha, ruta y mapa de Q. Un contacto llamado «bomba» no basta: la salida asignada debe ser la que manda la bomba.
- Si no inicia, asigna el START que realmente utiliza el LAD. Si el programa usa M8 de primer ciclo, prueba Ejecutar.
- Si una parada no corta salidas, comprueba el cableado lógico del programa. Pausar sólo congela la simulación.
- Los archivos `datos/motor.log` y `datos/motor_errores.log` registran problemas del motor. Las cargas y el perfil de simulación permanecen dentro de `datos`; el paquete no necesita modificar la instalación de LOGO!.
- Las fuentes Java del servidor y el motor están en `Fuentes`. El JAR de la aplicación ya está compilado; no se compila nada al abrirlo. El motor Siemens conserva sus binarios originales.


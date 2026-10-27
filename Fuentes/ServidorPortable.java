import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;
import com.google.gson.*;
import DE.siemens.ad.logo.model.*;
import DE.siemens.ad.logo.model.block.*;

/** Servicio local portable. Importación limitada, carga atómica y temporización virtual. */
public final class ServidorPortable {
 static final String VERSION="1.1.0";
 static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
 static Path folder;static HttpServer server;static int port;static MotorLAD motor;static Path loaded;
 static String name="",sha="",mode="manual",failure="",stopReason="";static boolean paused=true,stopped=false,completed=false,endWasActive=false;static int speed=10;
 static PlantaPortable plant;static final Map<String,Integer> pulses=new HashMap<>();
 static final ArrayDeque<String> trace=new ArrayDeque<>(),events=new ArrayDeque<>();static String traceHeader="";static long traceDropped=0;
 static HistorialEnsayos historyStore;static String runId="",runCreatedAt="",savedHistoryId="",historyError="",lastSavedFailure="";
 static long lastSavedClock=-1,historyRevision=0;static boolean lastSavedCompleted=false,lastSavedStopped=false;
 static String configWarning="";
 static Path configPath(String digest){if(!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Huella de programa no válida");return folder.resolve("datos/mapas/"+digest+".json");}
 static PlantaPortable.Config loadConfig(MotorLAD candidate,String digest)throws IOException {
  Path file=configPath(digest);if(!Files.isRegularFile(file))return PlantaPortable.defaults(candidate);
  PlantaPortable.Config c=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),PlantaPortable.Config.class);if(c==null)throw new IOException("Mapa guardado vacío");c.validate(candidate);return c;
 }
 static void persistConfig(){
  try{Path target=configPath(sha);Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"mapa-",".tmp");
   try{Files.writeString(temp,JSON.toJson(plant.config),StandardCharsets.UTF_8);try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
   configWarning="";
  }catch(Exception e){configWarning="Ajustes aplicados, pero no se pudieron guardar para la próxima carga: "+e.getMessage();event(configWarning);}
 }
 static HistorialEnsayos history(){if(historyStore==null)historyStore=new HistorialEnsayos(folder.resolve("datos/historial"));return historyStore;}
 static String traceCsv(){return "\uFEFF"+traceHeader+"\n"+String.join("\n",trace)+"\n";}
 static void saveHistory(String reason)throws IOException {
  requireLoaded();if(runId.isEmpty())throw new IllegalArgumentException("El ensayo aún no está inicializado");
  if(runId.equals(savedHistoryId)&&lastSavedClock==motor.clock.now&&lastSavedCompleted==completed&&lastSavedStopped==stopped&&lastSavedFailure.equals(failure)&&historyError.isEmpty())return;
  String status=completed?"Finalizado":!failure.isEmpty()?"Error":stopped?"Detenido":"Parcial";
  Map<String,Object> record=map("schema",1,"id",runId,"createdAt",runCreatedAt,"savedAt",Instant.now().toString(),"programName",name,"programSHA256",sha,"mode",mode,"duration",motor.clock.now/1000.,"status",status,"reason",!stopReason.isEmpty()?stopReason:reason,"config",plant.config,"tanks",mode.equals("gunt")?plant.tankRows():List.of(),"operations",mode.equals("gunt")?plant.movementRows():List.of(),"events",new ArrayList<>(events),"traceDropped",traceDropped,"error",failure);
  history().save(runId,record,traceCsv());savedHistoryId=runId;lastSavedClock=motor.clock.now;lastSavedCompleted=completed;lastSavedStopped=stopped;lastSavedFailure=failure;historyError="";historyRevision++;
 }
 static void autoSaveHistory(String reason){if(motor==null||runId.isEmpty()||(motor.clock.now<=100&&!stopped&&failure.isEmpty()))return;try{saveHistory(reason);}catch(Exception e){String next="No se pudo guardar el historial: "+e.getMessage();if(!next.equals(historyError))event(next);historyError=next;e.printStackTrace();}}
 static Map<String,Object> map(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 static void event(String s){events.add(String.format(Locale.ROOT,"%.1f s | %s",motor==null?0:motor.clock.now/1000.,s));while(events.size()>150)events.remove();}
 static void tick()throws Exception {
  if(motor==null)throw new IllegalArgumentException("Carga primero un archivo .lld");
  if(stopped)return;
  if(mode.equals("gunt"))plant.sensors(motor);
  for(String a:pulses.keySet())motor.input(a,1);
  motor.tick(100);if(mode.equals("gunt"))plant.flow(motor,.1);
  Iterator<Map.Entry<String,Integer>> it=pulses.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Integer> p=it.next();int left=p.getValue()-1;if(left<=0){motor.input(p.getKey(),0);it.remove();}else p.setValue(left);}
  boolean end=endActive();
  if(plant.config.autoStop&&end&&!endWasActive){paused=true;stopped=true;completed=true;stopReason="Fin de ciclo · "+plant.config.endSignal;for(String a:pulses.keySet())motor.input(a,0);pulses.clear();event("STOP automático: "+stopReason+". Niveles y tiempos finales conservados.");}
  endWasActive=end;sample();if(stopped)autoSaveHistory("Fin de ciclo");
 }
 static boolean endActive(){String a=plant.config.endSignal;return !a.isEmpty()&&motor.value(motor.byAddress.get(a))!=0;}
 static void resetStop(){stopped=false;completed=false;stopReason="";endWasActive=endActive();}
 static void requireNotStopped(){if(stopped)throw new IllegalArgumentException("Ensayo en STOP. Pulsa Reiniciar desde cero para iniciar otra simulación.");}
 static void sample(){
  StringJoiner row=new StringJoiner(";");row.add(String.format(Locale.ROOT,"%.3f",motor.clock.now/1000.));row.add(mode);
  for(String a:motor.inputs.keySet())row.add(String.valueOf(motor.inputs.get(a)));
  for(Object item:motor.diagram.getOutputBlocks())row.add(String.valueOf(motor.value((Block)item)));
  if(mode.equals("gunt")){for(double v:plant.volumes)row.add(String.format(Locale.ROOT,"%.6f",v));row.add(String.format(Locale.ROOT,"%.6f",plant.reserve));}else for(int n=0;n<5;n++)row.add("");
  for(int k=0;k<5;k++){row.add(mode.equals("gunt")?String.format(Locale.ROOT,"%.1f",plant.fillSeconds[k]):"");row.add(mode.equals("gunt")?String.format(Locale.ROOT,"%.1f",plant.drainSeconds[k]):"");}row.add(stopped?"STOP":paused?"PAUSA":"RUN");row.add(completed?"1":"0");
  if(trace.size()>=20000){trace.remove();traceDropped++;}trace.add(row.toString());
 }
 static void initializeTrace(){runId=UUID.randomUUID().toString();runCreatedAt=Instant.now().toString();savedHistoryId="";lastSavedClock=-1;trace.clear();traceDropped=0;StringJoiner header=new StringJoiner(";");header.add("Tiempo_s");header.add("Modo");for(String a:motor.inputs.keySet())header.add(a);for(Object b:motor.diagram.getOutputBlocks())header.add(MotorLAD.address((Block)b));for(String a:new String[]{"T1_L","T2_L","T3_L","Reactor_L","Reserva_L"})header.add(a);for(String a:new String[]{"T1","T2","T3","Reactor","Reserva"}){header.add(a+"_llenado_s");header.add(a+"_vaciado_s");}header.add("Estado");header.add("Completado");traceHeader=header.toString();}
 static void fresh(String newMode,PlantaPortable.Config config)throws Exception{
  MotorLAD candidate=new MotorLAD(loaded);config.validate(candidate);PlantaPortable nextPlant=new PlantaPortable(config);if(newMode.equals("gunt"))nextPlant.sensors(candidate);candidate.tick(100);if(newMode.equals("gunt"))nextPlant.flow(candidate,.1);JSON.toJson(candidate.describe(candidate.diagram.getBlocks(),true));autoSaveHistory(mode.equals(newMode)?"Ensayo reiniciado":"Cambio de modo");motor=candidate;plant=nextPlant;mode=newMode;paused=true;failure="";resetStop();pulses.clear();events.clear();initializeTrace();sample();event("Nuevo ensayo, memorias reinicializadas y M8 aplicado en el primer ciclo. Reloj en pausa.");
 }
 static Map<String,Object> state(){
  Map<String,Object> s=map("service","LOGO_LAD_PORTABLE","folder",folder.toString(),"loaded",motor!=null,"name",name,"sha256",sha,"mode",mode,"paused",paused,"stopped",stopped,"completed",completed,"stopReason",stopReason,"speed",speed,"error",failure,"events",new ArrayList<>(events),"traceDropped",traceDropped,"sensorRoles",PlantaPortable.SENSOR,"actorRoles",PlantaPortable.ACTOR);
  s.putAll(map("runId",runId,"savedHistoryId",savedHistoryId,"historyRevision",historyRevision,"historyError",historyError));
  s.put("version",VERSION);s.put("configWarning",configWarning);if(plant!=null){s.put("sensorRoles",plant.sensorRoles());s.put("actorRoles",plant.actorRoles());}
  if(plant!=null&&plant.config.water!=null&&plant.config.water.enabled){double[] props=HidraulicaAgua.water(plant.config.water.temperatureC);s.put("waterProperties",map("densityKgM3",props[0],"viscosityPaS",props[1],"gravityMs2",HidraulicaAgua.G));}
  if(motor!=null){s.put("time",motor.clock.now/1000.);s.put("hardware",motor.diagram.getHardware().getClass().getSimpleName());s.put("inputs",motor.describe(motor.diagram.getInputBlocks(),false));s.put("outputs",motor.describe(motor.diagram.getOutputBlocks(),true));s.put("blocks",motor.describe(motor.diagram.getBlocks(),true));s.put("config",plant.config);s.put("plant",map("volumes",plant.volumes,"reserve",plant.reserve,"milk",plant.milk,"fillSeconds",plant.fillSeconds,"drainSeconds",plant.drainSeconds,"inflow",plant.inflow,"outflow",plant.outflow,"stop",plant.stop,"source",plant.source,"returnOK",plant.returnOK,"blocked",plant.blocked,"observations",plant.observations));}return s;
 }
 static void reply(HttpExchange e,int code,String type,byte[] data)throws IOException{e.getResponseHeaders().set("Content-Type",type);e.getResponseHeaders().set("Cache-Control","no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.sendResponseHeaders(code,data.length);try(OutputStream out=e.getResponseBody()){out.write(data);}}
 static void reply(HttpExchange e,int code,Object data)throws IOException{reply(e,code,"application/json; charset=utf-8",JSON.toJson(data).getBytes(StandardCharsets.UTF_8));}
 static void requireLoaded(){if(motor==null)throw new IllegalArgumentException("Carga primero un archivo .lld");}
 static void unload()throws IOException {
  paused=true;autoSaveHistory("Archivo quitado");
  // Quitar sólo la copia importada, validando su ubicación y nombre. El original no se toca.
  if(loaded!=null){Path copy=loaded.toAbsolutePath().normalize(),allowed=folder.resolve("datos/cargas").toAbsolutePath().normalize();
   if(!allowed.equals(copy.getParent())||!copy.getFileName().toString().matches("[a-f0-9]{64}\\.lld"))throw new IOException("La copia cargada no está dentro de datos/cargas; no se ha eliminado.");
   Files.deleteIfExists(copy);
  }
  motor=null;loaded=null;plant=null;name="";sha="";mode="manual";failure="";speed=10;stopped=false;completed=false;stopReason="";endWasActive=false;
  pulses.clear();trace.clear();traceHeader="";traceDropped=0;events.clear();runId="";savedHistoryId="";configWarning="";
 }
 static void action(JsonObject body)throws Exception {
  String cmd=body.has("cmd")?body.get("cmd").getAsString():"";if(cmd.equals("unload")){unload();return;}requireLoaded();
  if(Set.of("run","step","advance","input","pulse","start","fault").contains(cmd))requireNotStopped();
  switch(cmd){
   case "run":if(!failure.isEmpty())throw new IllegalArgumentException("Reinicia el ensayo después del error");paused=false;event("Reloj virtual en marcha.");break;
   case "pause":paused=true;break;
   case "stop":paused=true;stopped=true;stopReason="Detención manual";for(String a:pulses.keySet())motor.input(a,0);pulses.clear();event("STOP manual. Reinicia para un nuevo ensayo.");sample();autoSaveHistory("STOP manual");break;
   case "save-history":saveHistory("Guardado manual del ensayo");break;
   case "step":paused=true;tick();break;
   case "advance":{paused=true;double seconds=body.get("seconds").getAsDouble();if(!Double.isFinite(seconds)||seconds<.1||seconds>60)throw new IllegalArgumentException("Avance: 0.1 a 60 s");for(int n=0;n<(int)Math.round(seconds*10)&&!stopped;n++)tick();break;}
   case "speed":{int value=body.get("value").getAsInt();if(value<1||value>50)throw new IllegalArgumentException("Velocidad: 1 a 50");speed=value;break;}
   case "input":{String a=body.get("address").getAsString();if(mode.equals("gunt")&&a.startsWith("I")){int number=Integer.parseInt(a.substring(1));for(int n:plant.config.sensors)if(number==n)throw new IllegalArgumentException("Esta entrada la gobierna la planta; cambia a modo manual para forzarla");}motor.input(a,body.get("value").getAsDouble());pulses.remove(a);if(paused)tick();break;}
   case "pulse":{String a=body.get("address").getAsString();if(!motor.inputs.containsKey(a)||a.startsWith("AI"))throw new IllegalArgumentException("Pulso sólo para entradas digitales disponibles");if(mode.equals("gunt")&&a.startsWith("I")){int n=Integer.parseInt(a.substring(1));for(int input:plant.config.sensors)if(n==input)throw new IllegalArgumentException("Entrada asignada a un sensor de la planta");}pulses.put(a,3);if(paused)tick();event("Pulso de 0.3 s solicitado en "+a+". Avanza el reloj para completarlo.");break;}
   case "start":if(!mode.equals("gunt")||plant.config.start.isEmpty())throw new IllegalArgumentException("Asigna la entrada START en el mapa GUNT");pulses.put(plant.config.start,3);paused=false;event("START en "+plant.config.start);break;
   case "restart":paused=true;fresh(mode,plant.config);break;
   case "mode":{String value=body.get("value").getAsString();if(!value.equals("manual")&&!value.equals("gunt"))throw new IllegalArgumentException("Modo desconocido");paused=true;fresh(value,plant.config);break;}
   case "config":{paused=true;PlantaPortable.Config c=JSON.fromJson(body.get("config"),PlantaPortable.Config.class);if(c==null)throw new IllegalArgumentException("Configuración vacía");c.validate(motor);for(int n:c.sensors)if(n>0&&c.start.equals("I"+n))throw new IllegalArgumentException("START no puede compartir la dirección de un sensor");fresh(mode,c);persistConfig();event("Nombres, sensores y caudales aplicados a un nuevo ensayo.");break;}
   case "fault":{if(!mode.equals("gunt"))throw new IllegalArgumentException("Los fallos físicos requieren modo GUNT");String k=body.get("kind").getAsString();boolean v=body.get("value").getAsBoolean();switch(k){case "stop":plant.stop=!v;break;case "source":plant.source=!v;break;case "return":plant.returnOK=!v;break;default:if(!k.matches("v[1-8]"))throw new IllegalArgumentException("Fallo desconocido");int n=Integer.parseInt(k.substring(1))-1;if(v)plant.blocked.add(n);else plant.blocked.remove(n);}if(paused)tick();event("Fallo "+k+" "+(v?"activado":"retirado"));break;}
   default:throw new IllegalArgumentException("Acción desconocida");
  }
 }
 static void handle(HttpExchange e)throws IOException{
  String origin=e.getRequestHeaders().getFirst("Origin");String host=e.getRequestHeaders().getFirst("Host");
  if((origin!=null&&!origin.equals("http://127.0.0.1:"+port)&&!origin.equals("http://localhost:"+port))||host==null||(!host.equals("127.0.0.1:"+port)&&!host.equals("localhost:"+port))){reply(e,403,map("error","Origen no permitido"));return;}
  try{synchronized(ServidorPortable.class){String route=e.getRequestURI().getPath(),method=e.getRequestMethod();
   if(route.equals("/")&&method.equals("GET"))reply(e,200,"text/html; charset=utf-8",Files.readAllBytes(folder.resolve("interfaz.html")));
   else if(route.equals("/favicon.ico")){e.sendResponseHeaders(204,-1);e.close();}
   else if(route.equals("/api/info")&&method.equals("GET"))reply(e,200,map("service","LOGO_LAD_PORTABLE","version",VERSION,"folder",folder.toString()));
   else if(route.equals("/api/state")&&method.equals("GET"))reply(e,200,state());
   else if(route.equals("/guia")&&method.equals("GET"))reply(e,200,"text/html; charset=utf-8",Files.readAllBytes(folder.resolve("LEEME.html")));
   else if(route.equals("/modelo-tiempo")&&method.equals("GET"))reply(e,200,"text/html; charset=utf-8",Files.readAllBytes(folder.resolve("Modelo_tiempo_RT800.html")));
   else if(route.equals("/api/model-time")&&method.equals("POST")){
    requireLoaded();if(!mode.equals("gunt")||!paused)throw new IllegalArgumentException("Pausa la planta GUNT antes de calcular la duración");
    JsonObject body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();
    PlantaPortable.Config c=JSON.fromJson(body.get("config"),PlantaPortable.Config.class);if(c==null)throw new IllegalArgumentException("Configuración vacía");
    if(body.has("defaults")&&body.get("defaults").getAsBoolean())c=ModeloTiempo.initialParameters(c);
    if(body.has("gravity")&&body.get("gravity").getAsBoolean())c.water=HidraulicaAgua.fromMeasuredRates(c);
    c.validate(motor);reply(e,200,ModeloTiempo.predict(loaded,c));
   }
   else if(route.equals("/api/calibration")&&method.equals("POST")){
    requireLoaded();if(!mode.equals("gunt")||!paused)throw new IllegalArgumentException("Pausa la planta GUNT antes de probar la calibración");
    JsonObject body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();PlantaPortable.Config c=JSON.fromJson(body.get("config"),PlantaPortable.Config.class);
    if(c==null)throw new IllegalArgumentException("Configuración vacía");c.validate(motor);reply(e,200,CalibracionCaudales.suggest(loaded,c,body.get("seconds").getAsDouble()));
   }
   else if(route.equals("/api/history")&&method.equals("GET"))reply(e,200,history().list());
   else if(route.startsWith("/api/history/")&&method.equals("GET")){
    String[] parts=route.substring("/api/history/".length()).split("/");String id=parts[0];
    if(parts.length==1)reply(e,200,history().read(id));
    else if(parts.length==2&&Set.of("trace.csv","summary.csv","movements.csv","record.json").contains(parts[1])){
     history().read(id);
     e.getResponseHeaders().set("Content-Disposition","attachment; filename=ensayo_"+id+"_"+parts[1]);
     if(parts[1].equals("record.json"))reply(e,200,history().read(id));
     else reply(e,200,"text/csv; charset=utf-8",parts[1].equals("trace.csv")?history().trace(id):history().csv(id,parts[1].equals("movements.csv")).getBytes(StandardCharsets.UTF_8));
    }else reply(e,404,map("error","Descarga no disponible"));
   }
   else if(route.equals("/api/upload")&&method.equals("POST")){
    paused=true;String encoded=e.getRequestHeaders().getFirst("X-File-Name");String filename=encoded==null?"programa.lld":URLDecoder.decode(encoded,StandardCharsets.UTF_8);
    filename=filename.replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);if(filename.length()>180||!filename.toLowerCase(Locale.ROOT).endsWith(".lld"))throw new IllegalArgumentException("Selecciona un archivo .lld");
    byte[] bytes=e.getRequestBody().readNBytes(20*1024*1024+1);if(bytes.length>20*1024*1024)throw new IllegalArgumentException("Máximo 20 MB por archivo");
    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));Path upload=folder.resolve("datos/cargas/"+digest+".lld");Files.createDirectories(upload.getParent());Files.write(upload,bytes);
    MotorLAD candidate=new MotorLAD(upload);PlantaPortable.Config c;String warning="";try{c=loadConfig(candidate,digest);}catch(Exception err){c=PlantaPortable.defaults(candidate);warning="No se pudieron leer los ajustes guardados; se cargaron los valores iniciales: "+err.getMessage();}c.validate(candidate);candidate.tick(100);JSON.toJson(candidate.describe(candidate.diagram.getBlocks(),true));
    autoSaveHistory("Archivo sustituido");motor=candidate;loaded=upload;name=filename;sha=digest;plant=new PlantaPortable(c);mode="manual";failure="";configWarning=warning;resetStop();pulses.clear();events.clear();initializeTrace();sample();event("Archivo cargado sin cambiar el original. Entradas manuales a cero; M8 de primer ciclo.");if(!warning.isEmpty())event(warning);reply(e,200,state());
   }else if(route.equals("/api/action")&&method.equals("POST")){JsonObject body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();action(body);reply(e,200,state());}
   else if(route.equals("/api/config")&&method.equals("GET")){requireLoaded();e.getResponseHeaders().set("Content-Disposition","attachment; filename=mapa_gunt.json");reply(e,200,map("schema",1,"programSHA256",sha,"config",plant.config));}
   else if(route.equals("/api/trace")&&method.equals("GET")){requireLoaded();e.getResponseHeaders().set("Content-Disposition","attachment; filename=traza_simulacion.csv");reply(e,200,"text/csv; charset=utf-8",traceCsv().getBytes(StandardCharsets.UTF_8));}
   else if(route.equals("/api/shutdown")&&method.equals("POST")){paused=true;autoSaveHistory("Motor cerrado");reply(e,200,map("closed",true));Thread end=new Thread(()->{try{Thread.sleep(300);}catch(Exception ignored){}System.exit(0);});end.setDaemon(true);end.start();}
   else reply(e,404,map("error","Ruta no disponible"));
  }}catch(Throwable err){err.printStackTrace();reply(e,400,map("error",err.getMessage()==null?err.getClass().getSimpleName():err.getMessage()));}
 }
 public static void main(String[] args)throws Exception{
  folder=Paths.get(args[0]).toAbsolutePath();port=args.length>1?Integer.parseInt(args[1]):8768;RuntimeLogo.initialize();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);server.createContext("/",ServidorPortable::handle);server.setExecutor(Executors.newFixedThreadPool(3));server.start();
  ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor();loop.scheduleAtFixedRate(()->{synchronized(ServidorPortable.class){if(motor!=null&&!paused)try{for(int n=0;n<speed&&!paused;n++)tick();}catch(Throwable err){paused=true;failure=err.toString();event("Ensayo detenido por error del motor.");autoSaveHistory("Error del motor");err.printStackTrace();}}},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);
  System.out.println("SIMULADOR PORTABLE: http://127.0.0.1:"+port+"/");
 }
}

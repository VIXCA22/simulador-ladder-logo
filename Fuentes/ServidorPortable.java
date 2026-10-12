import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;
import com.google.gson.*;
import DE.siemens.ad.logo.model.*;
import DE.siemens.ad.logo.model.block.*;

/** Servicio local portable. Importación limitada, carga atómica y temporización virtual. */
public final class ServidorPortable {
 static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
 static Path folder;static HttpServer server;static int port;static MotorLAD motor;static Path loaded;
 static String name="",sha="",mode="manual",failure="";static boolean paused=true;static int speed=10;
 static PlantaPortable plant;static final Map<String,Integer> pulses=new HashMap<>();
 static final ArrayDeque<String> trace=new ArrayDeque<>(),events=new ArrayDeque<>();static String traceHeader="";static long traceDropped=0;
 static Map<String,Object> map(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 static void event(String s){events.add(String.format(Locale.ROOT,"%.1f s | %s",motor==null?0:motor.clock.now/1000.,s));while(events.size()>150)events.remove();}
 static void tick()throws Exception {
  if(motor==null)throw new IllegalArgumentException("Carga primero un archivo .lld");
  if(mode.equals("gunt"))plant.sensors(motor);
  for(String a:pulses.keySet())motor.input(a,1);
  motor.tick(100);if(mode.equals("gunt"))plant.flow(motor,.1);
  Iterator<Map.Entry<String,Integer>> it=pulses.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Integer> p=it.next();int left=p.getValue()-1;if(left<=0){motor.input(p.getKey(),0);it.remove();}else p.setValue(left);}
  sample();
 }
 static void sample(){
  StringJoiner row=new StringJoiner(";");row.add(String.format(Locale.ROOT,"%.3f",motor.clock.now/1000.));row.add(mode);
  for(String a:motor.inputs.keySet())row.add(String.valueOf(motor.inputs.get(a)));
  for(Object item:motor.diagram.getOutputBlocks())row.add(String.valueOf(motor.value((Block)item)));
  if(mode.equals("gunt")){for(double v:plant.volumes)row.add(String.format(Locale.ROOT,"%.6f",v));row.add(String.format(Locale.ROOT,"%.6f",plant.reserve));}else for(int n=0;n<5;n++)row.add("");
  if(trace.size()>=20000){trace.remove();traceDropped++;}trace.add(row.toString());
 }
 static void initializeTrace(){trace.clear();traceDropped=0;StringJoiner header=new StringJoiner(";");header.add("Tiempo_s");header.add("Modo");for(String a:motor.inputs.keySet())header.add(a);for(Object b:motor.diagram.getOutputBlocks())header.add(MotorLAD.address((Block)b));for(String a:new String[]{"T1_L","T2_L","T3_L","Reactor_L","Reserva_L"})header.add(a);traceHeader=header.toString();}
 static void fresh(String newMode,PlantaPortable.Config config)throws Exception{
  MotorLAD candidate=new MotorLAD(loaded);config.validate(candidate);PlantaPortable nextPlant=new PlantaPortable(config);if(newMode.equals("gunt"))nextPlant.sensors(candidate);candidate.tick(100);if(newMode.equals("gunt"))nextPlant.flow(candidate,.1);JSON.toJson(candidate.describe(candidate.diagram.getBlocks(),true));motor=candidate;plant=nextPlant;mode=newMode;paused=true;failure="";pulses.clear();events.clear();initializeTrace();sample();event("Nuevo ensayo, memorias reinicializadas y M8 aplicado en el primer ciclo. Reloj en pausa.");
 }
 static Map<String,Object> state(){
  Map<String,Object> s=map("service","LOGO_LAD_PORTABLE","folder",folder.toString(),"loaded",motor!=null,"name",name,"sha256",sha,"mode",mode,"paused",paused,"speed",speed,"error",failure,"events",new ArrayList<>(events),"traceDropped",traceDropped,"sensorRoles",PlantaPortable.SENSOR,"actorRoles",PlantaPortable.ACTOR);
  if(motor!=null){s.put("time",motor.clock.now/1000.);s.put("hardware",motor.diagram.getHardware().getClass().getSimpleName());s.put("inputs",motor.describe(motor.diagram.getInputBlocks(),false));s.put("outputs",motor.describe(motor.diagram.getOutputBlocks(),true));s.put("blocks",motor.describe(motor.diagram.getBlocks(),true));s.put("config",plant.config);s.put("plant",map("volumes",plant.volumes,"reserve",plant.reserve,"milk",plant.milk,"stop",plant.stop,"source",plant.source,"returnOK",plant.returnOK,"blocked",plant.blocked,"observations",plant.observations));}return s;
 }
 static void reply(HttpExchange e,int code,String type,byte[] data)throws IOException{e.getResponseHeaders().set("Content-Type",type);e.getResponseHeaders().set("Cache-Control","no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.sendResponseHeaders(code,data.length);try(OutputStream out=e.getResponseBody()){out.write(data);}}
 static void reply(HttpExchange e,int code,Object data)throws IOException{reply(e,code,"application/json; charset=utf-8",JSON.toJson(data).getBytes(StandardCharsets.UTF_8));}
 static void requireLoaded(){if(motor==null)throw new IllegalArgumentException("Carga primero un archivo .lld");}
 static void unload()throws IOException {
  paused=true;
  // Quitar sólo la copia importada, validando su ubicación y nombre. El original no se toca.
  if(loaded!=null){Path copy=loaded.toAbsolutePath().normalize(),allowed=folder.resolve("datos/cargas").toAbsolutePath().normalize();
   if(!allowed.equals(copy.getParent())||!copy.getFileName().toString().matches("[a-f0-9]{64}\\.lld"))throw new IOException("La copia cargada no está dentro de datos/cargas; no se ha eliminado.");
   Files.deleteIfExists(copy);
  }
  motor=null;loaded=null;plant=null;name="";sha="";mode="manual";failure="";speed=10;
  pulses.clear();trace.clear();traceHeader="";traceDropped=0;events.clear();
 }
 static void action(JsonObject body)throws Exception {
  String cmd=body.has("cmd")?body.get("cmd").getAsString():"";if(cmd.equals("unload")){unload();return;}requireLoaded();
  switch(cmd){
   case "run":if(!failure.isEmpty())throw new IllegalArgumentException("Reinicia el ensayo después del error");paused=false;event("Reloj virtual en marcha.");break;
   case "pause":paused=true;break;
   case "step":paused=true;tick();break;
   case "advance":{paused=true;double seconds=body.get("seconds").getAsDouble();if(!Double.isFinite(seconds)||seconds<.1||seconds>60)throw new IllegalArgumentException("Avance: 0.1 a 60 s");for(int n=0;n<(int)Math.round(seconds*10);n++)tick();break;}
   case "speed":{int value=body.get("value").getAsInt();if(value<1||value>50)throw new IllegalArgumentException("Velocidad: 1 a 50");speed=value;break;}
   case "input":{String a=body.get("address").getAsString();if(mode.equals("gunt")&&a.startsWith("I")){int number=Integer.parseInt(a.substring(1));for(int n:plant.config.sensors)if(number==n)throw new IllegalArgumentException("Esta entrada la gobierna la planta; cambia a modo manual para forzarla");}motor.input(a,body.get("value").getAsDouble());pulses.remove(a);if(paused)tick();break;}
   case "pulse":{String a=body.get("address").getAsString();if(!motor.inputs.containsKey(a)||a.startsWith("AI"))throw new IllegalArgumentException("Pulso sólo para entradas digitales disponibles");if(mode.equals("gunt")&&a.startsWith("I")){int n=Integer.parseInt(a.substring(1));for(int input:plant.config.sensors)if(n==input)throw new IllegalArgumentException("Entrada asignada a un sensor de la planta");}pulses.put(a,3);if(paused)tick();event("Pulso de 0.3 s solicitado en "+a+". Avanza el reloj para completarlo.");break;}
   case "start":if(!mode.equals("gunt")||plant.config.start.isEmpty())throw new IllegalArgumentException("Asigna la entrada START en el mapa GUNT");pulses.put(plant.config.start,3);paused=false;event("START en "+plant.config.start);break;
   case "restart":paused=true;fresh(mode,plant.config);break;
   case "mode":{String value=body.get("value").getAsString();if(!value.equals("manual")&&!value.equals("gunt"))throw new IllegalArgumentException("Modo desconocido");paused=true;fresh(value,plant.config);break;}
   case "config":{paused=true;PlantaPortable.Config c=JSON.fromJson(body.get("config"),PlantaPortable.Config.class);if(c==null)throw new IllegalArgumentException("Configuración vacía");c.validate(motor);for(int n:c.sensors)if(n>0&&c.start.equals("I"+n))throw new IllegalArgumentException("START no puede compartir la dirección de un sensor");fresh(mode,c);event("Mapa y parámetros aplicados a un nuevo ensayo.");break;}
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
   else if(route.equals("/api/info")&&method.equals("GET"))reply(e,200,map("service","LOGO_LAD_PORTABLE","folder",folder.toString()));
   else if(route.equals("/api/state")&&method.equals("GET"))reply(e,200,state());
   else if(route.equals("/api/upload")&&method.equals("POST")){
    paused=true;String encoded=e.getRequestHeaders().getFirst("X-File-Name");String filename=encoded==null?"programa.lld":URLDecoder.decode(encoded,StandardCharsets.UTF_8);
    filename=filename.replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);if(filename.length()>180||!filename.toLowerCase(Locale.ROOT).endsWith(".lld"))throw new IllegalArgumentException("Selecciona un archivo .lld");
    byte[] bytes=e.getRequestBody().readNBytes(20*1024*1024+1);if(bytes.length>20*1024*1024)throw new IllegalArgumentException("Máximo 20 MB por archivo");
    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));Path upload=folder.resolve("datos/cargas/"+digest+".lld");Files.createDirectories(upload.getParent());Files.write(upload,bytes);
    MotorLAD candidate=new MotorLAD(upload);PlantaPortable.Config c=PlantaPortable.defaults(candidate);c.validate(candidate);candidate.tick(100);JSON.toJson(candidate.describe(candidate.diagram.getBlocks(),true));
    motor=candidate;loaded=upload;name=filename;sha=digest;plant=new PlantaPortable(c);mode="manual";failure="";pulses.clear();events.clear();initializeTrace();sample();event("Archivo cargado sin cambiar el original. Entradas manuales a cero; M8 de primer ciclo.");reply(e,200,state());
   }else if(route.equals("/api/action")&&method.equals("POST")){JsonObject body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();action(body);reply(e,200,state());}
   else if(route.equals("/api/config")&&method.equals("GET")){requireLoaded();e.getResponseHeaders().set("Content-Disposition","attachment; filename=mapa_gunt.json");reply(e,200,map("schema",1,"programSHA256",sha,"config",plant.config));}
   else if(route.equals("/api/trace")&&method.equals("GET")){requireLoaded();e.getResponseHeaders().set("Content-Disposition","attachment; filename=traza_simulacion.csv");reply(e,200,"text/csv; charset=utf-8",("\uFEFF"+traceHeader+"\n"+String.join("\n",trace)+"\n").getBytes(StandardCharsets.UTF_8));}
   else if(route.equals("/api/shutdown")&&method.equals("POST")){reply(e,200,map("closed",true));Thread end=new Thread(()->{try{Thread.sleep(300);}catch(Exception ignored){}System.exit(0);});end.setDaemon(true);end.start();}
   else reply(e,404,map("error","Ruta no disponible"));
  }}catch(Throwable err){err.printStackTrace();reply(e,400,map("error",err.getMessage()==null?err.getClass().getSimpleName():err.getMessage()));}
 }
 public static void main(String[] args)throws Exception{
  folder=Paths.get(args[0]).toAbsolutePath();port=args.length>1?Integer.parseInt(args[1]):8768;RuntimeLogo.initialize();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);server.createContext("/",ServidorPortable::handle);server.setExecutor(Executors.newFixedThreadPool(3));server.start();
  ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor();loop.scheduleAtFixedRate(()->{synchronized(ServidorPortable.class){if(motor!=null&&!paused)try{for(int n=0;n<speed;n++)tick();}catch(Throwable err){paused=true;failure=err.toString();event("Ensayo detenido por error del motor.");err.printStackTrace();}}},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);
  System.out.println("SIMULADOR PORTABLE: http://127.0.0.1:"+port+"/");
 }
}

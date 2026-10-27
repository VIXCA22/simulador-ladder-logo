import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Un archivo por ensayo, conservado entre reinicios del servidor. */
final class HistorialEnsayos {
 static final Gson JSON=new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
 private final Path directory;
 HistorialEnsayos(Path directory){this.directory=directory.toAbsolutePath().normalize();}
 private Path file(String id,String extension){
  if(id==null||!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw new IllegalArgumentException("Identificador de ensayo no válido");
  return directory.resolve(id+extension);
 }
 private void atomicWrite(Path destination,String content)throws IOException {
  Files.createDirectories(directory);Path temporary=Files.createTempFile(directory,"ensayo-",".tmp");
  try{Files.writeString(temporary,content,StandardCharsets.UTF_8);try{Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}}
  finally{Files.deleteIfExists(temporary);}
 }
 void save(String id,Map<String,Object> record,String trace)throws IOException {
  Path summary=file(id,".json"),oldTrace=Files.exists(summary)?tracePath(id,read(id)):null;
  String version=id+"-"+UUID.randomUUID()+".csv";Path nextTrace=directory.resolve(version);record.put("traceFile",version);
  atomicWrite(nextTrace,trace);
  try{atomicWrite(summary,JSON.toJson(record));}catch(IOException e){Files.deleteIfExists(nextTrace);throw e;}
  // Sólo se retira la versión anterior después de publicar el nuevo resumen.
  if(oldTrace!=null)try{Files.deleteIfExists(oldTrace);}catch(IOException ignored){}
 }
 JsonObject read(String id)throws IOException {
  Path source=file(id,".json");if(!Files.isRegularFile(source))throw new IllegalArgumentException("No se encuentra el ensayo guardado");
  try(Reader reader=Files.newBufferedReader(source,StandardCharsets.UTF_8)){
   JsonObject record=JsonParser.parseReader(reader).getAsJsonObject();
   if(!record.has("id")||!record.get("id").getAsString().equals(id)||!record.has("savedAt")||!record.has("duration")||!record.has("tanks")||!record.has("operations"))throw new IOException("Registro de ensayo incompleto");
   java.time.Instant.parse(record.get("savedAt").getAsString());return record;
  }
 }
 private Path tracePath(String id,JsonObject record)throws IOException {
  if(!record.has("traceFile"))return file(id,".csv");String name=record.get("traceFile").getAsString();
  if(!name.matches(java.util.regex.Pattern.quote(id)+"-[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.csv"))throw new IOException("Nombre de traza no válido");return directory.resolve(name);
 }
 byte[] trace(String id)throws IOException{return Files.readAllBytes(tracePath(id,read(id)));}
 Map<String,Object> list()throws IOException {
  List<JsonObject> records=new ArrayList<>();List<String> unreadable=new ArrayList<>();
  if(Files.isDirectory(directory))try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.json")){
   for(Path path:files){String filename=path.getFileName().toString(),id=filename.substring(0,filename.length()-5);
    try{JsonObject record=read(id),summary=new JsonObject();for(String key:new String[]{"id","createdAt","savedAt","programName","programSHA256","mode","duration","status","reason"})summary.add(key,record.get(key));records.add(summary);}
    catch(Exception e){unreadable.add(filename);}
   }
  }
  records.sort((a,b)->b.get("savedAt").getAsString().compareTo(a.get("savedAt").getAsString()));
  return ServidorPortable.map("runs",records,"unreadable",unreadable);
 }
 static String csvCell(JsonElement value){String s=value==null||value.isJsonNull()?"":value.getAsString();return s.contains(";")||s.contains("\"")||s.contains("\n")||s.contains("\r")?"\""+s.replace("\"","\"\"")+"\"":s;}
 String csv(String id,boolean operations)throws IOException {
  JsonObject record=read(id);StringBuilder out=new StringBuilder("\uFEFF");
  String[] keys=operations?new String[]{"tank","direction","start","end","seconds","litres","initialLitres","finalLitres"}:new String[]{"tank","capacity","initialLitres","finalLitres","maxLitres","fillSeconds","drainSeconds","filledLitres","drainedLitres"};
  out.append(operations?"Silo;Movimiento;Inicio_s;Fin_s;Duracion_s;Litros;Nivel_inicial_L;Nivel_final_L\n":"Silo;Capacidad_L;Inicial_L;Final_L;Maximo_L;Llenado_s;Vaciado_s;Entradas_L;Salidas_L\n");
  for(JsonElement item:record.getAsJsonArray(operations?"operations":"tanks")){JsonObject row=item.getAsJsonObject();StringJoiner line=new StringJoiner(";");for(String key:keys)line.add(csvCell(row.get(key)));out.append(line).append('\n');}return out.toString();
 }
}

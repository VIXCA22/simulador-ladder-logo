import java.awt.Component;
import java.lang.reflect.Field;
import java.io.*;
import java.nio.file.*;
import DE.siemens.ad.logo.model.*;
import DE.siemens.ad.logo.util.VariableMemory;
import DE.siemens.ad.pdraw.standard.LogoDrawing;
import DE.siemens.ad.logo.model.visitor.build.object.*;

/** Inicialización aislada y lectores originales; no abre el editor ni conecta con equipos. */
final class RuntimeLogo {
 static void initialize() throws Exception {
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
  sun.misc.Unsafe u=(sun.misc.Unsafe)uf.get(null);
  Class<?> c=Class.forName("DE.siemens.ad.logo.app.Application");Object a=u.allocateInstance(c);
  Field f=c.getDeclaredField("fSingletonInstance");f.setAccessible(true);f.set(null,a);
  f=c.getDeclaredField("windowManager");f.setAccessible(true);
  f.set(a,new DE.siemens.ad.logo.app.WindowManager(){public Component getActiveTab(){javax.swing.JPanel p=new javax.swing.JPanel();p.setName("Diagram");return p;}});
  // Un LAD es un grafo serializado. Sólo se admiten las familias que usa LOGO! y tamaños acotados.
  ObjectInputFilter.Config.setSerialFilter(info->{
   if(info.depth()>2000||info.references()>600000||info.arrayLength()>2000000||info.streamBytes()>40000000)return ObjectInputFilter.Status.REJECTED;
   Class<?> t=info.serialClass();if(t==null)return ObjectInputFilter.Status.UNDECIDED;
   while(t.isArray())t=t.getComponentType();if(t.isPrimitive())return ObjectInputFilter.Status.ALLOWED;
   String n=t.getName();
   return n.startsWith("DE.siemens.")||n.startsWith("CH.ifa.")||n.startsWith("java.util.")||n.startsWith("java.awt.")||n.startsWith("java.lang.")||n.startsWith("java.beans.")||n.startsWith("javax.swing.")||n.startsWith("javax.crypto.")||n.equals("com.sun.crypto.provider.SealedObjectForKeyProtector")||n.startsWith("java.security.KeyRep")?ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED;
  });
 }
 static class Reader83 extends CompilerFileToObjectLogo8Update3 {byte[] body(String p)throws IOException{if(isHasPassword(p))throw new IOException("Archivo protegido: guarda una copia sin contraseña desde LOGO! con autorización de su propietario.");return decrypt(p,true);}}
 static class Reader84 extends CompilerFileToObjectLogo8Update4 {byte[] body(String p)throws IOException{if(isHasPassword(p))throw new IOException("Archivo protegido: guarda una copia sin contraseña desde LOGO! con autorización de su propietario.");return decrypt(p,true);}}
 static class Reader9 extends CompilerFileToObjectLogo9 {byte[] body(String p)throws IOException{if(isHasPassword(p))throw new IOException("Archivo protegido: guarda una copia sin contraseña desde LOGO! con autorización de su propietario.");return decrypt(p,true);}}
 static LogoDrawing parse(InputStream stream)throws Exception{
  try(ObjectInputStream in=new ObjectInputStream(stream)){in.readInt();in.readInt();in.readInt();int count=in.readInt();if(count<0||count>100000)throw new IOException("Cantidad de figuras fuera de rango");for(int n=0;n<count;n++)in.readObject();Object drawing=in.readObject();if(!(drawing instanceof LogoDrawing))throw new IOException("El archivo no contiene un diagrama LOGO!");return (LogoDrawing)drawing;}
 }
 static LogoDrawing read(Path path)throws Exception{
  byte[] data=Files.readAllBytes(path);if(data.length<24)throw new IOException("Archivo incompleto o formato no reconocido");
  int major,minor;try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(data))){major=in.readInt();minor=in.readInt();}
  // El formato histórico tiene un primer bloque de exactamente cuatro enteros (16 bytes).
  if(data[4]==0x77&&(data[5]&255)==16)return parse(new ByteArrayInputStream(data));
  if(data[4]!=0x77||(data[5]&255)!=113||data.length<119)throw new IOException("Contenedor LOGO! no reconocido: V"+major+"."+minor);
  // La versión del editor no determina el cifrado: V9 también guarda diagramas para LOGO! 8.
  int target=DE.siemens.ad.logo.util.Util.bytes2Int(java.util.Arrays.copyOfRange(data,22,26));
  String p=path.toAbsolutePath().toString();byte[] body;
  if(target==new DE.siemens.ad.logo.model.hardware.Logo8Update2().getID())body=new Reader83().body(p);
  else if(target==new DE.siemens.ad.logo.model.hardware.Logo8Update4().getID())body=new Reader84().body(p);
  else if(target==new DE.siemens.ad.logo.model.hardware.Logo9().getID())body=new Reader9().body(p);
  else throw new IOException("Destino LOGO! no reconocido: "+target+", editor V"+major+"."+minor);
  return parse(new ByteArrayInputStream(body));
 }
 static final class Clock extends Interpreter {
  // LOGO! 9 usa un mapa VM mayor; el tamaño procede del controlador nativo.
  long now;Clock(WiringDiagram d){fWiringDiagram=d;fVM=new VariableMemory(Math.max(4096,d.getHardware().getIntProperty("VMSizeInByte")));fRetenMap=new java.util.Hashtable<>();}
  public void start(){}public long getCurrentSimulationClockTime(){return now;}public long getCurrentSimulationTimerTime(){return now;}
  public int readIQMSimulationValue(int type,int number){return 0;}
 }
}

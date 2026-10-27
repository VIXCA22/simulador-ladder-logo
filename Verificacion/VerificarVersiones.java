import java.io.*;
import java.nio.file.*;
import java.util.*;
import CH.ifa.draw.framework.*;
import DE.siemens.ad.logo.app.AbstractLogoDrawingEditor;
import DE.siemens.ad.logo.util.*;
import DE.siemens.ad.logo.model.hardware.*;
import DE.siemens.ad.logo.model.visitor.build.object.*;
import DE.siemens.ad.pdraw.standard.LogoDrawing;

/** Pruebas de formatos propios, cifrados mediante los escritores oficiales. */
public class VerificarVersiones {
 static byte[] raw;static int target;static String password="";
 static class W83 extends CompilerObjectToFileLogo8Update3 {
  protected byte[] convertDiagramToBytes(AbstractLogoDrawingEditor e,ProgressListener p){return raw;}
  protected byte[] getHardwareID(AbstractLogoDrawingEditor e){return Util.int2Bytes(target);}
  protected String getPassword(AbstractLogoDrawingEditor e){return password;}
  byte[] save()throws IOException{return encrypt(null,null);}
 }
 static class W84 extends CompilerObjectToFileLogo8Update4 {
  protected byte[] convertDiagramToBytes(AbstractLogoDrawingEditor e,ProgressListener p){return raw;}
  protected byte[] getHardwareID(AbstractLogoDrawingEditor e){return Util.int2Bytes(target);}
  protected String getPassword(AbstractLogoDrawingEditor e){return password;}
  byte[] save()throws IOException{return encrypt(null,null);}
 }
 static class W9 extends CompilerObjectToFileLogo9 {
  protected byte[] convertDiagramToBytes(AbstractLogoDrawingEditor e,ProgressListener p){return raw;}
  protected byte[] getHardwareID(AbstractLogoDrawingEditor e){return Util.int2Bytes(target);}
  protected String getPassword(AbstractLogoDrawingEditor e){return password;}
  byte[] save()throws IOException{return encrypt(null,null);}
 }
 static byte[] serialize(LogoDrawing d,int major,int minor)throws Exception{
  List<Figure> figures=new ArrayList<>();FigureEnumeration it=d.figures();while(it.hasMoreElements())figures.add(it.nextFigure());
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){
   out.writeInt(major);out.writeInt(minor);out.writeInt(100);out.writeInt(figures.size());
   for(Figure f:figures)out.writeObject(f);out.writeObject(d);
  }return bytes.toByteArray();
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){try{
  RuntimeLogo.initialize();Path folder=Paths.get(args[1]);Files.createDirectories(folder);List<String> results=new ArrayList<>();
  int[][] cases={{8,0,0},{8,1,0},{8,2,0},{8,3,83},{8,4,84},{9,0,83},{9,0,84},{9,0,9}};
  for(int[] c:cases){
   LogoDrawing d=RuntimeLogo.read(Paths.get(args[0]));d.setFile(folder.resolve("circuito_propio.lld").toFile());
   d.getWiringDiagram().setHardware(c[2]==9?new Logo9():c[2]==84?new Logo8Update4():c[2]==83?new Logo8Update2():new Logo8());
   target=d.getWiringDiagram().getHardware().getID();raw=serialize(d,c[0],c[1]);
   byte[] data=c[2]==9?new W9().save():c[2]==84?new W84().save():c[2]==83?new W83().save():raw;
   String label="editor_"+c[0]+"_"+c[1]+"_destino_"+target;
   Path file=folder.resolve(label+".lld");Files.write(file,data);MotorLAD m=new MotorLAD(file);m.tick(100);
   check(!m.output(1),label+" Q1 inicial");m.input("I1",1);m.input("AI1",620);m.tick(100);
   check(m.output(1),label+" directo");check(!m.output(2),label+" TON prematuro");
   check(m.value(m.byAddress.get("AQ1"))==620,label+" analogico");
   for(int n=0;n<11;n++)m.tick(100);check(m.output(2),label+" TON");
   results.add("{\"case\":\""+label+"\",\"direct\":true,\"ton\":true,\"analog\":true}");
   System.out.println("PASS "+label);
  }
  password="clave_prueba_propia";Path protectedFile=folder.resolve("propio_protegido.lld");Files.write(protectedFile,new W9().save());
  boolean refused=false;try{RuntimeLogo.read(protectedFile);}catch(IOException e){refused=e.getMessage().contains("protegido");}
  check(refused,"Debe rechazar la copia propia con password");
  Files.writeString(folder.resolve("Compatibilidad_LOGO_8_9.json"),"{\"date\":\""+java.time.LocalDate.now(java.time.ZoneId.of("America/Costa_Rica"))+"\",\"fixtures\":\"Circuitos propios serializados y cifrados con escritores Siemens V9; no equivalen a todas las variantes creadas por editores antiguos\",\"cases\":["+String.join(",",results)+"],\"passwordRefused\":true}");
  System.out.println("PASS: 8 formatos, directo/TON/AI-AQ y rechazo de proteccion");System.exit(0);
 }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}

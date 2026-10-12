import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import DE.siemens.ad.logo.model.*;
import DE.siemens.ad.logo.model.block.*;
import DE.siemens.ad.logo.model.hardware.Logo8;

/** Entradas manipulables y cálculo con los bloques nativos del archivo cargado. */
final class MotorLAD {
 WiringDiagram diagram;RuntimeLogo.Clock clock;boolean first=true;
 Map<String,Double> inputs=new LinkedHashMap<>();Map<String,Block> byAddress=new LinkedHashMap<>();
 MotorLAD(Path file)throws Exception{
  diagram=RuntimeLogo.read(file).getWiringDiagram().getFup();
  for(Object item:diagram.getBlocks()){
   Block b=(Block)item;String type=b.getClass().getSimpleName();
   if(type.contains("Network")||type.contains("SInput")||type.contains("SAnalog")||type.contains("Asi")||type.contains("Message")||type.contains("Clock")||type.contains("Year")||type.contains("Astronomic"))throw new IllegalArgumentException("Bloque no admitido en el banco local: "+b.getNumberString()+" ("+type+"). Comunicación, calendario y pantallas requieren la simulación oficial.");
   byAddress.put(address(b),b);
  }
  for(Object item:diagram.getInputBlocks()){
   Block b=(Block)item;String prefix=b.getNumberPrefix();
   if(!(b.getSimulationParameter() instanceof AbstractInputSimulationParameter)||!(prefix.equals("I")||prefix.equals("AI")||prefix.equals("C")))throw new IllegalArgumentException("Entrada no admitida: "+address(b));
   inputs.put(address(b),0.0);
  }
  Field f=DE.siemens.ad.logo.model.Logo.class.getDeclaredField("fDownloadedWiringDiagram");f.setAccessible(true);f.set(diagram.getHardware(),diagram);
  if(diagram.getHardware() instanceof Logo8)((Logo8)diagram.getHardware()).prepareMemories(diagram);
  diagram.getHardware().getCompilerToHardware().compile(diagram,diagram.getHardware());
  clock=new RuntimeLogo.Clock(diagram);diagram.initParameter(clock,false);
 }
 static String address(Block b){return b.getNumberPrefix()+b.getNumber();}
 boolean analog(Block b){return b instanceof AnalogBlock||(b.getOutConnectorCount()>0&&b.getOutConnector(0) instanceof AnalogOutBlockConnector);}
 double value(Block b){return analog(b)?(b instanceof AnalogBlock?b.getAnalogValue():((AnalogOutBlockConnector)b.getOutConnector(0)).getAnalogValue()):(b.getBinaryValue()?1:0);}
 boolean output(int n){Block b=byAddress.get("Q"+n);return b!=null&&b.getBinaryValue();}
 void input(String a,double v){if(!inputs.containsKey(a))throw new IllegalArgumentException("El LAD no usa la entrada "+a);if(!Double.isFinite(v)||v< -32768||v>32767)throw new IllegalArgumentException("Entrada fuera de rango -32768..32767");inputs.put(a,a.startsWith("AI")?v:(v==0?0.0:1.0));}
 void inputIfUsed(int number,boolean state){String a="I"+number;if(number>0&&inputs.containsKey(a))inputs.put(a,state?1.0:0.0);}
 void tick(int ms)throws Exception{
  for(Object item:diagram.getInputBlocks()){
   Block b=(Block)item;AbstractInputSimulationParameter p=(AbstractInputSimulationParameter)b.getSimulationParameter();double v=inputs.get(address(b));
   if(p instanceof AnalogInputSimulationParameter)p.setInternAnalogValue(v);else p.setBinaryValue(v!=0);
  }
  clock.now+=ms;diagram.invalidateOutConnectors();Block boot=byAddress.get("M8");
  // AI en el editor depende de su ventana activa. En este banco la fuente virtual se
  // inyecta en el conector; los bloques de cálculo aguas abajo siguen siendo nativos.
  for(Object item:diagram.getInputBlocks()){
   Block b=(Block)item;if(b.getSimulationParameter() instanceof AnalogInputSimulationParameter){
    OutBlockConnector connector=b.getOutConnector(0);Field v=OutBlockConnector.class.getDeclaredField("fAnalogValue"),valid=OutBlockConnector.class.getDeclaredField("fValidDataAnalog");v.setAccessible(true);valid.setAccessible(true);v.setDouble(connector,inputs.get(address(b)));valid.setBoolean(connector,true);
   }
  }
  if(boot!=null){for(String name:new String[]{"fValue","fProcessImageValue"}){Field f=OutputBlock.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(boot,first);}boot.getOutConnector(0).setUseConstantValue(false);((BinaryOutBlockConnector)boot.getOutConnector(0)).setBinaryValue(first);}
  for(Object item:diagram.getBlocks())if(item instanceof OnDelayBlock||item instanceof OffDelayBlock)((Block)item).getBinaryValue();
  for(Object item:diagram.getOutputBlocks()){
   Block b=(Block)item;if(b==boot)continue;
   if(analog(b))b.getOutConnector(0).calculateAnalogValue();else ((OutputBlock)b).calculateBinaryValue();
  }
  for(Object item:diagram.getBlocks())if(item instanceof LatchingRelayBlock)((Block)item).getBinaryValue();
  for(Object item:diagram.getOutputBlocks())if(item!=boot)((OutputBlock)item).endCalculation();
  first=false;
 }
 List<Map<String,Object>> describe(Collection<?> items,boolean read){
  List<Map<String,Object>> out=new ArrayList<>();for(Object item:items){Block b=(Block)item;Map<String,Object> row=new LinkedHashMap<>();row.put("address",address(b));row.put("name",b.getUserDefinedName());row.put("type",b.getClass().getSimpleName());row.put("analog",analog(b));row.put("value",read?value(b):inputs.getOrDefault(address(b),0.0));row.put("parameter",String.valueOf(b.getParameter()));out.add(row);}return out;
 }
}

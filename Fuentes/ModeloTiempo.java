import java.nio.file.Path;
import java.util.*;
import DE.siemens.ad.logo.model.*;

/** Predicción causal: balances de agua + ejecución nativa del LAD. No ajusta un tiempo objetivo. */
final class ModeloTiempo {
 static final String INITIAL_SOURCE="Parámetros iniciales de la planta. Ajusta los caudales y la geometría a las condiciones de cada práctica.";
 static PlantaPortable.Config initialParameters(PlantaPortable.Config original){
  PlantaPortable.Config c=CalibracionCaudales.copy(original);
  c.fillRates=new double[]{.154,.154296,.164683};
  c.doseRates=new double[]{.054406,.067880,.053462};
  c.returnRates=new double[]{.060839,.06,.06};c.pump=.17;c.drain=.246053;c.flowUncertainty=.15;
  c.hydraulicSource=INITIAL_SOURCE;
  if(c.water!=null&&c.water.enabled)c.water=HidraulicaAgua.fromMeasuredRates(c);
  return c;
 }
 static Map<String,Object> run(Path file,PlantaPortable.Config source,double limit)throws Exception {
  PlantaPortable.Config c=CalibracionCaudales.copy(source);MotorLAD m=new MotorLAD(file);c.validate(m);
  if(c.endSignal.isEmpty())throw new IllegalArgumentException("Asigna FIN para calcular la duración del ciclo");
  PlantaPortable p=new PlantaPortable(c);List<Map<String,Object>> timers=new ArrayList<>();
  for(Block b:m.byAddress.values())if(b.getParameter() instanceof TimeProtectionParameter){
   TimeProtectionParameter t=(TimeProtectionParameter)b.getParameter();
   timers.add(ServidorPortable.map("block",MotorLAD.address(b),"type",b.getClass().getSimpleName(),"seconds",t.getParamTime().getCurrentValue().getMilliseconds()/1000.));
  }
  double pump=0,pumpFlow=0,mixer=0,flow=0,parallel=0;boolean reached=false,previous=false;List<Map<String,Object>> phases=new ArrayList<>();String prior="";
  for(int n=0;n<(int)Math.ceil(limit*10);n++){
   p.sensors(m);if(!c.start.isEmpty())m.input(c.start,n>=1&&n<=3?1:0);m.tick(100);p.flow(m,.1);
   if(p.command(m,8))pump+=.1;if(p.command(m,9))mixer+=.1;
   if(p.command(m,8)&&p.outflow[4]>1e-10)pumpFlow+=.1;
   int routes=0;for(int k=0;k<5;k++)if(p.outflow[k]>1e-10)routes++;
   if(routes>0)flow+=.1;if(routes>1)parallel+=.1;
   StringJoiner marks=new StringJoiner(" ");for(Block b:m.byAddress.values())if(b.getNumberPrefix().equals("M")&&m.value(b)!=0)marks.add(MotorLAD.address(b));
   if(!marks.toString().equals(prior)){phases.add(ServidorPortable.map("time",m.clock.now/1000.,"markers",marks.toString()));prior=marks.toString();}
   boolean end=m.value(m.byAddress.get(c.endSignal))!=0;if(n>0&&end&&!previous){reached=true;break;}previous=end;
  }
  double seconds=m.clock.now/1000.;
  return ServidorPortable.map("completed",reached,"seconds",seconds,"pumpSeconds",pump,"pumpFlowSeconds",pumpFlow,"mixerSeconds",mixer,"flowSeconds",flow,"noFlowSeconds",Math.max(0,seconds-flow),"parallelFlowSeconds",parallel,"timers",timers,"phases",phases,"tanks",p.tankRows(),"operations",p.movementRows(),"observations",new ArrayList<>(p.observations));
 }
 static PlantaPortable.Config vary(PlantaPortable.Config source,double factor){
  if(source.water!=null&&source.water.enabled){PlantaPortable.Config c=CalibracionCaudales.copy(source);c.water.conductanceFactor=Math.max(.1,Math.min(3,c.water.conductanceFactor*factor));return c;}
  PlantaPortable.Config c=CalibracionCaudales.scaled(source,factor);
  for(double[] rates:new double[][]{c.fillRates,c.doseRates,c.returnRates})if(rates!=null)for(int n=0;n<3;n++)rates[n]*=factor;
  // El límite es el rango soportado por el modelo, no una propiedad de la bomba real.
  c.pump=Math.min(1,Math.max(.001,c.pump));c.dose=Math.min(1,Math.max(.001,c.dose));c.drain=Math.min(1,Math.max(.001,c.drain));c.ret=Math.min(1,Math.max(.001,c.ret));
  for(double[] rates:new double[][]{c.fillRates,c.doseRates,c.returnRates})if(rates!=null)for(int n=0;n<3;n++)rates[n]=Math.min(1,Math.max(.001,rates[n]));
  return c;
 }
 static Map<String,Object> predict(Path file,PlantaPortable.Config source)throws Exception {
  Map<String,Object> nominal=run(file,source,3600);List<Map<String,Object>> scenarios=new ArrayList<>();
  if(source.flowUncertainty>0)for(double factor:new double[]{1-source.flowUncertainty,1+source.flowUncertainty}){
   Map<String,Object> trial=run(file,vary(source,factor),3600);scenarios.add(ServidorPortable.map("factor",factor,"completed",trial.get("completed"),"seconds",trial.get("seconds")));
  }
  boolean water=source.water!=null&&source.water.enabled;
  Map<String,Object> result=ServidorPortable.map("model",water?"Agua: Bernoulli + Darcy–Weisbach + LAD nativo":"Caudales constantes + LAD nativo","source",water?source.water.provenance:source.hydraulicSource,"config",source,"nominal",nominal,"scenarios",scenarios,"referenceSeconds",source.referenceSeconds,"differenceSeconds",(Boolean)nominal.get("completed")&&source.referenceSeconds>0?(Double)nominal.get("seconds")-source.referenceSeconds:null,"notice",(water?"Caudales variables con altura, viscosidad, pérdidas y curva de bomba. Geometría pendiente de medir; mayor detalle no demuestra mayor exactitud. ":"Caudales efectivos constantes. ")+"Los escenarios de conductancia/caudal no son un intervalo de confianza. No incluyen limpieza ni carga manual. FIN determina la ocupación simulada.");
  if(water){result.put("waterProperties",ServidorPortable.map("densityKgM3",HidraulicaAgua.water(source.water.temperatureC)[0],"viscosityPaS",HidraulicaAgua.water(source.water.temperatureC)[1],"gravityMs2",HidraulicaAgua.G));PlantaPortable.Config constant=CalibracionCaudales.copy(source);constant.water.enabled=false;result.put("constantComparison",run(file,constant,3600));}
  return result;
 }
}

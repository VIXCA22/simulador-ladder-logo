import java.nio.file.Path;
import java.util.*;

/** Ajusta caudales mediante ensayos aislados; nunca escala el reloj ni los temporizadores LAD. */
final class CalibracionCaudales {
 static PlantaPortable.Config copy(PlantaPortable.Config config){return ServidorPortable.JSON.fromJson(ServidorPortable.JSON.toJson(config),PlantaPortable.Config.class);}
 static PlantaPortable.Config scaled(PlantaPortable.Config source,double factor){PlantaPortable.Config c=copy(source);c.pump*=factor;c.dose*=factor;c.drain*=factor;c.ret*=factor;return c;}
 static double duration(Path file,PlantaPortable.Config config,double limit)throws Exception {
  MotorLAD m=new MotorLAD(file);config.validate(m);if(config.endSignal.isEmpty())throw new IllegalArgumentException("Asigna la señal FIN antes de calibrar el ciclo");
  PlantaPortable p=new PlantaPortable(config);p.sensors(m);m.tick(100);p.flow(m,.1);boolean previous=m.value(m.byAddress.get(config.endSignal))!=0;
  for(int n=0;n<(int)Math.ceil(limit*10);n++){
   p.sensors(m);if(!config.start.isEmpty())m.input(config.start,n<3?1:0);m.tick(100);p.flow(m,.1);
   boolean end=m.value(m.byAddress.get(config.endSignal))!=0;if(end&&!previous)return m.clock.now/1000.;previous=end;
  }
  return Double.NaN;
 }
 static Map<String,Object> suggest(Path file,PlantaPortable.Config source,double target)throws Exception {
  if(!Double.isFinite(target)||target<1||target>3600)throw new IllegalArgumentException("Referencia: de 1 a 3600 segundos");
  if(source.fillRates!=null||source.doseRates!=null||source.returnRates!=null||(source.water!=null&&source.water.enabled))throw new IllegalArgumentException("Los mapas por ramal y el modelo de agua calculan la duración de forma independiente; no se ajustan a un tiempo objetivo");
  source=copy(source);source.referenceSeconds=target;
  double limit=Math.min(10800,Math.max(600,target*3)),baseline=duration(file,source,limit);
  if(!Double.isFinite(baseline))throw new IllegalArgumentException("El programa no llegó a FIN con estos ajustes. Revisa los sensores, el mapa y las condiciones de arranque.");
  double min=.001,max=1000;for(double rate:new double[]{source.pump,source.dose,source.drain,source.ret}){min=Math.max(min,.001/rate);max=Math.min(max,1/rate);}
  double estimate=Math.max(min,Math.min(max,baseline/target));
  double low=Math.max(min,estimate*.5),high=Math.min(max,estimate*2),bestFactor=1,best=baseline;int trials=1;List<Map<String,Object>> observations=new ArrayList<>();observations.add(ServidorPortable.map("factor",1,"seconds",baseline));
  // La búsqueda conserva el mejor ensayo observado: una lógica LAD arbitraria puede no ser monótona.
  for(int i=0;i<18&&Math.abs(best-target)>.251;i++){
   double factor=i==0?low:i==1?high:(low+high)/2;
   double result=duration(file,scaled(source,factor),limit);trials++;
   observations.add(ServidorPortable.map("factor",factor,"seconds",Double.isFinite(result)?result:null));
   if(Double.isFinite(result)&&Math.abs(result-target)<Math.abs(best-target)){best=result;bestFactor=factor;}
   if(i<2)continue;
   if(!Double.isFinite(result)||result>target)low=factor;else high=factor;
  }
  PlantaPortable.Config candidate=scaled(source,bestFactor);candidate.referenceSeconds=target;
  // Los cambios de etapa pueden producir saltos de varias décimas. Afinar la descarga
  // permite acercarse al objetivo conservando intactos todos los temporizadores.
  if(Math.abs(best-target)>.051){
   PlantaPortable.Config base=copy(candidate);double slow=Math.max(.001/base.drain,.95),fast=Math.min(1/base.drain,1.05);
   for(int n=0;n<9&&Math.abs(best-target)>.051;n++){
    double factor=(slow+fast)/2;PlantaPortable.Config trial=copy(base);trial.drain=base.drain*factor;double result=duration(file,trial,limit);trials++;
    observations.add(ServidorPortable.map("factor",bestFactor,"drainFactor",factor,"seconds",Double.isFinite(result)?result:null));
    if(Double.isFinite(result)&&Math.abs(result-target)<Math.abs(best-target)){best=result;candidate=trial;}
    if(!Double.isFinite(result)||result>target)slow=factor;else fast=factor;
   }
  }
  return ServidorPortable.map("referenceSeconds",target,"baselineSeconds",baseline,"predictedSeconds",best,"differenceSeconds",best-target,"flowFactor",bestFactor,"trials",trials,"config",candidate,"observations",observations);
 }
}

import java.util.*;

/** Modelo de agua y mapa editable. La planta nunca sustituye la lógica del LAD. */
final class PlantaPortable {
 static final String[] SENSOR={"T1 alto","T1 bajo","T2 alto","T2 bajo","T3 alto","T3 bajo","Reactor alto","Reactor medio","Reactor bajo","Fuente disponible","STOP permitido","Retorno permitido"};
 static final String[] ACTOR={"V1 llenar T1","V2 dosificar T1","V3 llenar T2","V4 dosificar T2","V5 llenar T3","V6 dosificar T3","V7 retorno","V8 descargar reactor","Bomba","Agitador"};
 static final class Config {
  int[] sensors={1,2,3,4,5,6,7,8,9,10,12,14},actors={1,2,3,4,5,6,7,8,9,10};
  double pump=.10,dose=.08,drain=.12,ret=.06,tankHigh=1.45,tankLow=.08,reactorHigh=6.8,reactorMid=3.5,reactorLow=.12;
  double[] initial={0,0,0,0};double initialReserve=12;String start="";
  void validate(MotorLAD motor){
   if(sensors==null||sensors.length!=12||actors==null||actors.length!=10||initial==null||initial.length!=4)throw new IllegalArgumentException("Mapa incompleto");
   Set<Integer> unique=new HashSet<>();for(int n:sensors){if(n<0||n>64)throw new IllegalArgumentException("Entrada fuera de rango");if(n>0&&(!motor.inputs.containsKey("I"+n)||!unique.add(n)))throw new IllegalArgumentException("Entrada ausente o repetida I"+n);}
   unique.clear();for(int n:actors){if(n<0||n>64)throw new IllegalArgumentException("Salida fuera de rango");if(n>0&&(!motor.byAddress.containsKey("Q"+n)||!unique.add(n)))throw new IllegalArgumentException("Salida ausente o repetida Q"+n);}
   for(double n:new double[]{pump,dose,drain,ret})if(!Double.isFinite(n)||n<.001||n>1)throw new IllegalArgumentException("Caudal válido: 0.001 a 1 L/s");
   if(!(tankLow>=0&&tankHigh>tankLow&&tankHigh<=1.5&&reactorLow>=0&&reactorMid>reactorLow&&reactorHigh>reactorMid&&reactorHigh<=7))throw new IllegalArgumentException("Umbrales: bajo < medio < alto, dentro de la capacidad");
   for(int k=0;k<4;k++)if(!Double.isFinite(initial[k])||initial[k]<0||initial[k]>(k==3?7:1.5))throw new IllegalArgumentException("Volumen inicial fuera de capacidad");
   if(!Double.isFinite(initialReserve)||initialReserve<0||initialReserve>70)throw new IllegalArgumentException("Reserva inicial: 0 a 70 L");
   if(start==null||(!start.isEmpty()&&(!motor.inputs.containsKey(start)||start.startsWith("AI"))))throw new IllegalArgumentException("Selecciona una entrada digital o cursor disponible para START");
  }
 }
 Config config;double[] volumes;double reserve,total,milk;boolean stop=true,source=true,returnOK=true;Set<Integer> blocked=new LinkedHashSet<>();Set<String> observations=new LinkedHashSet<>();
 PlantaPortable(Config c){config=c;volumes=c.initial.clone();reserve=c.initialReserve;total=reserve;for(double n:volumes)total+=n;}
 void sensors(MotorLAD motor){
  boolean[] values={volumes[0]>=config.tankHigh,volumes[0]>=config.tankLow,volumes[1]>=config.tankHigh,volumes[1]>=config.tankLow,volumes[2]>=config.tankHigh,volumes[2]>=config.tankLow,volumes[3]>=config.reactorHigh,volumes[3]>=config.reactorMid,volumes[3]>=config.reactorLow,reserve>.5&&source,stop,returnOK};
  for(int n=0;n<values.length;n++)motor.inputIfUsed(config.sensors[n],values[n]);
 }
 boolean command(MotorLAD m,int role){return config.actors[role]>0&&m.output(config.actors[role]);}
 boolean valve(MotorLAD m,int role){return command(m,role)&&!blocked.contains(role);}
 void flow(MotorLAD m,double dt){
  int paths=0;for(int k=0;k<3;k++)if(valve(m,2*k))paths++;
  if(command(m,8)){
   if(paths==0)observations.add("Bomba comandada sin ruta de llenado abierta.");
   if(!source||reserve<=.5)observations.add("Bomba comandada sin fuente disponible.");
   if(paths>0&&source)for(int k=0;k<3;k++)if(valve(m,2*k)){
    double intended=Math.min(config.pump*dt/paths,reserve);if(intended>1.5-volumes[k]+1e-8)observations.add("Intento de sobrellenado T"+(k+1)+"; el modelo limita la capacidad.");double v=Math.max(0,Math.min(intended,1.5-volumes[k]));volumes[k]+=v;reserve-=v;
   }
  }
  for(int k=0;k<3;k++)if(valve(m,2*k+1)){
   if(command(m,2*k))observations.add("Llenado y dosificación simultáneos en T"+(k+1)+".");
   double intended=Math.min(config.dose*dt,volumes[k]);if(intended>7-volumes[3]+1e-8)observations.add("Intento de sobrellenado del reactor.");double v=Math.max(0,Math.min(intended,7-volumes[3]));volumes[k]-=v;volumes[3]+=v;if(k==0)milk+=v;
  }
  if(valve(m,7)){double v=Math.min(config.drain*dt,volumes[3]);v=Math.max(0,Math.min(v,70-reserve));volumes[3]-=v;reserve+=v;if(command(m,9))observations.add("Agitador comandado durante descarga del reactor.");}
  if(valve(m,6)&&!command(m,8))for(int k=0;k<3;k++)if(valve(m,2*k)){double v=Math.min(config.ret*dt,volumes[k]);v=Math.max(0,Math.min(v,70-reserve));volumes[k]-=v;reserve+=v;}
  if(command(m,9)&&volumes[3]<.005)observations.add("Agitador comandado con reactor vacío.");
  if(!stop)for(int n=0;n<10;n++)if(command(m,n)){observations.add("STOP del modelo no ha cortado todas las órdenes del LAD.");break;}
  double sum=reserve;for(double v:volumes)sum+=v;if(Math.abs(sum-total)>1e-7)throw new IllegalStateException("Balance de agua alterado");
 }
 static Config defaults(MotorLAD motor){Config c=new Config();for(int n=0;n<c.sensors.length;n++)if(!motor.inputs.containsKey("I"+c.sensors[n]))c.sensors[n]=0;for(int n=0;n<c.actors.length;n++)if(!motor.byAddress.containsKey("Q"+c.actors[n]))c.actors[n]=0;c.start=motor.inputs.containsKey("C1")?"C1":motor.inputs.containsKey("I11")?"I11":"";return c;}
}

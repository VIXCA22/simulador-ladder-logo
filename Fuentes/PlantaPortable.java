import java.util.*;

/** Modelo de agua y mapa editable. La planta nunca sustituye la lógica del LAD. */
final class PlantaPortable {
 static final String[] SENSOR={"T1 alto","T1 bajo","T2 alto","T2 bajo","T3 alto","T3 bajo","Reactor alto","Reactor medio","Reactor bajo","Fuente disponible","STOP permitido","Retorno permitido"};
 static final String[] ACTOR={"V1 llenar T1","V2 dosificar T1","V3 llenar T2","V4 dosificar T2","V5 llenar T3","V6 dosificar T3","V7 retorno","V8 descargar reactor","Bomba","Agitador"};
 static final class Config {
  int[] sensors={1,2,3,4,5,6,7,8,9,10,12,14},actors={1,2,3,4,5,6,7,8,9,10};
  double pump=.10,dose=.08,drain=.12,ret=.06,tankHigh=1.45,tankLow=.08,reactorHigh=6.8,reactorMid=3.5,reactorLow=.12;
  double[] initial={0,0,0,0};double initialReserve=12;String start="",endSignal="";boolean autoStop=true;
  String[] names={"Tanque 1","Tanque 2","Tanque 3","Mezclador","Reserva"};double[] levels;
  double referenceSeconds=0;
  HidraulicaAgua.Config water;
  // Caudales efectivos por ramal. null conserva mapas anteriores con caudal común.
  double[] fillRates,doseRates,returnRates;double flowUncertainty=.15;
  String hydraulicSource="Parámetros configurados; pendientes de medición en planta";
  double fillRate(int n){return fillRates==null?pump:fillRates[n];}
  double doseRate(int n){return doseRates==null?dose:doseRates[n];}
  double returnRate(int n){return returnRates==null?ret:returnRates[n];}
  double level(int n){return levels!=null?levels[n]:n<6?(n%2==0?tankHigh:tankLow):n==6?reactorHigh:n==7?reactorMid:reactorLow;}
  String label(int n){return (n<3?"T"+(n+1)+" · ":"")+names[n];}
  void validate(MotorLAD motor){
   if(water!=null)water.validate();
   if(sensors==null||sensors.length!=12||actors==null||actors.length!=10||initial==null||initial.length!=4)throw new IllegalArgumentException("Mapa incompleto");
   Set<Integer> unique=new HashSet<>();for(int n:sensors){if(n<0||n>64)throw new IllegalArgumentException("Entrada fuera de rango");if(n>0&&(!motor.inputs.containsKey("I"+n)||!unique.add(n)))throw new IllegalArgumentException("Entrada ausente o repetida I"+n);}
   unique.clear();for(int n:actors){if(n<0||n>64)throw new IllegalArgumentException("Salida fuera de rango");if(n>0&&(!motor.byAddress.containsKey("Q"+n)||!unique.add(n)))throw new IllegalArgumentException("Salida ausente o repetida Q"+n);}
   for(double n:new double[]{pump,dose,drain,ret})if(!Double.isFinite(n)||n<.001||n>1)throw new IllegalArgumentException("Caudal válido: 0.001 a 1 L/s");
   for(double[] rates:new double[][]{fillRates,doseRates,returnRates})if(rates!=null){if(rates.length!=3)throw new IllegalArgumentException("Se necesitan tres caudales por ruta");for(double q:rates)if(!Double.isFinite(q)||q<.001||q>1)throw new IllegalArgumentException("Caudal por ramal: 0.001 a 1 L/s");}
   if(!Double.isFinite(flowUncertainty)||flowUncertainty<0||flowUncertainty>.5)throw new IllegalArgumentException("Variación de caudal: 0 a 50 %");
   if(hydraulicSource==null||hydraulicSource.length()>500)throw new IllegalArgumentException("Procedencia de caudales no válida");
   if(levels==null)levels=new double[]{tankHigh,tankLow,tankHigh,tankLow,tankHigh,tankLow,reactorHigh,reactorMid,reactorLow};
   if(levels.length!=9)throw new IllegalArgumentException("Se necesitan nueve alturas de sensor");
   for(int n=0;n<9;n++)if(!Double.isFinite(levels[n])||levels[n]<0||levels[n]>(n<6?1.5:7))throw new IllegalArgumentException("Altura de sensor fuera de la capacidad del tanque");
   for(int n=0;n<3;n++)if(levels[n*2+1]>=levels[n*2])throw new IllegalArgumentException("En cada silo: sensor bajo < sensor alto");
   if(!(levels[8]<levels[7]&&levels[7]<levels[6]))throw new IllegalArgumentException("En el reactor: bajo < medio < alto");
   tankHigh=levels[0];tankLow=levels[1];reactorHigh=levels[6];reactorMid=levels[7];reactorLow=levels[8];
   if(names==null||names.length!=5)throw new IllegalArgumentException("Se necesitan cinco nombres de depósito");
   for(int n=0;n<5;n++){if(names[n]==null)throw new IllegalArgumentException("Nombre de depósito vacío");names[n]=names[n].strip();if(names[n].isEmpty()||names[n].codePointCount(0,names[n].length())>32||names[n].chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nombres: de 1 a 32 caracteres, sin saltos de línea");}
   if(!Double.isFinite(referenceSeconds)||(referenceSeconds!=0&&(referenceSeconds<1||referenceSeconds>3600)))throw new IllegalArgumentException("Duración medida: vacía o de 1 a 3600 segundos");
   for(int k=0;k<4;k++)if(!Double.isFinite(initial[k])||initial[k]<0||initial[k]>(k==3?7:1.5))throw new IllegalArgumentException("Volumen inicial fuera de capacidad");
   if(!Double.isFinite(initialReserve)||initialReserve<0||initialReserve>70)throw new IllegalArgumentException("Reserva inicial: 0 a 70 L");
   if(start==null||(!start.isEmpty()&&(!motor.inputs.containsKey(start)||start.startsWith("AI"))))throw new IllegalArgumentException("Selecciona una entrada digital o cursor disponible para START");
   if(endSignal==null)endSignal="";
   if(!endSignal.isEmpty()&&(!motor.byAddress.containsKey(endSignal)||motor.analog(motor.byAddress.get(endSignal))||motor.inputs.containsKey(endSignal)))throw new IllegalArgumentException("FIN debe ser un bloque digital, marca o salida del LAD");
  }
 }
 Config config;double[] volumes;double reserve,total,milk;
 final double[] fillSeconds=new double[5],drainSeconds=new double[5],inflow=new double[5],outflow=new double[5];
 final double[] filledLitres=new double[5],drainedLitres=new double[5],maxLitres=new double[5];
 static final String[] TANK_NAMES={"T1 · Tanque 1","T2 · Tanque 2","T3 · Tanque 3","Reactor","Reserva"};
 static final double[] CAPACITY={1.5,1.5,1.5,7,70};
 static final class Movement {
  int tank;boolean filling;double start,end,seconds,litres,initialLitres,finalLitres;
  Movement(int tank,boolean filling,double start,double initial){this.tank=tank;this.filling=filling;this.start=start;initialLitres=initial;}
 }
 final List<Movement> movements=new ArrayList<>();final Movement[][] activeMovements=new Movement[2][5];
 boolean stop=true,source=true,returnOK=true;Set<Integer> blocked=new LinkedHashSet<>();Set<String> observations=new LinkedHashSet<>();
 PlantaPortable(Config c){config=c;volumes=c.initial.clone();reserve=c.initialReserve;total=reserve;for(double n:volumes)total+=n;for(int n=0;n<5;n++)maxLitres[n]=n<4?volumes[n]:reserve;}
 boolean wet(int tank,int sensor){return volumes[tank]>1e-10&&volumes[tank]>=config.level(sensor);}
 void sensors(MotorLAD motor){
  boolean[] values={wet(0,0),wet(0,1),wet(1,2),wet(1,3),wet(2,4),wet(2,5),wet(3,6),wet(3,7),wet(3,8),reserve>.5&&source,stop,returnOK};
  for(int n=0;n<values.length;n++)motor.inputIfUsed(config.sensors[n],values[n]);
 }
 boolean command(MotorLAD m,int role){return config.actors[role]>0&&m.output(config.actors[role]);}
 boolean valve(MotorLAD m,int role){return command(m,role)&&!blocked.contains(role);}
 void transfer(int from,int to,double litres){if(litres>1e-10){outflow[from]+=litres;inflow[to]+=litres;}}
 void movement(int tank,int direction,double litres,double before,double after,double end,double dt){
  if(litres<=0){activeMovements[direction][tank]=null;return;}
  Movement m=activeMovements[direction][tank];if(m==null){m=new Movement(tank,direction==0,Math.max(0,end-dt),before);movements.add(m);activeMovements[direction][tank]=m;}
  m.end=end;m.seconds+=dt;m.litres+=litres;m.finalLitres=after;
 }
 List<Map<String,Object>> movementRows(){List<Map<String,Object>> rows=new ArrayList<>();for(Movement m:movements)rows.add(ServidorPortable.map("tankId",m.tank,"tank",config.label(m.tank),"direction",m.filling?"Llenado":"Vaciado","start",m.start,"end",m.end,"seconds",m.seconds,"litres",m.litres,"initialLitres",m.initialLitres,"finalLitres",m.finalLitres));return rows;}
 List<Map<String,Object>> tankRows(){List<Map<String,Object>> rows=new ArrayList<>();for(int n=0;n<5;n++)rows.add(ServidorPortable.map("tankId",n,"tank",config.label(n),"capacity",CAPACITY[n],"initialLitres",n<4?config.initial[n]:config.initialReserve,"finalLitres",n<4?volumes[n]:reserve,"maxLitres",maxLitres[n],"fillSeconds",fillSeconds[n],"drainSeconds",drainSeconds[n],"filledLitres",filledLitres[n],"drainedLitres",drainedLitres[n]));return rows;}
 String[] sensorRoles(){String[] labels=SENSOR.clone();for(int n=0;n<9;n++){int tank=n<6?n/2:3;labels[n]=config.label(tank)+" · "+(n<6?(n%2==0?"alto":"bajo"):n==6?"alto":n==7?"medio":"bajo");}return labels;}
 String[] actorRoles(){String[] labels=ACTOR.clone();for(int n=0;n<6;n++)labels[n]="V"+(n+1)+" "+(n%2==0?"llenar ":"dosificar ")+config.label(n/2);labels[7]="V8 descargar "+config.label(3);return labels;}
 void flow(MotorLAD m,double dt){
  double[] before={volumes[0],volumes[1],volumes[2],volumes[3],reserve};
  Arrays.fill(inflow,0);Arrays.fill(outflow,0);
  if(config.water!=null&&config.water.enabled)flowWater(m,dt);else{
  int paths=0;double demand=0;for(int k=0;k<3;k++)if(valve(m,2*k)){paths++;demand+=config.fillRate(k);}
  double share=demand>0?Math.min(1,config.pump/demand):0;
  if(command(m,8)){
   if(paths==0)observations.add("Bomba comandada sin ruta de llenado abierta.");
   if(!source||reserve<=.5)observations.add("Bomba comandada sin fuente disponible.");
   if(paths>0&&source)for(int k=0;k<3;k++)if(valve(m,2*k)){
    double intended=Math.min(config.fillRate(k)*share*dt,reserve);if(intended>1.5-volumes[k]+1e-8)observations.add("Intento de sobrellenado T"+(k+1)+"; el modelo limita la capacidad.");double v=Math.max(0,Math.min(intended,1.5-volumes[k]));volumes[k]+=v;reserve-=v;transfer(4,k,v);
   }
  }
  for(int k=0;k<3;k++)if(valve(m,2*k+1)){
   if(command(m,2*k))observations.add("Llenado y dosificación simultáneos en T"+(k+1)+".");
   double intended=Math.min(config.doseRate(k)*dt,volumes[k]);if(intended>7-volumes[3]+1e-8)observations.add("Intento de sobrellenado del reactor.");double v=Math.max(0,Math.min(intended,7-volumes[3]));volumes[k]-=v;volumes[3]+=v;transfer(k,3,v);if(k==0)milk+=v;
  }
  if(valve(m,7)){double v=Math.min(config.drain*dt,volumes[3]);v=Math.max(0,Math.min(v,70-reserve));volumes[3]-=v;reserve+=v;transfer(3,4,v);if(command(m,9))observations.add("Agitador comandado durante descarga del reactor.");}
  if(valve(m,6)&&!command(m,8))for(int k=0;k<3;k++)if(valve(m,2*k)){double v=Math.min(config.returnRate(k)*dt,volumes[k]);v=Math.max(0,Math.min(v,70-reserve));volumes[k]-=v;reserve+=v;transfer(k,4,v);}
  }
  for(int k=0;k<5;k++){double after=k<4?volumes[k]:reserve;maxLitres[k]=Math.max(maxLitres[k],after);filledLitres[k]+=inflow[k];drainedLitres[k]+=outflow[k];movement(k,0,inflow[k],before[k],after,m.clock.now/1000.,dt);movement(k,1,outflow[k],before[k],after,m.clock.now/1000.,dt);if(inflow[k]>0)fillSeconds[k]+=dt;if(outflow[k]>0)drainSeconds[k]+=dt;inflow[k]/=dt;outflow[k]/=dt;}
  if(command(m,9)&&volumes[3]<.005)observations.add("Agitador comandado con reactor vacío.");
  if(!stop)for(int n=0;n<10;n++)if(command(m,n)){observations.add("STOP del modelo no ha cortado todas las órdenes del LAD.");break;}
  double sum=reserve;for(double v:volumes)sum+=v;if(Math.abs(sum-total)>1e-7)throw new IllegalStateException("Balance de agua alterado");
 }
 void flowWater(MotorLAD m,double dt){
  boolean pumping=command(m,8)&&source;boolean[] open=new boolean[10];
  for(int k=0;k<3;k++){open[k]=valve(m,2*k);open[3+k]=valve(m,2*k+1);open[7+k]=!command(m,8)&&valve(m,6)&&valve(m,2*k);}
  for(int k=0;k<3;k++)if(open[k]&&open[3+k])observations.add("Llenado y dosificación simultáneos en T"+(k+1)+".");
  open[6]=valve(m,7);if(command(m,8)&&!open[0]&&!open[1]&&!open[2])observations.add("Bomba comandada sin ruta de llenado abierta.");
  if(command(m,8)&&(!source||reserve<=.5))observations.add("Bomba comandada sin fuente disponible.");
  if(command(m,9)&&open[6])observations.add("Agitador comandado durante descarga del reactor.");
  // Subpasos hidráulicos de 20 ms; las órdenes del LAD se mantienen durante su scan de 100 ms.
  int steps=Math.max(1,(int)Math.ceil(dt/.02));double sub=dt/steps;
  for(int n=0;n<steps;n++){
   double[] litres={volumes[0],volumes[1],volumes[2],volumes[3],reserve};double[] q=HidraulicaAgua.rates(config.water,litres,open,pumping);
   for(int r=3;r<10;r++)if(open[r]&&litres[HidraulicaAgua.FROM[r]]>1e-8&&HidraulicaAgua.head(config.water,r,litres)<=0)observations.add(HidraulicaAgua.ROUTE[r]+": sin carga positiva por gravedad; revisar cotas y nivel del receptor.");
   for(int r=0;r<10;r++)if(open[r]){int from=HidraulicaAgua.FROM[r],to=HidraulicaAgua.TO[r];double v=Math.max(0,Math.min(q[r]*sub,Math.min(litres[from],CAPACITY[to]-litres[to])));litres[from]-=v;litres[to]+=v;transfer(from,to,v);if(r==3)milk+=v;}
   for(int k=0;k<4;k++)volumes[k]=litres[k];reserve=litres[4];
  }
 }
 static String detectEnd(MotorLAD motor){
  List<String> named=new ArrayList<>(),terminal=new ArrayList<>();
  for(DE.siemens.ad.logo.model.Block b:motor.byAddress.values()){
   if(motor.inputs.containsKey(MotorLAD.address(b))||motor.analog(b))continue;
   String label=Objects.toString(b.getUserDefinedName(),"").trim().toUpperCase(Locale.ROOT);
   if(label.matches("(FIN|FINAL|DONE|COMPLETED|CICLO_FIN|FIN_CICLO)([_ ].*)?"))named.add(MotorLAD.address(b));
   // Una etapa final sin etiqueta sólo se reconoce si su marca reinicia otra etapa RS.
   if(b instanceof DE.siemens.ad.logo.model.block.LatchingRelayBlock&&b.getInConnector(0).isConnected()&&!b.getInConnector(1).isConnected()){
    for(Object o:b.getViaOutConnectorConnectedBlocks()){
     DE.siemens.ad.logo.model.Block mark=(DE.siemens.ad.logo.model.Block)o;
     if(!mark.getNumberPrefix().equals("M")||mark.getNumber()==8)continue;
     for(DE.siemens.ad.logo.model.Block other:motor.byAddress.values())if(other instanceof DE.siemens.ad.logo.model.block.LatchingRelayBlock&&other.getInConnector(1).getLinkedBlock()==mark){terminal.add(MotorLAD.address(mark));break;}
    }
   }
  }
  return named.size()==1?named.get(0):named.isEmpty()&&terminal.size()==1?terminal.get(0):"";
 }
 static Config defaults(MotorLAD motor){Config c=new Config();for(int n=0;n<c.sensors.length;n++)if(!motor.inputs.containsKey("I"+c.sensors[n]))c.sensors[n]=0;for(int n=0;n<c.actors.length;n++)if(!motor.byAddress.containsKey("Q"+c.actors[n]))c.actors[n]=0;c.start=motor.inputs.containsKey("C1")?"C1":motor.inputs.containsKey("I11")?"I11":"";c.endSignal=detectEnd(motor);return ModeloTiempo.initialParameters(c);}
}

import java.nio.file.*;
import java.util.*;
public final class VerificarAgua {
 static final class Commands {
  MotorLAD motor;
  Commands(Path file)throws Exception{motor=new MotorLAD(file);for(int n=1;n<=10;n++)motor.byAddress.put("Q"+n,new DE.siemens.ad.logo.model.block.OutputBlock(motor.diagram));}
  void set(Integer... values){Set<Integer> on=new HashSet<>(Arrays.asList(values));for(int n=1;n<=10;n++)((DE.siemens.ad.logo.model.block.OutputBlock)motor.byAddress.get("Q"+n)).setBinaryValue(on.contains(n));}
 }
 static final List<String> checks=new ArrayList<>();
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks.add(s);System.out.println("PASS "+s);}
 static boolean near(double a,double b,double eps){return Math.abs(a-b)<eps;}
 public static void main(String[] args){try{
  Path root=Paths.get(args[0]);HidraulicaAgua.Config c=new HidraulicaAgua.Config();c.validate();HidraulicaAgua.Pipe p=new HidraulicaAgua.Pipe();p.lengthM=0;p.minorK=0;
  double area=Math.PI*Math.pow(p.diameterMm*.001,2)/4;
  check(near(HidraulicaAgua.flow(c,p,1),area*Math.sqrt(2*HidraulicaAgua.G)*1000,1e-7),"Sin pérdidas recupera Torricelli en unidades SI");
  check(near(HidraulicaAgua.flow(c,p,.25)/HidraulicaAgua.flow(c,p,1),.5,1e-7),"Un cuarto de altura produce la mitad de caudal ideal");
  check(HidraulicaAgua.flow(c,p,0)==0&&HidraulicaAgua.flow(c,p,-1)==0,"Sin carga positiva no se inventa flujo ni reflujo");
  check(near(HidraulicaAgua.water(20)[0],998.2,1e-8)&&near(HidraulicaAgua.water(20)[1],.001002,1e-10),"Propiedades tabuladas de agua a 20 °C");
  p.lengthM=1;p.diameterMm=2;double h=.00001;double[] water=HidraulicaAgua.water(20);double a=32*water[1]*p.lengthM/(water[0]*HidraulicaAgua.G*Math.pow(p.diameterMm*.001,2)),b=1/(2*HidraulicaAgua.G);double v=2*h/(a+Math.sqrt(a*a+4*b*h)),expected=v*Math.PI*Math.pow(p.diameterMm*.001,2)/4*1000;
  check(near(HidraulicaAgua.flow(c,p,h),expected,expected*1e-4),"Régimen laminar reproduce Poiseuille con energía de salida");
  p=new HidraulicaAgua.Pipe();double q=HidraulicaAgua.flow(c,p,.5);p.lengthM*=2;check(HidraulicaAgua.flow(c,p,.5)<q,"Mayor longitud reduce el caudal");
  p=new HidraulicaAgua.Pipe();q=HidraulicaAgua.flow(c,p,.5);p.minorK+=10;check(HidraulicaAgua.flow(c,p,.5)<q,"Más pérdidas en válvulas reducen el caudal");
  p=new HidraulicaAgua.Pipe();q=HidraulicaAgua.flow(c,p,.5);p.diameterMm=10;check(HidraulicaAgua.flow(c,p,.5)>q,"Mayor diámetro interior aumenta el caudal");
  p=new HidraulicaAgua.Pipe();c.temperatureC=5;q=HidraulicaAgua.flow(c,p,.5);c.temperatureC=60;check(HidraulicaAgua.flow(c,p,.5)>q,"La viscosidad del agua fría aumenta las pérdidas");c.temperatureC=20;
  double[] litres={.75,.75,.75,3.5,12};c.pipes[3].outletM=.45;double head=HidraulicaAgua.head(c,3,litres);litres[3]=6;check(HidraulicaAgua.head(c,3,litres)<head,"La contrapresión del receptor sumergido reduce la carga");
  RuntimeLogo.initialize();PlantaPortable.Config pc=ModeloTiempo.initialParameters(new PlantaPortable.Config());pc.water=HidraulicaAgua.fromMeasuredRates(pc);c=pc.water;
  litres=new double[]{.75,.75,.75,3.5,12};boolean[] open=new boolean[10];open[0]=true;double[] rates=HidraulicaAgua.rates(c,litres,open,true);check(near(rates[0],pc.fillRate(0),.00001),"K preparado reproduce el aforo individual en su altura de referencia");
  open[1]=true;rates=HidraulicaAgua.rates(c,litres,open,true);double total=rates[0]+rates[1],hp=HidraulicaAgua.pumpHead(c,total);
  for(int k=0;k<2;k++){p=c.pipes[k];double vv=rates[k]*.001/(Math.PI*Math.pow(p.diameterMm*.001,2)/4);check(near(HidraulicaAgua.loss(c,p,vv),hp+HidraulicaAgua.head(c,k,litres),.00001),"Ramal "+k+" comparte el punto de presión de la bomba");}
  check(rates[0]<pc.fillRate(0),"Abrir otro ramal cambia el punto de trabajo de la bomba");
  Commands cmd=new Commands(root.resolve("Ejemplos/Banco_digital_analogico.lld"));cmd.set(8);pc=new PlantaPortable.Config();pc.initial=new double[]{0,0,0,7};pc.water=new HidraulicaAgua.Config();pc.water.bottomsM[3]=0;pc.water.bottomsM[4]=-1;pc.water.heightsM[4]=.1;p=pc.water.pipes[6];p.outletM=0;p.lengthM=0;p.minorK=0;
  PlantaPortable plant=new PlantaPortable(pc);double seconds=0;while(plant.volumes[3]>.001&&seconds<120){plant.flow(cmd.motor,.02);seconds+=.02;}
  double analytic=2*(.007/.35)/(Math.PI*Math.pow(p.diameterMm*.001,2)/4*Math.sqrt(2*HidraulicaAgua.G))*(Math.sqrt(.35)-Math.sqrt(.35*.001/7));
  check(Math.abs(seconds-analytic)<.15,"Vaciado numérico concuerda con solución analítica de Torricelli");
  double mass=plant.reserve;for(double x:plant.volumes)mass+=x;check(near(mass,plant.total,1e-8),"El submodelo de gravedad conserva todo el volumen");
  c.pipes[0].minorK=Double.NaN;boolean rejected=false;try{c.validate();}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Parámetros físicos no finitos se rechazan");
  Files.writeString(root.resolve("Verificacion/Agua_unidad.json"),HistorialEnsayos.JSON.toJson(ServidorPortable.map("checks",checks,"passed",checks.size(),"torricelliAnalyticSeconds",analytic,"torricelliNumericSeconds",seconds)));System.exit(0);
 }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}

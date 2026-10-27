import java.util.*;

/** Agua incompresible en depósitos ventilados; Bernoulli, Darcy y pérdidas locales. SI interno. */
final class HidraulicaAgua {
 static final double G=9.80665;
 static final int[] FROM={4,4,4,0,1,2,3,0,1,2},TO={0,1,2,3,3,3,4,4,4,4};
 static final String[] ROUTE={"Llenado T1","Llenado T2","Llenado T3","Dosis T1","Dosis T2","Dosis T3","Descarga reactor","Retorno T1","Retorno T2","Retorno T3"};
 static final class Pipe {
  double diameterMm=8,lengthM=.8,roughnessMm=.0015,minorK=5,outletM=.82;
 }
 static final class Config {
  boolean enabled=true;double temperatureC=20,pumpHeadM=7,pumpFreeLs=2.5,conductanceFactor=1;
  double[] heightsM={.30,.30,.30,.35,.35},bottomsM={1.10,1.10,1.10,.45,0};
  Pipe[] pipes=new Pipe[10];
  String provenance="Geometría provisional sin medir. Diámetros interiores supuestos, no equivalentes garantizados de DN. Curva de bomba parabólica supuesta a partir de máximos de catálogo.";
  Config(){for(int n=0;n<10;n++){pipes[n]=new Pipe();pipes[n].outletM=n<3?1.20:n<6?.82:.37;}pipes[6].diameterMm=20;pipes[6].lengthM=1;}
  void validate(){
   range(temperatureC,5,60,"Temperatura de agua: 5 a 60 °C");range(pumpHeadM,.1,20,"Altura de bomba: 0.1 a 20 m");range(pumpFreeLs,.01,10,"Caudal libre de bomba: 0.01 a 10 L/s");range(conductanceFactor,.1,3,"Factor hidráulico fuera de rango");
   if(heightsM==null||bottomsM==null||heightsM.length!=5||bottomsM.length!=5||pipes==null||pipes.length!=10)throw new IllegalArgumentException("Geometría hidráulica incompleta");
   for(int n=0;n<5;n++){range(heightsM[n],.01,3,"Altura útil: 0.01 a 3 m");range(bottomsM[n],-3,5,"Cota del fondo: -3 a 5 m");}
   for(int n=0;n<10;n++){Pipe p=pipes[n];if(p==null)throw new IllegalArgumentException("Falta tubería "+n);range(p.diameterMm,1,50,"Diámetro interior: 1 a 50 mm");range(p.lengthM,0,20,"Longitud: 0 a 20 m");range(p.roughnessMm,0,p.diameterMm*.1,"Rugosidad fuera de rango");range(p.minorK,0,100000,"Coeficiente K: 0 a 100000");range(p.outletM,bottomsM[TO[n]],8,"Cota de salida inferior al fondo del receptor");}
   if(provenance==null||provenance.length()>1000)throw new IllegalArgumentException("Procedencia hidráulica no válida");
  }
 }
 static void range(double x,double a,double b,String message){if(!Double.isFinite(x)||x<a||x>b)throw new IllegalArgumentException(message);}
 // Interpolación de tabla de agua a presión atmosférica, 5–60 °C (Julien, tabla 1.3).
 static double[] water(double c){double[] t={5,10,15,20,25,30,40,50,60},rho={1000,999.7,999.1,998.2,997,995.7,992.2,988,983.2},mu={1.518,1.307,1.139,1.002,.890,.798,.653,.547,.466};int n=0;while(n<t.length-2&&c>t[n+1])n++;double a=(c-t[n])/(t[n+1]-t[n]);return new double[]{rho[n]+a*(rho[n+1]-rho[n]),(mu[n]+a*(mu[n+1]-mu[n]))*.001};}
 static double friction(double re,double relativeRoughness){
  if(re<=0)return 0;if(re<=2300)return 64/re;
  double r=Math.max(4000,re),x=-1.8*Math.log10(Math.pow(relativeRoughness/3.7,1.11)+6.9/r),turb=1/(x*x);
  return re>=4000?turb:(64./2300+(turb-64./2300)*(re-2300)/1700);
 }
 static double loss(Config c,Pipe p,double velocity){
  double[] props=water(c.temperatureC);return loss(c,p,velocity,props);
 }
 static double loss(Config c,Pipe p,double velocity,double[] props){
  if(velocity<=0)return 0;double d=p.diameterMm*.001;double re=props[0]*velocity*d/props[1];
  return (1+p.minorK+friction(re,p.roughnessMm/p.diameterMm)*p.lengthM/d)*velocity*velocity/(2*G);
 }
 static double flow(Config c,Pipe p,double head){
  if(head<=0)return 0;double low=0,high=Math.sqrt(2*G*head),d=p.diameterMm*.001;
  double[] props=water(c.temperatureC);for(int n=0;n<27;n++){double mid=(low+high)/2;if(loss(c,p,mid,props)>head)high=mid;else low=mid;}
  return Math.PI*d*d/4*(low+high)/2*1000*c.conductanceFactor;
 }
 static double surface(Config c,int tank,double litres){return c.bottomsM[tank]+c.heightsM[tank]*litres/PlantaPortable.CAPACITY[tank];}
 static double head(Config c,int route,double[] litres){return surface(c,FROM[route],litres[FROM[route]])-Math.max(c.pipes[route].outletM,surface(c,TO[route],litres[TO[route]]));}
 static double pumpHead(Config c,double totalLs){double q=totalLs/(c.pumpFreeLs*c.conductanceFactor);return Math.max(0,c.pumpHeadM*(1-q*q));}
 static double[] rates(Config c,double[] litres,boolean[] open,boolean pump){
  double[] q=new double[10];
  if(pump&&litres[4]>1e-10){
   // Una presión de colector común; las tres ramas comparten el punto de la curva.
   double low=0,high=c.pumpHeadM;
   for(int n=0;n<23;n++){double h=(low+high)/2,total=0;for(int k=0;k<3;k++)if(open[k]&&litres[k]<1.5-1e-10)total+=flow(c,c.pipes[k],h+head(c,k,litres));if(h>pumpHead(c,total))high=h;else low=h;}
   double h=(low+high)/2;for(int k=0;k<3;k++)if(open[k]&&litres[k]<1.5-1e-10)q[k]=flow(c,c.pipes[k],h+head(c,k,litres));
  }
  for(int k=3;k<10;k++)if(open[k]&&litres[FROM[k]]>1e-10&&litres[TO[k]]<PlantaPortable.CAPACITY[TO[k]]-1e-10)q[k]=flow(c,c.pipes[k],head(c,k,litres));
  return q;
 }
 static Config fromMeasuredRates(PlantaPortable.Config plant){
  Config c=plant.water==null?new Config():ServidorPortable.JSON.fromJson(ServidorPortable.JSON.toJson(plant.water),Config.class);c.enabled=true;c.conductanceFactor=1;c.validate();
  // Un aforo de cada ruta identifica K efectivo a media capacidad; no identifica geometría.
  double[] litres={.75,.75,.75,3.5,plant.initialReserve};
  List<String> constrained=new ArrayList<>();
  for(int n=0;n<10;n++){
   double q=n<3?plant.fillRate(n):n<6?plant.doseRate(n-3):n==6?plant.drain:plant.returnRate(n-7);
   double available=head(c,n,litres)+(n<3?pumpHead(c,q):0),d=c.pipes[n].diameterMm*.001,velocity=q*.001/(Math.PI*d*d/4),base=loss(c,c.pipes[n],velocity)-c.pipes[n].minorK*velocity*velocity/(2*G);
   double k=(available-base)*2*G/(velocity*velocity);
   if(k<0)constrained.add(ROUTE[n]);
   c.pipes[n].minorK=Math.max(0,k);
  }
  c.provenance="K efectivos calculados de caudales por ruta a media capacidad. Geometría y curva de bomba provisionales; no se ha identificado la instalación ni validado mejora frente al modelo constante."+(constrained.isEmpty()?"":" La geometría no permite reproducir los caudales de "+String.join(", ",constrained)+"; K limitado a cero. Revisar dimensiones.");c.validate();return c;
 }
 static List<Map<String,Object>> diagnostics(Config c,double[] litres){
  List<Map<String,Object>> result=new ArrayList<>();double[] props=water(c.temperatureC);
  for(int k=0;k<10;k++){double h=head(c,k,litres),q=flow(c,c.pipes[k],Math.max(0,h)),d=c.pipes[k].diameterMm*.001,velocity=q*.001/(Math.PI*d*d/4);result.add(ServidorPortable.map("route",ROUTE[k],"gravityHeadM",h,"gravityPressurePa",props[0]*G*h,"gravityLs",q,"reynolds",props[0]*velocity*d/props[1]));}return result;
 }
}

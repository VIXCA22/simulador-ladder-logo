"""Comprobación funcional contra una instancia de prueba; utiliza solo los ejemplos propios."""
import argparse, copy, json, urllib.request
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--base',default='http://127.0.0.1:8808')
parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
parser.add_argument('--output',type=Path)
args=parser.parse_args();checks=[]

def request(route,payload=None,file=None):
    headers={};data=None
    if file:
        data=file.read_bytes();headers={'Content-Type':'application/octet-stream','X-File-Name':file.name}
    elif payload is not None:
        data=json.dumps(payload).encode();headers={'Content-Type':'application/json'}
    with urllib.request.urlopen(urllib.request.Request(args.base+route,data,headers),timeout=90) as response:
        body=response.read()
        return json.loads(body) if 'application/json' in response.headers.get('Content-Type','') else body.decode('utf-8')

def action(cmd,**kwargs):return request('/api/action',dict(cmd=cmd,**kwargs))
def check(ok,name):
    assert ok,name
    checks.append(name);print('PASS',name,flush=True)

check(request('/api/info')['version']=='1.1.0','Motor de aplicación versión 1.1.0')
for name in ['Banco_digital_analogico.lld','Banco_LOGO9.lld']:
    s=request('/api/upload',file=args.root/'Ejemplos'/name)
    check(s['loaded'] and s['config']['referenceSeconds']==0,'Carga y configuración inicial: '+name)
    s=action('input',address='I1',value=1)
    check(next(q for q in s['outputs'] if q['address']=='Q1')['value']==1,'Contacto directo: '+name)
    s=action('input',address='AI1',value=620)
    check(next(q for q in s['outputs'] if q['address']=='AQ1')['value']==620,'Señal analógica: '+name)
    s=action('advance',seconds=1.1)
    check(next(q for q in s['outputs'] if q['address']=='Q2')['value']==1,'Temporizador nativo: '+name)

c=copy.deepcopy(s['config']);c['endSignal']='Q1';c['names'][0]='Tanque de ensayo'
s=action('config',config=c);s=action('input',address='I1',value=1)
check(s['stopped'] and s['completed'] and s['paused'],'STOP automático al detectar FIN')
record=request('/api/history/'+s['runId'])
check(record['status']=='Finalizado' and record['config']['names'][0]=='Tanque de ensayo','Historial con configuración del ensayo')
s=action('restart');check(not s['stopped'] and s['time']==.1,'Reinicio preparado en pausa')
action('mode',value='gunt');c['sensors']=[0]*12;c['start']='I1'
prediction=request('/api/model-time',dict(config=c,gravity=True))
check(prediction['nominal']['completed'] and prediction['config']['water']['enabled'],'Cálculo aislado con modelo de agua')
check(request('/api/state')['time']==.1,'La predicción conserva el ensayo abierto')
check('Modelo hidráulico y cálculo de tiempos' in request('/modelo-tiempo'),'Documentación técnica disponible')
check('Simulador de prácticas de automatización' in request('/guia'),'Guía disponible desde la aplicación')
request('/api/action',dict(cmd='unload'))
check(not request('/api/state')['loaded'],'Se puede quitar el programa y cargar otra práctica')
report=dict(version='1.1.0',passed=len(checks),checks=checks)
if args.output:args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('TOTAL',len(checks),flush=True)

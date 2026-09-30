"""Reproducible single-pointer owner scenarios, before/after on the same physical Pixel.
Video crops are for motion, never calibrated photometry. Keep all active frames, not
percentiles of a recording padded mostly with idle frames.
"""
import json, os, pathlib, subprocess, sys, time, xml.etree.ElementTree as ET, re
from PIL import Image, ImageDraw
ROOT = pathlib.Path(__file__).resolve().parent
ADB = [r'D:\DevSetup\Android\Sdk\platform-tools\adb.exe', '-s', os.environ.get('OWNER_SERIAL','28121FDH2006ZF')]
FF = r'C:\Users\shaya\AppData\Local\Programs\Stremio\ffmpeg.exe'
out = ROOT / sys.argv[1]
out.mkdir(parents=True, exist_ok=False)
def adb(*args): return subprocess.check_output(ADB + list(args))
size=adb('shell','wm','size').decode()
screen_w,screen_h=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
assert screen_h>screen_w, 'Portrait required'
def root_boxes(label):
    fg=adb('shell','dumpsys','activity','activities').decode()
    assert re.search(r'topResumedActivity=.*com.wexpa.vitals/',fg), 'Vitals must be foreground'
    adb('shell','uiautomator','dump','/sdcard/owner-motion.xml')
    xml=adb('shell','cat','/sdcard/owner-motion.xml')
    (out/f'{label}-ui.xml').write_bytes(xml)
    tree=ET.fromstring(xml)
    assert tree.get('rotation')=='0', 'Portrait rotation required'
    assert not any(n.get('text')=='Continue' for n in tree.iter('node')), 'Dismiss onboarding first'
    boxes=[]
    for n in tree.iter('node'):
        p=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
        if n.get('clickable')=='true' and len(p)==4 and p[1]>.82*screen_h and .15*screen_w<p[2]-p[0]<.23*screen_w and p[3]-p[1]>.07*screen_w:
            boxes.append(p)
    assert len(boxes)==5, f'Root tab bar unavailable: {boxes}'
    boxes.sort()
    assert max(p[1] for p in boxes)==min(p[1] for p in boxes)
    return boxes
boxes=root_boxes('initial')
xs=[(p[0]+p[2])/2 for p in boxes]; y=(boxes[0][1]+boxes[0][3])/2
offset=(xs[-1]-xs[0])/12
scenarios={
 'wide_offset': (2,[(0,xs[2]+offset),(400,xs[2]+offset),(1600,xs[3]+offset),(2100,xs[3]+offset)]),
 'hard_right': (0,[(0,xs[0]),(400,xs[0]),(580,screen_w-10)]),
 'hard_left': (4,[(0,xs[4]),(400,xs[4]),(580,10)]),
 'stop_end': (0,[(0,xs[0]),(400,xs[0]),(800,screen_w-10),(1400,screen_w-10)]),
}
if len(sys.argv)>2 and sys.argv[2]=='additional':
    scenarios={
      'slow_left':(4,[(0,xs[4]),(400,xs[4]),(1900,10),(2200,10)]),
      'slow_right':(0,[(0,xs[0]),(400,xs[0]),(1900,screen_w-10),(2200,screen_w-10)]),
      'stationary_overlap':(2,[(0,xs[2]),(400,xs[2]),(1000,xs[2]-offset*1.8),(1900,xs[2]-offset*1.8)]),
      'tap_short':(0,[(0,xs[1]),(55,xs[1])]),
      'tap_long':(0,[(0,xs[4]),(55,xs[4])]),
      'retarget':(0,[(0,xs[4]),(55,xs[4])]),
    }
if len(sys.argv)>2 and sys.argv[2]=='retarget':
    scenarios={'retarget':(0,[(0,xs[4]),(55,xs[4])])}
metadata={'device':ADB[-1],'screen':[screen_w,screen_h],'centres':xs,'y':y,'scenarios':{},'sampling':'30Hz resampling for silhouette inspection, not frame-time measurement'}
for name,(start,points) in scenarios.items():
    assert root_boxes(name)==boxes, 'Tab layout changed'
    adb('shell','input','tap',str(int(xs[start])),str(int(y))); time.sleep(.6)
    remote=f'/sdcard/owner_{name}.mp4'
    rec=subprocess.Popen(ADB+['shell','screenrecord','--bit-rate','12000000','--time-limit','5',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(.4)
    args=['shell','CLASSPATH=/data/local/tmp/owner-gesture.zip','app_process','/','OwnerGesture',str(y)]
    args += [f'{t},{x}' for t,x in points]
    if name=='retarget':
        args=['shell','CLASSPATH=/data/local/tmp/owner-gesture.zip','app_process','/','OwnerGesture','--retarget',str(y),str(xs[4]),str(xs[1])]
    eventlog=adb(*args).decode()
    stdout,stderr=rec.communicate(timeout=10)
    assert rec.returncode==0,(stdout,stderr)
    adb('pull',remote,str(out/f'{name}.mp4'))
    # Keep the complete first 3.5s at 30Hz, including all motion and recovery.
    frames=out/name; frames.mkdir(exist_ok=True)
    crop=f'crop={screen_w}:260:0:{int(y)-130},fps=30'
    subprocess.run([FF,'-v','error','-y','-i',str(out/f'{name}.mp4'),'-t','3.5','-vf',crop,str(frames/'%03d.png')],check=True)
    files=sorted(frames.glob('*.png'))
    # First page: every 3rd frame = 100ms, time labels retained. All 30Hz originals remain.
    chosen=files[::3]
    sheet=Image.new('RGB',(screen_w,280*len(chosen)),(10,10,10)); draw=ImageDraw.Draw(sheet)
    for j,f in enumerate(chosen):
        sheet.paste(Image.open(f),(0,j*280+20)); draw.text((5,j*280),f'{(int(f.stem)-1)/30:.3f}s',fill='white')
    sheet.save(out/f'{name}-sheet.png')
    metadata['scenarios'][name]={'keyframes_ms_x':points,'event_log':eventlog,'video':f'{name}.mp4','frames':len(files)}
    print(name,eventlog.strip(),flush=True)
    (out/'manifest.json').write_text(json.dumps(metadata,indent=2))
assert root_boxes('final')==boxes
(out/'manifest.json').write_text(json.dumps(metadata,indent=2))

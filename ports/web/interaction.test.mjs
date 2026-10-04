import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createCalmInteraction} from './interaction.mjs';
import {calmFeedback} from './core.mjs';

const near = (a,b,tolerance=1e-8) => assert.ok(Math.abs(a-b)<=tolerance, `${a} differs from ${b}`);
const options = {width:320,height:72,radius:36,x:20,y:20,viewportWidth:360,viewportHeight:112};

test('whole-bar extreme pulls stay below the original Phone T04 body-height bound', () => {
  // IMG_6756 at PTS 20819/600 and 29863/600: unoccluded bar186 -> 204..205px.
  // This is a ceiling, not a fitted trajectory: the reference contains no finger coordinates.
  const density=3, width=834/density, height=186/density, radius=93/density;
  let maximum=0;
  for(const press of [{}, {pressScale:1.05,pressGrowth:0}]) for(let degrees=0;degrees<360;degrees+=5) {
    const angle=degrees*Math.PI/180, motion=createCalmInteraction({width,height,radius,...press});
    motion.press(0,Math.cos(angle)*10000,Math.sin(angle)*10000);
    for(let frame=1;frame<=240;frame++) {
      const time=frame/120;
      if(frame===120)motion.release(time);
      const [, , c,d]=motion.sample(time).matrix;
      // Independent support function of a transformed capsule; no renderer's bounds helper.
      const bodyHeight=2*(Math.abs(c)*(width/2-radius)+radius*Math.hypot(c,d))*density;
      maximum=Math.max(maximum,bodyHeight);
      assert.ok(bodyHeight<=205,`bar exceeded the original extreme bound: ${bodyHeight}`);
    }
  }
  assert.ok(maximum>186,'test never exercised material growth');
  console.log(`PHONE_T04_BAR maximum=${maximum.toFixed(4)} reference=204..205`);
});

test('navigation press matches ordinary hold and stays gradual at viewport edges', () => {
  const motion=createCalmInteraction({width:278,height:62,pressScale:1.05,pressGrowth:0});
  motion.press(0);
  const early=motion.sample(.04),held=motion.sample(2);
  assert(early.press>0&&early.press<.2);
  near(held.matrix[0],1.05);near(held.matrix[3]*186,196,1);
  // A tight viewport changes the equilibrium, not the pace at which it is reached.
  const edge=createCalmInteraction({width:200,height:60,x:1,y:20,viewportWidth:202,viewportHeight:100,
    pressScale:1.05,pressGrowth:0});
  edge.press(0);const growing=edge.sample(.1);
  near(growing.matrix[0],1+.01*growing.press);
  assert(growing.matrix[0]<1.01);
  const partial=createCalmInteraction({width:200,height:60,x:1,y:-20,viewportWidth:202,viewportHeight:100,
    pressScale:1.05,pressGrowth:0});
  partial.press(0);const clipped=partial.sample(2);
  near(clipped.matrix[0],1.01);near(clipped.matrix[3],1.05);
  for(const bad of [{pressScale:NaN},{pressScale:.5},{pressScale:3},{pressGrowth:-1}])
    assert.throws(()=>createCalmInteraction({...options,...bad}),/Press scale/);
});

test('press is gradual, does not snap on release, and eventually stops requesting frames', () => {
  const motion=createCalmInteraction(options);
  assert.deepEqual(motion.press(0).matrix,[1,0,0,1]);
  const early=motion.sample(.04), later=motion.sample(.12), held=motion.sample(2);
  assert(early.press>0&&early.press<.2);assert(later.press>early.press&&later.press<.6);
  near(held.matrix[0],1.0125);near(held.matrix[3],1.03);assert.equal(held.active,false);
  const release=motion.release(2);assert.deepEqual(release.matrix,held.matrix);assert(release.active);
  const fading=motion.sample(2.1);assert(fading.press>0&&fading.press<held.press);
  assert.deepEqual(motion.sample(5),{matrix:[1,0,0,1],press:0,held:false,active:false});
});

test('independent frame cadence never advances a new input target into its past', () => {
  function run(hz) {
    const motion=createCalmInteraction(options), events=[];
    for(let i=0;i<=120;i++)events.push({time:i/120,type:'input'});
    for(let i=0;i<=hz;i++)events.push({time:i/hz,type:'frame'});
    events.sort((a,b)=>a.time-b.time||a.type.localeCompare(b.type));
    for(const {time,type} of events) {
      if(type==='input')motion.press(time,600*Math.sin(time*4),200*Math.cos(time*3));
      else motion.sample(time);
    }
    motion.release(1);return motion.sample(1.08);
  }
  const reference=run(120);
  for(const hz of [30,60,90,144]) {
    const actual=run(hz);actual.matrix.forEach((n,i)=>near(n,reference.matrix[i]));
    near(actual.press,reference.press);
  }
  const motion=createCalmInteraction(options);motion.press(0);motion.press(.1,300,0);
  const current=motion.sample(.11);assert.deepEqual(motion.sample(.09),current);
  assert.throws(()=>motion.press(.09),/monotonic/);
});

test('huge over-pulls have the same recovery as saturated pulls, with no hidden displacement', () => {
  const normal=createCalmInteraction(options),extreme=createCalmInteraction(options);
  normal.press(0,1e6,-1e6);extreme.press(0,Number.MAX_VALUE,-Number.MAX_VALUE);
  for(const time of [.05,.15,1,10]) {
    const a=normal.sample(time),b=extreme.sample(time);a.matrix.forEach((v,i)=>near(v,b.matrix[i]));
  }
  normal.release(10);extreme.release(10);
  for(const time of [10.1,10.2,11,20]) {
    const a=normal.sample(time),b=extreme.sample(time);a.matrix.forEach((v,i)=>near(v,b.matrix[i]));
  }
  assert.equal(extreme.sample(20).active,false);
});

test('idle hold resumes, re-grab retains continuity, and reduced motion or resize cancels stale gestures', () => {
  const motion=createCalmInteraction(options);motion.press(0);const held=motion.sample(20);
  motion.press(20,200,0);assert.notDeepEqual(motion.sample(20.1).matrix,held.matrix);
  const before=motion.release(20.1);assert.deepEqual(motion.press(20.1,-20,0).matrix,before.matrix);
  const reduced=motion.setReducedMotion(true,20.2);assert.deepEqual(reduced.matrix,[1,0,0,1]);
  assert.equal(motion.press(20.3,1e9,1e9).active,false);assert.deepEqual(motion.sample(21).matrix,[1,0,0,1]);
  motion.setReducedMotion(false,22);motion.press(22,200,0);motion.sample(22.2);
  const resized=motion.resize({width:240},22.2);assert.deepEqual(resized.matrix,[1,0,0,1]);assert(!resized.held);
  motion.press(22.3);assert(motion.sample(22.4).press>0);
  assert(motion.sample(1e308).matrix.every(Number.isFinite));
});

test('all screen edges and diagonal extremes stay inside the viewport without moving the centre', () => {
  for(const [w,h,r] of [[56,56,28],[320,72,36],[320,180,20]]) {
    for(const [x,y] of [[0,0],[400-w,0],[0,300-h],[400-w,300-h],[40,20]]) {
      if(x+w>400||y+h>300)continue;
      for(const [dx,dy] of [[1e6,0],[-1e6,0],[0,1e6],[0,-1e6],[1e6,1e6],[-1e6,-1e6]]) {
        const motion=createCalmInteraction({width:w,height:h,radius:r,x,y,viewportWidth:400,viewportHeight:300});
        motion.press(0,dx,dy);
        for(const time of [.03,.1,.3,1]) {
          const [a,b,c,d]=motion.sample(time).matrix, cx=x+w/2,cy=y+h/2;
          // Sample the actual rounded outline; independent of the controller's support-width solver.
          for(let k=0;k<64;k++) {
            const angle=k*Math.PI/32,cos=Math.cos(angle),sin=Math.sin(angle);
            const px=Math.sign(cos)*(w/2-r)+r*cos,py=Math.sign(sin)*(h/2-r)+r*sin;
            const tx=cx+a*px+b*py,ty=cy+c*px+d*py;
            assert(tx>=-1e-8&&tx<=400+1e-8&&ty>=-1e-8&&ty<=300+1e-8,`outline escaped: ${tx},${ty}`);
          }
          if(w>=2*h) {near(b,0);near(c,0);}
        }
      }
    }
  }
});

test('settled geometry preserves the existing portable contract for controls, pills and cards', () => {
  for(const [w,h,r] of [[56,56,28],[320,72,36],[320,180,20]]) {
    for(const [dx,dy] of [[0,0],[15,30],[-1e6,0],[1e6,-1e6]]) {
      const expected=calmFeedback(w,h,dx,dy,true,r), c=Math.cos(expected.angle),s=Math.sin(expected.angle);
      const motion=createCalmInteraction({width:w,height:h,radius:r});motion.press(0,dx,dy);
      const [a,b,e,d]=motion.sample(20).matrix;
      near(a,(expected.along*c*c+expected.across*s*s)*expected.pressX);
      near(d,(expected.along*s*s+expected.across*c*c)*expected.pressY);
      near(b,(expected.along-expected.across)*c*s*expected.pressY);
      near(e,(expected.along-expected.across)*c*s*expected.pressX);
    }
  }
  assert.throws(()=>createCalmInteraction({...options,width:0}),/dimensions/);
  assert.throws(()=>createCalmInteraction({...options,viewportHeight:undefined}),/viewport/);
  const motion=createCalmInteraction(options);
  assert.throws(()=>motion.press(0,NaN),/displacement/);assert.throws(()=>motion.sample(-1),/timestamps/);
});

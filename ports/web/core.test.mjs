import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lensSource, stepSpring, calmFeedback } from './core.mjs';

test('lens is continuous and monotone up to the boundary, all supported zooms', () => {
  for (const zoom of [1,1.25,1.6,2.5]) {
    let previous=-100;
    for(let x=-100;x<=100;x+=.1) {
      const mapped=lensSource(x,0,100,100,zoom).x;
      assert(mapped>=previous-1e-8); assert(mapped-previous<=.19); previous=mapped;
    }
    assert.equal(lensSource(100,0,100,100,zoom).x,100);
    const slope=(lensSource(100,0,100,100,zoom).x-lensSource(99.999,0,100,100,zoom).x)/.001;
    assert(Math.abs(slope-1)<.0001);
  }
});
test('spring is independent of frame partition and does not overshoot',()=>{
  const one=stepSpring(0,0,1,.25);
  let many={value:0,velocity:0};
  for(let i=0;i<30;i++) {many=stepSpring(many.value,many.velocity,1,1/120);assert(many.value<=1);}
  assert(Math.abs(one.value-many.value)<1e-10);
});
test('calm material strain grows gradually and remains bounded at screen extremes',()=>{
  const near=calmFeedback(56,56,56,0),far=calmFeedback(56,56,1e6,0);
  assert(near.along<1.03);assert(far.along<1.076);assert(far.along>near.along);
  assert(calmFeedback(320,180,1e6,0).along<=1+2/180);
  assert.deepEqual(calmFeedback(320,180,1e6,0,false),{pressX:1,pressY:1,along:1,across:1,angle:0});
});
test('invalid values fail explicitly',()=>{
  for(const zoom of [NaN,Infinity,.9,3]) assert.throws(()=>lensSource(1,1,100,100,zoom),RangeError);
  assert.throws(()=>calmFeedback(0,50,1,1),RangeError);
  assert.throws(()=>stepSpring(0,0,1,-1),RangeError);
});

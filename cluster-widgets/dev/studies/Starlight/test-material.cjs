const assert=require('assert/strict'),fs=require('fs'),R=require('./renderer');let count=0;const ok=v=>{assert(v);count++},near=(a,b)=>ok(Math.abs(a-b)<1e-7),eq=(a,b)=>{assert.equal(a,b);count++};
// Latest owner explicitly replaces source-frontier heat with speed-linked 120–200 ramp.
for(const v of [0,60,100,120])eq(R.speedHeat(v),0);near(R.speedHeat(140),.25);near(R.speedHeat(160),.5);eq(R.speedHeat(200),1);eq(R.speedHeat(300),1);for(const v of [NaN,undefined,Infinity])eq(R.speedHeat(v),0);
let maxSpeed=0;
for(const mode of ['city','motion','drive','accel','regen'])for(let i=0;i<=Math.round(R.duration(mode)*60);i++){
 const t=Math.min(R.duration(mode),i/60),s=R.sample(t,mode);ok(s.speed>=0&&s.speed<=200);ok(s.speedRaw>=0&&s.speedRaw<=200);ok(s.left>=0&&s.right>=0);ok(s.batteryMagnitudeKw<=100);near(780-s.leftLength,R.Scale.speedX(s.speedRaw));near(780-s.rightLength,R.Scale.batteryX(s.batteryMagnitudeKw));near(s.tipHeat,R.speedHeat(s.speedRaw));eq(s.regen,s.batteryPowerKw<0);if(s.batteryPowerKw===0)eq(s.right,0);maxSpeed=Math.max(maxSpeed,s.speedRaw);
}
eq(maxSpeed,200);
// Left glass follows speed even when traction is zero or battery recovers.
for(const batteryPowerKw of [-100,-20,0,20,100]){let s=R.functionalState({speedRaw:160,powerKw:0,tractionKw:0,batteryPowerKw});near(s.tipHeat,.5);ok(s.left>0);near(s.leftLength,780-R.Scale.speedX(160));}
const report={result:'PASS',assertions:count,public_modes_speed_bound:200,bound_provenance:'owner-requested preview constraint; not researched vehicle specification',red_ramp_kmh:[120,200],red_trigger:'explicit owner-requested speed ramp',source_frontier_replaced_by_speed:true,glass_material_preserved:true,battery_display_magnitude_kw:[0,100],battery_curve:'normalized actual Apex Contour regeneration geometry; both signs use one fixed magnitude scale',green_only_regen:true,physically_calibrated:false};fs.writeFileSync(__dirname+'/qa/material-validation.json',JSON.stringify(report,null,2));console.log(report);

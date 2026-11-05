/* Shared preview geometry. Speed layout requested by owner; battery geometry
   normalizes the actual Apex Contour regeneration display, not a vehicle rating. */
(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.ThemeScales=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){'use strict';
const clamp=(v,a,b)=>Math.max(a,Math.min(b,Number.isFinite(v)?v:a));
const speedValues=[0,20,40,50,60,80,100,120,140,160,200];
// Keep the urban range expanded in distance per km/h, while reserving enough
// foreground room for genuinely larger three-digit labels. The speed frontier
// consumes these same knots; labels are never nudged away from their value.
const speedPositions=[710,600,500,450,400,342,285,228.5,172.5,117,50];
const speedSizes=[26,28,30,31,32,33,34,35,36,38,40];
const batteryValues=[0,10,20,30,40,50,75,100];
const apexRegenDistances=[0,39,74,106,134,159,186,207.6];
const batteryPositions=apexRegenDistances.map(d=>710-660*d/207.6);
const originalX=[702.72,643.2,574.08,495.36,422.4,347.52,259.2,174.72,84.48],originalY=[318,324,331,339,352,361,371,384,394];
function interpolate(values,positions,v){v=clamp(v,values[0],values.at(-1));for(let i=0;i<values.length-1;i++)if(v<=values[i+1])return positions[i]+(positions[i+1]-positions[i])*(v-values[i])/(values[i+1]-values[i]);return positions.at(-1);}
function scaleY(x){return interpolate([...originalX].reverse(),[...originalY].reverse(),x);}
// The label plane follows the existing glass seam's perspective. Blade heights
// deliberately keep scaleY(), preserving the accepted tall pointed glass.
// angle is in radians and is consumed around the tick's own (x,y) anchor.
const speedLabelAngle=-Math.atan(.35);
const speedLabelY=x=>318+.35*(710-clamp(x,50,710));
const speedX=v=>interpolate(speedValues,speedPositions,v),batteryX=v=>interpolate(batteryValues,batteryPositions,Math.abs(v));
const speedRatio=v=>(710-speedX(v))/660,batteryRatio=v=>(710-batteryX(v))/660;
const leftTicks=speedValues.map((value,i)=>Object.freeze({value,x:speedPositions[i],y:speedLabelY(speedPositions[i]),size:speedSizes[i],angle:speedLabelAngle}));
const rightTicks=batteryValues.map((value,i)=>Object.freeze({value,x:1920-batteryPositions[i],y:speedLabelY(batteryPositions[i]),size:[26,30,34,36,38,40,42,44][i],angle:-speedLabelAngle}));
const speedLabel=Object.freeze({text:'VELOCIDADE · km/h',x:440,y:278,size:16});
return Object.freeze({speedValues,speedPositions,speedSizes,batteryValues,batteryPositions,apexRegenDistances,leftTicks,rightTicks,speedLabel,speedX,batteryX,speedRatio,batteryRatio,scaleY,speedLabelY,speedLabelAngle,interpolate});
});

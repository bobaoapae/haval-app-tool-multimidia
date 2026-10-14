// Build-only illustrative data for the catalog thumbnail, never part of app.html.
const fs = require('node:fs');
const path = require('node:path');
const { createCanvas, GlobalFonts } = require('@napi-rs/canvas');
const renderer = require('../src/renderer.js');
const data = require('../src/apex-data.js');

const root = path.resolve(__dirname, '..');
for (const [file, family] of Object.entries({
  'SpeedDigits.ttf': 'Speed Digits',
  'ScaleDigits.ttf': 'Scale Digits',
  'HeaderSans.ttf': 'Header Sans',
  'PanelSans.ttf': 'Panel Sans',
  'LabelSans.ttf': 'Label Sans',
})) GlobalFonts.registerFromPath(path.join(root, 'assets', file), family);

renderer.setCanvasFactory(createCanvas);
const readouts = data.decode({
  'car.basic.vehicle_speed': 86,
  'car.basic.engine_speed': 1800,
  'car.basic.gear_status': 2,
  'car.basic.total_odometer': 18426,
  'car.basic.inside_temp': 22,
  'car.basic.outside_temp': 27,
  'car.configure.default_temp_unit': 0,
  'car.drive_setting.drive_mode': 1,
  'car.ev_setting.power_model_config': 0,
  'car.basic.remain_fuel_percentage': 64,
  'car.ev_info.cur_battery_power_percentage': 82,
  'car.ev_info.fuel_mode_remain_odometer': 412,
  'car.ev_info.electric_mode_remain_odometer': 126,
  'car.ev_info.power_battery_voltage': 400,
  'car.ev_info.cur_charge_current': 80,
  'car.basic.cur_journey_odometer': 18.6,
  'car.basic.cur_journey_avg_fuel_consume': 7.1,
}, { clockText: '08:29', tankCapacityL: 55 });
const state = renderer.functionalState({
  speedRaw: readouts.speed,
  powerKw: readouts.powerKw,
  tractionKw: Math.max(0, readouts.powerKw),
  batteryPowerKw: readouts.powerKw,
});
state.speed = readouts.speed;
state.speedRaw = readouts.speed;
state.readouts = readouts;
const canvas = createCanvas(1920, 720);
renderer.renderPanel(canvas.getContext('2d'), 5, 'live', {
  state,
  starMotion: { speed: readouts.speed, travel: 100 },
});
const thumbnail = createCanvas(960, 360);
thumbnail.getContext('2d').drawImage(canvas, 0, 0, 960, 360);
fs.writeFileSync(path.join(root, 'thumbnail.png'), thumbnail.toBuffer('image/png'));
renderer.cleanup?.();
console.log('Starlight catalog thumbnail rendered from explicit build fixture.');

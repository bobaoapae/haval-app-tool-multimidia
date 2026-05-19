import { createStatusElement } from "./status.js";
import { stateManager, subscribe, setState } from '../../state.js';
import { createTemperatureElement } from "./acTemperature.js";
import { createFanElement } from "./fan.js";
import { createTempInfoElement } from "./infoTemperature.js";
//import { createImpulseAutoElement } from "./impulseAuto.js";
import { div, span, img } from "../../../utils/createElement.js";

var acON = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAABmUlEQVR4nN2US0oDQRCGSyVCQD2HBA8h6t5XVLxBjEHRC7hWFyq4CVMdFwlB5jRiFJWo4OMCEnxufumemkmi3ZPMuPOHhkmq6/+qX0X0v+VjmCpYiX4rwIxQOqbnpJJCnhSeugx/AvQ345EYi0mqHiLGUWSm0IgBNKL/GAe0g8F+Kj+UhHdiFAgYcAK0ocKamRtCYqWXGpp7mLTAuwGhKpgihQ+Jz9vN9WExngVQcKwOVkBQXFHiD1RGxpa8KuZnzr1UMQCdwzg38c6b11HBqQCK9iVSPEDLQ0k86rbkexM8wXhqgEJOAHe2FbyZoI9saoCPrMx5tSW3hD5KaVXFmHi82AA3Qs+lBjAmxOPaFqwLffMPgG3xqP4OKixHrUG3i6TyTYu5FI+866HdyoT1xACFDclt2h+alocFWeInMaYTmM+Qwpc8stlek/cjiIdS7HbpWFB5YM7Y7V1N0CH3Otr1BSlsmRtyjBEz9Lc+0PaeB+Z9tetQjDmzn22QazR7b4tLZWSIsUQKNWJcyWNsyXfN3Bbngf4XfQMPBJzW+8lHnAAAAABJRU5ErkJggg==";
var acOFF = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAABr0lEQVR4nN2US0oDQRCGR4OBgHoOEQ8h6t5XVLxBjCGiF8haXahnUILMKqsw9Xcgu1llIUaJEhV8XEDE52akpBqapHuSjLs0NEzP3/V/1dUPzxvq5vt+OgiCDT0GEHHXY9Z4TiJzIsoCeDYNOwEyflJKrQ6SdQrAiTYD0IwBNPU/IjoqlUqj/WR+LAGfAHJRFI24AGyolNqSuX+QWHNeqjZXSs126p0AI6k5AF8Su+wqTZqIXsQkZ5sDB0AgeQE8NhqNMVvwphhcuGqJGADHENEl6+bJM4PPJYO8dYlePED0gniUbeKD0KeSAmq12rQA7rtEIvpgMQzDTFJAGIYZmfNuA7yxWKlUJryErVqtTgrg1Qa4ZZGXmRRARDMCuLGJZRF3/gHYE4/TLhHAun4a+LkY1Nz3/RQRXcsmZ10X7U4mbCfIvigJtq0XTVaxIpO+Acz3a66UWiCiH45VSi32yuTQgBTiyiVlKWpzAPs9s+ErD+DAeK6viGiXT0i9Xh/nzt+8obrm2ryv51q3IAiWuJ6Ggau3e5bF1XizAKwBOAPQ4ssoF7LF//i0ODd0aNovv1MAVXvuRh4AAAAASUVORK5CYII=";

function createArcLabels(container, prefix, values, radius, startAngle, endAngle) {
  const angleStep = (endAngle - startAngle) / (values.length - 1);

  values.forEach((value, index) => {
    const angle = startAngle + (index * angleStep);
    const angleRad = angle * (Math.PI / 180);

    const label = document.createElement('div');
    label.className = 'ac-label';
    label.id = `${prefix}-label-${value}`;
    label.textContent = value;

    const x = radius * Math.cos(angleRad);
    const y = radius * Math.sin(angleRad);
    label.style.transform = `translate(${x}px, ${y}px)`;

    container.appendChild(label);
  });
}


export function createAcControlScreen() {
  var main = document.createElement('main');
  main.className = 'main-container';

  const container = document.createElement('div');
  container.className = 'ac-circle-container';

  const fanProgressRing = document.createElement('div');
  fanProgressRing.id = 'fan-progress-ring';
  fanProgressRing.className = 'progress-ring';

  const tempProgressRing = document.createElement('div');
  tempProgressRing.id = 'temp-progress-ring';
  tempProgressRing.className = 'progress-ring';

  container.appendChild(fanProgressRing);
  container.appendChild(tempProgressRing);

  var powerIcon = img({
    src: stateManager.get('power') === 1 ? acON : acOFF,
    className: 'w-32 h-32 ac-power-icon',
    id: 'ac-power-icon',
  });

  const divider = document.createElement('div');
  divider.className = 'ac-divider-line';
  const outerRing = document.createElement('div');
  outerRing.className = 'ac-outer-ring';
  const innerRingShadow = document.createElement('div');
  innerRingShadow.className = 'ac-inner-ring-shadow';
  const innerRing = document.createElement('div');
  innerRing.className = 'ac-inner-ring';

  const labelsContainer = document.createElement('div');
  labelsContainer.className = 'ac-labels-container';
  const labelRadius = 208;
  const fanValues = [1, 2, 3, 4, 5, 6, 7];
  createArcLabels(labelsContainer, 'fan', fanValues, labelRadius - 22, -170, -10);
  const tempValues = ['HI', 25, 24, 23, 22, 21, 20, 19, 18, 17, 'LO'];
  createArcLabels(labelsContainer, 'temp', tempValues, labelRadius - 26, 10, 170);

  const temperatureElement = createTemperatureElement();
  const fanElement = createFanElement();
  const statusElement = createStatusElement();
  const tempInfoElement = createTempInfoElement()

  //const impulseAutoElement = createImpulseAutoElement();

  container.appendChild(outerRing);
  container.appendChild(innerRingShadow);
  container.appendChild(innerRing);
  container.appendChild(divider);
  container.appendChild(powerIcon);
  container.appendChild(fanElement);
  container.appendChild(temperatureElement);
  container.appendChild(labelsContainer);
  container.appendChild(statusElement);
  container.appendChild(tempInfoElement);
  //container.appendChild(impulseAutoElement);
  main.appendChild(container);

  const updateActiveLabels = () => {
    let tempValue = stateManager.get('temp');
    if (tempValue == 32) tempValue = 'HI';
    else if (tempValue == 16) tempValue = 'LO';
  };

  setTimeout(() => {
    updateActiveLabels();
  }, 0);

  const onMountFuncs = [
    tempInfoElement.onMount,
    () => updateProgressRings(),
    () => {
      const unsubscribePower = subscribe('power', (newPower) => {
        const icon = document.getElementById('ac-power-icon');
        if (icon) {
          icon.src = newPower === 1 ? acON : acOFF;
        }
      });
      return unsubscribePower;
    },
  ].filter(Boolean);

  let cleanupFuncs = [];

  const onMount = () => {
    cleanupFuncs = onMountFuncs.map(fn => fn()).filter(Boolean);

    updateActiveLabels();

    const unsubscribeFan = subscribe('fan', updateActiveLabels);
    const unsubscribeTemp = subscribe('temp', updateActiveLabels);

    cleanupFuncs.push(unsubscribeFan, unsubscribeTemp);
  };

  const cleanup = () => {
    cleanupFuncs.forEach(fn => fn());
  };

  return { element: main, onMount, cleanup };
}

export function updateProgressRings() {
  const fanRing = document.getElementById('fan-progress-ring');
  const tempRing = document.getElementById('temp-progress-ring');

  const fanValues = 7;
  const currentFanIndex = stateManager.get('fan');
  const fanAngle = parseInt(currentFanIndex) * 180 / fanValues;
  if (fanRing) {
    fanRing.style.setProperty('--progress-angle', `${fanAngle}deg`);
  }

  const tempValues = 20;
  const currentTemp = stateManager.get('temp');
  const currentTempIndex = (currentTemp > 16 + (tempValues / 2) ? 10 : currentTemp - 16);
  const tempAngle = 360 - (2 * (parseFloat(currentTempIndex) * 180 / tempValues));
  if (tempRing) {
    tempRing.style.setProperty('--progress-angle', `${tempAngle}deg`);
  }
}


export class WarpTunnelAnimation {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.width = 452; // Fixed container size
        this.height = 452; // Fixed container size
        this.cx = this.width / 2;
        this.cy = this.height / 2;
        this.animationId = null;
        this.isRunning = false;

        // Configuration
        this.normalizedSpeed = 0;
        this.vignetteColor = 'rgba(0, 0, 0, 0.8)';
        this.config = {
            squareCount: 3,       // Number of tunnel "ribs"
            speed: 3,             // Speed of the tunnel (dynamic)
            rotationSpeed: 0.01,  // How fast the tunnel spins (dynamic)
            focalLength: 100,     // FOV depth
            baseSize: 50,         // Initial size of squares
            trail: 0.02,          // Motion blur trail (dynamic)
            rayCount: 30,         // Max rays
            activeRays: 5         // Dynamic ray count
        };

        this.squares = [];
        this.rays = [];
        this.sunRays = [];
        this.globalAngle = 0;

        this.initializeObjects();
    }

    initializeObjects() {
        this.squares = [];
        for (let i = 0; i < this.config.squareCount; i++) {
            this.squares.push(new Square(i * (2000 / this.config.squareCount), this.config));
        }

        this.rays = [];
        for (let i = 0; i < this.config.rayCount; i++) {
            this.rays.push(new Ray(this.width, this.height, this.config));
        }

        // Rise/Sun Rays
        this.sunRays = [];
        const sunRayCount = this.config.activeRays;
        for (let i = 0; i < sunRayCount; i++) {
            this.sunRays.push({
                angle: (Math.PI * 2 * i) / sunRayCount,
                length: Math.random(),
                speed: Math.random() * 0.02 + 0.01
            });
        }
    }

    setSpeed(factor) {
        const normalized = Math.min(Math.max(factor / 100, 0), 1.5);
        this.normalizedSpeed = normalized;

        const minSpeed = 1;
        const maxSpeed = 40;
        this.config.speed = minSpeed + (normalized * (maxSpeed - minSpeed));
        this.config.rotationSpeed = 0.001 + (normalized * 0.002);
        this.config.trail = Math.max(0.2, 0.3 - (normalized * 0.2));
        this.config.activeRays = Math.floor(20 + (normalized * 250));
    }

    start() {
        if (this.isRunning) return;
        this.isRunning = true;
        this.vignetteColor = getComputedStyle(document.documentElement).getPropertyValue('--bg-mask-80').trim() || 'rgba(0, 0, 0, 0.8)';
        this.canvas.width = this.width;
        this.canvas.height = this.height;
        this.animate();
    }

    stop() {
        this.isRunning = false;
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }
        // Fade out manually if needed, or just stop
    }

    animate() {
        if (!this.isRunning) return;

        // -- Background & Trail --
        // Dynamic background color: Dark Orange -> White/Bright
        // We use fillRect with low opacity to create the "trail" effect.

        // Base orange/yellow glow growing with speed
        // Normalized speed 0 -> 1.5. 
        // We want a subtle glow that builds up.

        // Red component grows to create orange/red tint
        let trailR = Math.min(this.normalizedSpeed * 80, 30);
        // Green component grows slower to keep it Orange/Gold (Less green = more orange)
        let trailG = Math.min(this.normalizedSpeed * 20, 10);
        let trailB = 0;

        // At high speed, tint the background slightly light to simulate "whiteout"
        if (this.normalizedSpeed > 0.8) {
            const whiteout = (this.normalizedSpeed - 0.8) * 50;
            trailR += whiteout;
            trailG += whiteout;
            trailB += whiteout;
        }

        this.ctx.fillStyle = `rgba(${trailR}, ${trailG}, ${trailB}, ${this.config.trail})`;
        this.ctx.fillRect(0, 0, this.width, this.height);

        this.ctx.globalCompositeOperation = 'lighter';

        this.globalAngle += this.config.rotationSpeed;

        // -- 1. Sun Rays (Radial Lines from center) --
        this.ctx.save();
        this.ctx.translate(this.cx, this.cy);
        this.ctx.rotate(this.globalAngle * 0.5); // Sun rays spin slower

        const sunIntensity = Math.min(1, this.normalizedSpeed * 1.2);
        if (sunIntensity > 0.1) {
            this.sunRays.forEach((ray, i) => {
                const len = 100 + (ray.length * 200 * this.normalizedSpeed);
                this.ctx.beginPath();
                this.ctx.moveTo(0, 0);
                this.ctx.lineTo(Math.cos(ray.angle) * len, Math.sin(ray.angle) * len);
                // More Orange Sun Rays (Less Green)
                this.ctx.strokeStyle = `rgba(255, 140, 0, ${sunIntensity * 0.4})`;
                this.ctx.lineWidth = 2 + (this.normalizedSpeed * 4);
                this.ctx.stroke();

                // Rotate individual rays slightly
                ray.angle += ray.speed * this.normalizedSpeed;
            });
        }
        this.ctx.restore();


        // -- 2. Speed Lines (Rays) --
        // Only draw up to 'activeRays' count
        for (let i = 0; i < Math.min(this.rays.length, this.config.activeRays); i++) {
            this.rays[i].update();
            this.rays[i].draw(this.ctx, this.cx, this.cy, this.globalAngle);
        }

        // -- 3. Tunnel Squares (Portals) --
        this.squares.sort((a, b) => b.z - a.z);
        this.squares.forEach(sq => {
            sq.update();
            sq.draw(this.ctx, this.cx, this.cy, this.globalAngle, this.normalizedSpeed);
        });

        // -- 4. Center Burst (Light at end of tunnel) --
        // Size and Color logic
        // Low speed: Orange center. High speed: White blinding center.
        const burstRadius = 50 + (this.normalizedSpeed * 100);
        const gradient = this.ctx.createRadialGradient(this.cx, this.cy, 0, this.cx, this.cy, burstRadius);

        const r = 255;
        const g = Math.min(255, 100 + (this.normalizedSpeed * 100)); // Start more orange (less green)
        const b = Math.min(255, 0 + (this.normalizedSpeed * 200));

        gradient.addColorStop(0, `rgba(${r}, ${g}, ${b}, 1)`);
        gradient.addColorStop(0.4, `rgba(255, 80, 0, ${0.5 + (this.normalizedSpeed * 0.5)})`); // Deep Orange
        gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

        this.ctx.fillStyle = gradient;
        this.ctx.fillRect(0, 0, this.width, this.height);

        // 6. Outer Black Glow (Vignette)
        const vignette = this.ctx.createRadialGradient(this.cx, this.cy, this.width * 0.35, this.cx, this.cy, this.width * 0.5);
        vignette.addColorStop(0, 'rgba(0, 0, 0, 0)');
        vignette.addColorStop(1, this.vignetteColor); // Strong black fade at edge

        this.ctx.fillStyle = vignette;
        this.ctx.fillRect(0, 0, this.width, this.height);

        // Reset blend mode
        this.ctx.globalCompositeOperation = 'source-over';
        this.animationId = requestAnimationFrame(() => this.animate());
    }
}

class Square {
    constructor(z, config) {
        this.z = z;
        this.config = config;
    }

    update() {
        this.z -= this.config.speed;
        if (this.z < 1) {
            this.z = 2000;
        }
    }

    draw(ctx, cx, cy, globalRotation, normalizedSpeed) {
        const scale = this.config.focalLength / (this.config.focalLength + this.z);
        if (this.z <= 0 || scale <= 0) return;

        const size = this.config.baseSize * 10;
        const projectedSize = size * scale;

        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(globalRotation + (this.z * 0.001) - (Math.PI / 30));

        // -- Golden Portal Visuals --
        // Hue: 30 (Orange) -> 45 (Gold/Yellow)
        const hue = 30 + (normalizedSpeed * 15);

        // Lightness: dim far away, bright close
        const lightness = 40 + (scale * 40) + (normalizedSpeed * 20);
        const alpha = Math.min(1, scale * (1.5 + normalizedSpeed));

        // Line Width increases with speed and proximity
        ctx.lineWidth = (5 * scale) + (normalizedSpeed * 10 * scale);

        // Golden color
        ctx.strokeStyle = `hsla(${hue}, 100%, ${lightness}%, ${alpha})`;

        // Glow
        ctx.shadowBlur = (20 * scale) + (normalizedSpeed * 40 * scale);
        ctx.shadowColor = `hsla(${hue}, 100%, 50%, 1)`;

        // Optional: Fill the portal slightly?
        ctx.fillStyle = `hsla(${hue}, 100%, 50%, ${0.05 * scale})`;
        ctx.fillRect(-projectedSize / 2, -projectedSize / 2, projectedSize, projectedSize);

        ctx.strokeRect(
            -projectedSize / 2,
            -projectedSize / 2,
            projectedSize,
            projectedSize
        );

        ctx.restore();
    }
}

class Ray {
    constructor(width, height, config) {
        this.width = width;
        this.height = height;
        this.config = config;
        this.reset();
    }

    reset() {
        this.x = (Math.random() - 0.5) * this.width;
        this.y = (Math.random() - 0.5) * this.height;
        this.z = Math.random() * 2000;
        // Rays moves faster than walls to look like particles passing by
        this.speed = this.config.speed * (1.5 + Math.random());
    }

    update() {
        this.z -= this.speed; // Uses its own speed which is factor of base speed
        // If speed changed drastically, we might want to update this.speed, but 
        // resetting on wrap is enough for chaos.
        if (this.z < 1) this.reset();
    }

    draw(ctx, cx, cy, globalRotation) {
        const scale = this.config.focalLength / (this.config.focalLength + this.z);
        const x2d = this.x * scale;
        const y2d = this.y * scale;

        // Long streaks
        const streakFactor = 2 + (this.config.speed * 0.1);
        const oldScale = this.config.focalLength / (this.config.focalLength + (this.z + streakFactor * 10));
        const oldX2d = this.x * oldScale;
        const oldY2d = this.y * oldScale;

        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(globalRotation);

        ctx.strokeStyle = `rgba(255, 255, 255, ${scale * 0.8})`;
        ctx.lineWidth = 2 * scale;
        ctx.beginPath();
        ctx.moveTo(oldX2d, oldY2d);
        ctx.lineTo(x2d, y2d);
        ctx.stroke();
        ctx.restore();
    }
}


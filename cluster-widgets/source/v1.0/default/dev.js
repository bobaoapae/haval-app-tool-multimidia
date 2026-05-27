const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const distDir = path.join(__dirname, 'dist');
if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
}

try {
    fs.copyFileSync(
        path.join(__dirname, 'theme.xml'),
        path.join(distDir, 'theme.xml')
    );
    console.log('✅ theme.xml copied to dist/theme.xml successfully for local development!');
} catch (e) {
    console.error('❌ Failed to copy theme.xml to dist/', e.message);
}

// Spawn parcel index.html
const parcel = spawn('npx', ['parcel', 'index.html'], { stdio: 'inherit', shell: true });

parcel.on('error', (err) => {
    console.error('❌ Failed to start Parcel:', err.message);
});

parcel.on('close', (code) => {
    process.exit(code || 0);
});

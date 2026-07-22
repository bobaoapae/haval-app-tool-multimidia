const http = require('http');
const fs = require('fs');
const path = require('path');

const apkPath = process.argv[2] || path.join(__dirname, '..', '..', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const port = process.argv[3] ? parseInt(process.argv[3], 10) : 8765;

const stat = fs.statSync(apkPath);
console.log(`Serving ${apkPath} (${stat.size} bytes) on port ${port}`);

const server = http.createServer((req, res) => {
    console.log(`Request: ${req.method} ${req.url}`);
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Length', stat.size);
    fs.createReadStream(apkPath).pipe(res);
});

server.listen(port, '0.0.0.0', () => {
    console.log(`Ready at http://0.0.0.0:${port}/app-debug.apk`);
});

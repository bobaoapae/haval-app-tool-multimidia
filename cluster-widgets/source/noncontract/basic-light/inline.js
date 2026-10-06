var fs = require('fs');
var path = require('path');

// Função para processar um HTML e inlinear CSS/JS
function processHtml(htmlPath, outputPath) {
  console.log(`🔄 Processando: ${htmlPath}`);
  
  if (!fs.existsSync(htmlPath)) {
    console.log(`❌ Arquivo não encontrado: ${htmlPath}`);
    return;
  }

  var htmlContent = fs.readFileSync(htmlPath, 'utf8');

  // Inline CSS
  var cssRegex = /<link[^>]*href=([^>\s]+\.css)[^>]*>/g;
  var cssMatch;
  while ((cssMatch = cssRegex.exec(htmlContent)) !== null) {
    var cssPath = cssMatch[1].replace(/['"]/g, '');
    
    // Converte caminho relativo para absoluto
    var fullCssPath;
    if (cssPath.startsWith('/src/')) {
      fullCssPath = path.join(__dirname, cssPath.substring(1));
    } else {
      fullCssPath = path.join(__dirname, 'dist', cssPath);
    }
    
    if (fs.existsSync(fullCssPath)) {
      var cssContent = fs.readFileSync(fullCssPath, 'utf8');
      htmlContent = htmlContent.split(cssMatch[0]).join('<style>' + cssContent + '</style>');
      console.log('✅ CSS inlined:', cssPath);
    }
  }

  // Inline JavaScript
  var jsRegex = /<script([^>]*)src=["']?([^"'\s>]+\.js)["']?([^>]*)><\/script>/g;
  var jsMatch;
  while ((jsMatch = jsRegex.exec(htmlContent)) !== null) {
    var beforeSrc = jsMatch[1];
    var jsPath = jsMatch[2];
    var afterSrc = jsMatch[3];
    
    var fullJsPath = path.join(__dirname, 'dist', jsPath);
    
      if (fs.existsSync(fullJsPath)) {
        var jsContent = fs.readFileSync(fullJsPath, 'utf8');
        
        // Remove sourcemap comments to avoid browser trying to load missing files
        jsContent = jsContent.replace(/\/\/# sourceMappingURL=.*/g, '');
        
        // Reconstitute attributes, looking for type="module"
        var attributes = (beforeSrc + ' ' + afterSrc).trim();
        var isModule = attributes.includes('type="module"') || attributes.includes('type=module');
        
        var scriptTag = isModule ? '<script type="module">' : '<script>';
        var replacement = scriptTag + jsContent + '</script>';
        
        // Use split/join to avoid $ special characters in jsContent when using .replace()
        htmlContent = htmlContent.split(jsMatch[0]).join(replacement);
        
        if (fs.existsSync(fullJsPath)) {
          fs.unlinkSync(fullJsPath);
        }
        console.log('✅ JS inlined:', jsPath + (isModule ? ' (as module)' : ''));
      }
  }

  // Salva o HTML processado
  fs.writeFileSync(outputPath, htmlContent, 'utf8');
  console.log(`✅ HTML gerado: ${outputPath}`);
}

// Função para inlinear assets dinâmicos (CSS referenciados no JS)
function inlineDynamicAssets(htmlPath) {
  console.log(`🔍 Buscando assets dinâmicos em: ${htmlPath}`);
  var htmlContent = fs.readFileSync(htmlPath, 'utf8');
  var distDir = path.join(__dirname, 'dist');
  var files = fs.readdirSync(distDir);
  
  var changed = false;
  files.forEach(file => {
    if (file.endsWith('.css') && !file.includes('.map')) {
      // Se o nome do arquivo aparece no HTML (provavelmente dentro do JS inlined)
      if (htmlContent.includes(file)) {
        var filePath = path.join(distDir, file);
        var content = fs.readFileSync(filePath, 'utf8');
        var base64 = Buffer.from(content).toString('base64');
        var dataUri = `data:text/css;base64,${base64}`;
        
        // Pattern 1: module.bundle.resolve("filename") [optionally with + "?" + Date.now()]
        // We match up to the end of the expression (comma, semicolon, closing paren)
        const escapedFile = file.replace(/\./g, '\\.');
        const resolveRegex = new RegExp(`module\\.bundle\\.resolve\\((['"])${escapedFile}(['"])\\)([^,;\\n\\r)]*)`, 'g');
        
        if (resolveRegex.test(htmlContent)) {
          console.log(`📦 Inlining dynamic asset (wrapped-robust): ${file}`);
          // Replace the entire resolution call (including any appended query params) with just the Data URI
          htmlContent = htmlContent.replace(resolveRegex, `"${dataUri}"`);
          changed = true;
        }
        
        // Pattern 2: importmap leading slash
        const importMapRegex = new RegExp(`(['"]):\\s*(['"])/${escapedFile}(['"])`, 'g');
        if (importMapRegex.test(htmlContent)) {
          console.log(`📦 Inlining dynamic asset (regex-importmap): ${file}`);
          htmlContent = htmlContent.replace(importMapRegex, `$1:$2${dataUri}$3`);
          changed = true;
        }

        // Pattern 3: Fallback simple quoted string
        const plainRegex = new RegExp(`(['"])([\\./]*)${escapedFile}(['"])`, 'g');
        if (plainRegex.test(htmlContent)) {
          console.log(`📦 Inlining dynamic asset (regex-plain): ${file}`);
          htmlContent = htmlContent.replace(plainRegex, `$1${dataUri}$3`);
          changed = true;
        }
        
        // Remove o arquivo CSS original pois agora está inlined
        fs.unlinkSync(filePath);
      }
    }
  });
  
  if (changed) {
    fs.writeFileSync(htmlPath, htmlContent, 'utf8');
    console.log(`✅ Assets dinâmicos inlined em: ${htmlPath}`);
  }
}

// Process index.html to app.html
console.log('🚀 Iniciando build unificado...');

var indexHtmlPath = path.join(__dirname, 'dist', 'index.html');
var appOutputPath = path.join(__dirname, 'dist', 'app_light.html');
processHtml(indexHtmlPath, appOutputPath);

// Inline dynamic assets (css referenced in JS)
inlineDynamicAssets(appOutputPath);

// Copy to Android resources
var androidRawPath = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'raw', 'app_light.html');
try {
  fs.copyFileSync(appOutputPath, androidRawPath);
  console.log(`✅ Copiado para Android: ${androidRawPath}`);
} catch (err) {
  console.error(`❌ Erro ao copiar para Android: ${err.message}`);
}

// Remove pasta assets vazia
var assetsDir = path.join(__dirname, 'dist', 'assets');
if (fs.existsSync(assetsDir)) {
  var files = fs.readdirSync(assetsDir);
  if (files.length === 0) {
    fs.rmdirSync(assetsDir);
    console.log('✅ Pasta assets removida');
  }
}

// Remove arquivos CSS originais
var cssFiles = ['night.style.css', 'light.style.css', 'style.css'];
cssFiles.forEach(function(cssFile) {
  var cssPath = path.join(__dirname, 'dist', cssFile);
  if (fs.existsSync(cssPath)) {
    fs.unlinkSync(cssPath);
    console.log(`✅ CSS removido: ${cssFile}`);
  }
});

console.log('🎉 Build completo! Arquivo gerado:');
console.log('  📄 app_light.html (unificado)');
